package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.WidgetPrefs
import com.example.darktodo.data.WidgetSettings

/** Renders the home-screen widget with platform RemoteViews and XML TextViews. */
object TodoWidget {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodoWidgetReceiver::class.java))
        update(context, manager, ids)
    }

    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val tasks = TaskStore.tasks(context)
        val settings = WidgetSettings.load(context)
        ids.forEach { id -> manager.updateAppWidget(id, render(context, tasks, settings)) }
    }

    private fun render(context: Context, tasks: List<Task>, settings: WidgetPrefs): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.todo_widget)
        val titleColor = parseColor(settings.taskTitle)
        val incompleteColor = parseColor(settings.incomplete)
        val completeColor = parseColor(settings.complete)
        val cardColor = parseColor(settings.taskCard)
        val fadedAlpha = (255 * (100 - settings.fadePercent) / 100).coerceIn(0, 255)

        views.setInt(R.id.widget_root_background, "setColorFilter", parseColor(settings.widgetBackground))
        views.setInt(R.id.widget_count_panel_background, "setColorFilter", parseColor(settings.countPanel))
        views.setInt(R.id.widget_task_card_background, "setColorFilter", cardColor)
        setStyledText(views, R.id.widget_count, tasks.count { !it.completed }.toString(), settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_count_label, "Tasks", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_title, "TODO", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_clear, "\u00D7", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_add, "+ Add task", settings.fontWeight, settings.fontItalic)
        setStyledText(views, R.id.widget_empty, "No tasks yet", settings.fontWeight, settings.fontItalic)
        views.setInt(R.id.widget_count, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_count_label, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_title, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_clear, "setTextColor", incompleteColor)
        views.setInt(R.id.widget_add, "setTextColor", incompleteColor)

        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        views.setOnClickPendingIntent(R.id.widget_add, openApp)

        val clearIntent = Intent(context, TodoWidgetReceiver::class.java).setAction(ACTION_CLEAR_COMPLETED)
        val clearPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_clear, clearPendingIntent)

        views.removeAllViews(R.id.widget_task_rows)
        val visibleTasks = tasks.take(5)
        views.setViewVisibility(R.id.widget_empty, if (visibleTasks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
        views.setInt(R.id.widget_empty, "setTextColor", titleColor)
        visibleTasks.forEach { task ->
            val row = RemoteViews(context.packageName, R.layout.widget_task_row)
            val opacity = if (task.completed) fadedAlpha else 255
            row.setTextViewText(R.id.widget_task_check, if (task.completed) "\u25cf" else "\u25cb")
            row.setInt(R.id.widget_task_check, "setTextColor", withAlpha(if (task.completed) completeColor else incompleteColor, opacity))
            setStyledText(row, R.id.widget_task_title, task.title, settings.fontWeight, settings.fontItalic)
            row.setTextViewTextSize(R.id.widget_task_title, TypedValue.COMPLEX_UNIT_SP, settings.taskTitleSizeSp * (13f / 16f))
            row.setInt(R.id.widget_task_title, "setTextColor", withAlpha(titleColor, opacity))
            row.setInt(R.id.widget_task_row, "setBackgroundColor", withAlpha(cardColor, opacity))

            val toggleIntent = Intent(context, TodoWidgetReceiver::class.java)
                .setAction(ACTION_TOGGLE_TASK)
                .putExtra(EXTRA_TASK_ID, task.id)
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.hashCode(),
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            row.setOnClickPendingIntent(R.id.widget_task_check, togglePendingIntent)
            row.setOnClickPendingIntent(R.id.widget_task_title, openApp)
            views.addView(R.id.widget_task_rows, row)
        }
        return views
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
    const val ACTION_CLEAR_COMPLETED = "com.example.darktodo.widget.CLEAR_COMPLETED"
    const val EXTRA_TASK_ID = "task_id"
}

class TodoWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        TodoWidget.update(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodoWidget.ACTION_TOGGLE_TASK -> intent.getStringExtra(TodoWidget.EXTRA_TASK_ID)?.let {
                TaskStore.toggle(context, it)
                TodoWidget.updateAll(context)
            }
            TodoWidget.ACTION_CLEAR_COMPLETED -> {
                TaskStore.clearCompleted(context)
                TodoWidget.updateAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }
}
