package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.TaskTimeFormatter
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetSettings

/** Renders the home-screen widget with platform RemoteViews and XML TextViews. */
object TodoWidget {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetReceiver::class.java))
        WidgetRefreshScheduler.reconcile(context)
        update(context, manager, ids)
    }

    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val tasks = TaskStore.tasks(context)
        val settings = WidgetSettings.load(context)
        ids.forEach { id ->
            val views = render(context, tasks.count { !it.completed }, settings)
            views.setRemoteAdapter(R.id.widget_task_rows, Intent(context, TodoWidgetService::class.java))
            views.setEmptyView(R.id.widget_task_rows, R.id.widget_empty)
            val clickTemplate = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, TodoWidgetReceiver::class.java).setAction(ACTION_COLLECTION_CLICK),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            )
            views.setPendingIntentTemplate(R.id.widget_task_rows, clickTemplate)
            manager.updateAppWidget(id, views)
        }
        if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_rows)
    }

    private fun render(context: Context, incompleteCount: Int, settings: WidgetPrefs): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.todo_widget)
        val titleColor = parseColor(settings.taskTitle)
        val incompleteColor = parseColor(settings.incomplete)
        val completeColor = parseColor(settings.complete)
        val cardColor = parseColor(settings.taskCard)
        views.setInt(R.id.widget_root_background, "setColorFilter", parseColor(settings.widgetBackground))
        views.setInt(R.id.widget_count_panel_background, "setColorFilter", parseColor(settings.countPanel))
        views.setInt(R.id.widget_task_card_background, "setColorFilter", cardColor)
        setStyledText(views, R.id.widget_count, incompleteCount.toString(), settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_count_label, "Tasks", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_refresh, "\u21BB", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_add, "+ Add task", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_empty, "No tasks yet", settings.fontWeight, settings.fontItalic)
        views.setInt(R.id.widget_count, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_count_label, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_refresh, "setTextColor", incompleteColor)
        views.setContentDescription(R.id.widget_refresh, "Refresh task countdowns")
        views.setInt(R.id.widget_add, "setTextColor", incompleteColor)

        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add, openApp)

        val refreshIntent = Intent(context, TodoWidgetReceiver::class.java).setAction(ACTION_REFRESH_WIDGET)
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

        views.setInt(R.id.widget_empty, "setTextColor", titleColor)
        return views
    }

    internal fun renderTaskRow(context: Context, task: Task): RemoteViews {
        val settings = WidgetSettings.load(context)
        val row = RemoteViews(context.packageName, R.layout.widget_task_row)
        val titleColor = parseColor(settings.taskTitle)
        val incompleteColor = parseColor(settings.incomplete)
        val completeColor = parseColor(settings.complete)
        val cardColor = parseColor(settings.taskCard)
        val opacity = if (task.completed) (255 * (100 - settings.fadePercent) / 100).coerceIn(0, 255) else 255
        row.setTextViewText(R.id.widget_task_check, if (task.completed) "\u25cf" else "\u25cb")
        row.setInt(R.id.widget_task_check, "setTextColor", withAlpha(if (task.completed) completeColor else incompleteColor, opacity))
        val label = if (!task.completed && task.reminderType != "none") "${task.title} | ${TaskTimeFormatter.label(context, task)}" else task.title
        setStyledText(row, R.id.widget_task_title, label, settings.fontWeight, settings.fontItalic)
        row.setTextViewTextSize(R.id.widget_task_title, TypedValue.COMPLEX_UNIT_SP, settings.taskTitleSizeSp * (13f / 16f))
        row.setInt(R.id.widget_task_title, "setTextColor", withAlpha(titleColor, opacity))
        row.setInt(R.id.widget_task_row, "setBackgroundColor", withAlpha(cardColor, opacity))
        val toggleIntent = Intent()
            .putExtra(EXTRA_COLLECTION_ACTION, ACTION_TOGGLE_TASK)
            .putExtra(EXTRA_TASK_ID, task.id)
        row.setOnClickFillInIntent(R.id.widget_task_row, toggleIntent)
        row.setOnClickFillInIntent(R.id.widget_task_check, toggleIntent)
        row.setOnClickFillInIntent(R.id.widget_task_title, toggleIntent)
        return row
    }

    private fun setStyledText(views: RemoteViews, viewId: Int, text: String, weight: Int, italic: Boolean) {
        // StyleSpan's italic bit is resolved against the XML Nunito family, which maps
        // normal and genuine italic font resources. The variable weight axis is applied
        // separately so style and weight do not interfere with one another.
        val styled = if (italic && text.isNotEmpty()) SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else text
        views.setTextViewText(viewId, styled)
        views.setString(viewId, "setFontVariationSettings", "'wght' $weight")
    }

    private fun parseColor(value: String): Int = try {
        Color.parseColor(if (value.startsWith("#")) value else "#$value")
    } catch (_: IllegalArgumentException) {
        Color.WHITE
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(Color.alpha(color) * alpha / 255, Color.red(color), Color.green(color), Color.blue(color))

    const val ACTION_TOGGLE_TASK = "com.example.darktodo.widget.TOGGLE_TASK"
    const val ACTION_REFRESH_WIDGET = "com.example.darktodo.widget.REFRESH_WIDGET"
    const val ACTION_COLLECTION_CLICK = "com.example.darktodo.widget.COLLECTION_CLICK"
    const val EXTRA_COLLECTION_ACTION = "collection_action"
    const val EXTRA_TASK_ID = "task_id"
}

class TodoWidgetReceiver : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        com.example.darktodo.reminders.ReminderScheduler.restore(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        com.example.darktodo.reminders.ReminderScheduler.restore(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        TodoWidget.update(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodoWidget.ACTION_COLLECTION_CLICK -> when (intent.getStringExtra(TodoWidget.EXTRA_COLLECTION_ACTION)) {
                TodoWidget.ACTION_TOGGLE_TASK -> intent.getStringExtra(TodoWidget.EXTRA_TASK_ID)?.let { TaskStore.toggle(context, it) }
            }
            TodoWidget.ACTION_TOGGLE_TASK -> intent.getStringExtra(TodoWidget.EXTRA_TASK_ID)?.let {
                TaskStore.toggle(context, it)
                TodoWidget.updateAll(context)
            }
            TodoWidget.ACTION_REFRESH_WIDGET -> {
                TodoWidget.updateAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }
}
