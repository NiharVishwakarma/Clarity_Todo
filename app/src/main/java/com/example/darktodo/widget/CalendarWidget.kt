package com.example.darktodo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.CalendarDates
import com.example.darktodo.data.CalendarTaskStatus
import com.example.darktodo.data.CalendarWidgetPrefs
import com.example.darktodo.data.CalendarWidgetSettings
import com.example.darktodo.data.TaskStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object CalendarWidget {
    private const val STATE_FILE = "calendar_widget_state"
    private const val KEY_SELECTED = "selected_"
    private const val KEY_MONTH = "month_"
    private const val KEY_FOLLOW_TODAY = "follow_today_"
    const val ACTION_MONTH = "com.example.darktodo.calendar.MONTH"
    const val ACTION_DATE = "com.example.darktodo.calendar.DATE"
    const val EXTRA_WIDGET = "calendar_widget_id"
    const val EXTRA_MONTH_DELTA = "calendar_month_delta"
    const val EXTRA_DATE = "calendar_date"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetReceiver::class.java))
        if (ids.isEmpty()) { cancelMidnight(context); return }
        updateTodaySelection(context, ids)
        ids.forEach { id -> manager.updateAppWidget(id, render(context, id)) }
        scheduleMidnight(context)
    }

    private fun render(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.calendar_widget)
        val prefs = CalendarWidgetSettings.load(context)
        val locale = Locale.getDefault()
        val currentDate = LocalDate.now(ZoneId.systemDefault())
        val state = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        val monthValue = state.getString(KEY_MONTH + widgetId, "")
        val month = runCatching { YearMonth.parse(monthValue) }.getOrDefault(YearMonth.from(currentDate))
        val selected = runCatching { LocalDate.parse(state.getString(KEY_SELECTED + widgetId, null)) }.getOrNull()
            ?: currentDate.also { state.edit().putString(KEY_SELECTED + widgetId, it.toString()).apply() }
        views.setImageViewResource(R.id.calendar_background, radiusDrawable(prefs.cornerRadius))
        views.setInt(R.id.calendar_background, "setColorFilter", color(prefs.background))
        views.setTextViewText(R.id.calendar_heading, month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)))
        views.setInt(R.id.calendar_heading, "setTextAppearance", WidgetFontStyles.resolve(prefs.fontFamily, prefs.fontItalic))
        views.setTextViewTextSize(R.id.calendar_heading, 2, prefs.headingSize.toFloat())
        views.setInt(R.id.calendar_heading, "setTextColor", color(prefs.heading))
        views.setInt(R.id.calendar_previous, "setTextColor", color(prefs.heading))
        views.setInt(R.id.calendar_next, "setTextColor", color(prefs.heading))
        views.setContentDescription(R.id.calendar_previous, "Previous month, ${month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))}")
        views.setContentDescription(R.id.calendar_next, "Next month, ${month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))}")
        val prev = navIntent(context, widgetId, -1)
        val next = navIntent(context, widgetId, 1)
        views.setOnClickPendingIntent(R.id.calendar_previous, prev)
        views.setOnClickPendingIntent(R.id.calendar_next, next)

        views.removeAllViews(R.id.calendar_weekdays)
        CalendarDates.weekdays(locale).forEach { day ->
            val weekday = RemoteViews(context.packageName, R.layout.calendar_widget_weekday).apply {
                setTextViewText(android.R.id.text1, day.getDisplayName(TextStyle.SHORT, locale).take(2))
                setInt(android.R.id.text1, "setTextAppearance", WidgetFontStyles.resolve(prefs.fontFamily, prefs.fontItalic))
                setTextViewTextSize(android.R.id.text1, 2, prefs.weekdaySize.toFloat())
                setInt(android.R.id.text1, "setTextColor", color(prefs.weekday))
            }
            views.addView(R.id.calendar_weekdays, weekday)
        }

        val tasks = TaskStore.tasks(context)
        val cells = CalendarDates.monthCells(month, locale)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val widgetHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250))
        val maxRowSpacing = ((widgetHeight - 68) / (cells.size / 7) - 26).coerceAtLeast(0)
        val effectiveRowSpacing = prefs.rowSpacing.coerceAtMost(maxRowSpacing)
        views.removeAllViews(R.id.calendar_grid)
        cells.chunked(7).forEach { week ->
            val row = RemoteViews(context.packageName, R.layout.calendar_widget_week_row)
            val spacingPx = (effectiveRowSpacing * context.resources.displayMetrics.density).toInt()
            row.setViewPadding(R.id.calendar_week_row, 0, 0, 0, spacingPx)
            week.forEach { date ->
                val cell = RemoteViews(context.packageName, R.layout.calendar_widget_day)
                if (date == null) {
                    cell.setViewVisibility(R.id.calendar_day_cell, View.INVISIBLE)
                } else {
                    val isSelected = date == selected
                    val isToday = date == currentDate
                    cell.setTextViewText(R.id.calendar_day_number, date.dayOfMonth.toString())
                    cell.setInt(R.id.calendar_day_number, "setTextAppearance", WidgetFontStyles.resolve(prefs.fontFamily, prefs.fontItalic))
                    cell.setTextViewTextSize(R.id.calendar_day_number, 2, prefs.dateSize.toFloat())
                    cell.setInt(R.id.calendar_day_number, "setTextColor", color(if (isSelected) prefs.selectedText else prefs.date))
                    cell.setViewVisibility(R.id.calendar_selected_circle, if (isSelected) View.VISIBLE else View.GONE)
                    if (isSelected) cell.setInt(R.id.calendar_selected_circle, "setColorFilter", color(prefs.selectedBackground))
                    if (isToday && !isSelected) cell.setInt(R.id.calendar_day_number, "setTextColor", color(prefs.selectedBackground))
                    cell.setContentDescription(R.id.calendar_day_cell, date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)) + if (isToday) ", today" else "")
                    when (CalendarDates.status(tasks, date)) {
                        CalendarTaskStatus.NONE -> cell.setViewVisibility(R.id.calendar_status_dot_border, View.GONE)
                        CalendarTaskStatus.COMPLETE -> { cell.setViewVisibility(R.id.calendar_status_dot_border, if (prefs.showDots) View.VISIBLE else View.GONE); cell.setInt(R.id.calendar_status_dot, "setColorFilter", color(prefs.completeDot)) }
                        CalendarTaskStatus.MIXED -> { cell.setViewVisibility(R.id.calendar_status_dot_border, if (prefs.showDots) View.VISIBLE else View.GONE); cell.setInt(R.id.calendar_status_dot, "setColorFilter", color(prefs.mixedDot)) }
                        CalendarTaskStatus.OPEN -> { cell.setViewVisibility(R.id.calendar_status_dot_border, if (prefs.showDots) View.VISIBLE else View.GONE); cell.setInt(R.id.calendar_status_dot, "setColorFilter", color(prefs.openDot)) }
                    }
                    cell.setOnClickPendingIntent(R.id.calendar_day_cell, dateIntent(context, widgetId, date))
                }
                row.addView(R.id.calendar_week_row, cell)
            }
            views.addView(R.id.calendar_grid, row)
        }
        return views
    }

    fun receive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val prefs = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_MONTH -> {
                val old = runCatching { YearMonth.parse(prefs.getString(KEY_MONTH + id, null)) }.getOrDefault(YearMonth.from(LocalDate.now()))
                prefs.edit().putString(KEY_MONTH + id, old.plusMonths(intent.getIntExtra(EXTRA_MONTH_DELTA, 0).toLong()).toString()).apply()
                updateAll(context)
            }
            ACTION_DATE -> {
                val date = intent.getStringExtra(EXTRA_DATE) ?: return
                val selectedDate = LocalDate.parse(date)
                prefs.edit().putString(KEY_SELECTED + id, date).putString(KEY_MONTH + id, YearMonth.from(selectedDate).toString())
                    .putBoolean(KEY_FOLLOW_TODAY + id, selectedDate == LocalDate.now(ZoneId.systemDefault())).apply()
                updateAll(context)
                context.startActivity(Intent(context, MainActivity::class.java).putExtra("task_date", date).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
    }

    fun selectDate(context: Context, widgetId: Int, value: String) {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return
        context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED + widgetId, date.toString()).putString(KEY_MONTH + widgetId, YearMonth.from(date).toString())
            .putBoolean(KEY_FOLLOW_TODAY + widgetId, date == LocalDate.now(ZoneId.systemDefault())).apply()
        updateAll(context)
    }

    fun clear(context: Context, id: Int) {
        context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE).edit().remove(KEY_MONTH + id).remove(KEY_SELECTED + id).remove(KEY_FOLLOW_TODAY + id).apply()
        updateAll(context)
    }

    private fun updateTodaySelection(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
        val today = LocalDate.now(ZoneId.systemDefault())
        ids.forEach { id ->
            val selected = runCatching { LocalDate.parse(prefs.getString(KEY_SELECTED + id, null)) }.getOrNull()
            if (prefs.getBoolean(KEY_FOLLOW_TODAY + id, true) && selected != today) {
                val edit = prefs.edit().putString(KEY_SELECTED + id, today.toString())
                val month = runCatching { YearMonth.parse(prefs.getString(KEY_MONTH + id, null)) }.getOrNull()
                if (month == null || month == YearMonth.from(today.minusDays(1))) edit.putString(KEY_MONTH + id, YearMonth.from(today).toString())
                edit.apply()
            }
        }
    }

    private fun navIntent(context: Context, id: Int, delta: Int): PendingIntent {
        val intent = Intent(context, CalendarWidgetReceiver::class.java).setAction(ACTION_MONTH).setData(android.net.Uri.parse("clarity://calendar/$id/month/$delta"))
            .putExtra(EXTRA_WIDGET, id).putExtra(EXTRA_MONTH_DELTA, delta)
        return PendingIntent.getBroadcast(context, id * 10 + delta + 50, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dateIntent(context: Context, id: Int, date: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(ACTION_DATE).setData(android.net.Uri.parse("clarity://calendar/$id/date/$date"))
            .putExtra(EXTRA_WIDGET, id).putExtra("task_date", date.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, ("$id:$date").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleMidnight(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, CalendarMidnightReceiver::class.java).setAction("com.example.darktodo.calendar.MIDNIGHT")
        val pi = PendingIntent.getBroadcast(context, 57302, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        if (Build.VERSION.SDK_INT >= 31 && runCatching { alarm.canScheduleExactAlarms() }.getOrDefault(false)) {
            runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }.onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
        } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    private fun cancelMidnight(context: Context) {
        val pi = PendingIntent.getBroadcast(context, 57302, Intent(context, CalendarMidnightReceiver::class.java).setAction("com.example.darktodo.calendar.MIDNIGHT"), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi); pi.cancel()
    }

    private fun radiusDrawable(radius: Int): Int = when { radius < 12 -> R.drawable.calendar_widget_rounded_8; radius < 19 -> R.drawable.calendar_widget_rounded_16; radius < 27 -> R.drawable.calendar_widget_rounded_22; else -> R.drawable.calendar_widget_rounded_32 }
    private fun color(value: String) = runCatching { Color.parseColor(value) }.getOrDefault(Color.WHITE)
}

class CalendarWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = CalendarWidget.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { CalendarWidget.updateAll(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { appWidgetIds.forEach { CalendarWidget.clear(context, it) } }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CalendarWidget.ACTION_DATE || intent.action == CalendarWidget.ACTION_MONTH) CalendarWidget.receive(context, intent) else super.onReceive(context, intent)
    }
}

class CalendarMidnightReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { CalendarWidget.updateAll(context) }
}
