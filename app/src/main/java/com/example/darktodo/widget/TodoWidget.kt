package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.util.Log
import android.view.View
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
        Log.i(TAG, "updateAll: widgetIds=${ids.contentToString()}")
        WidgetRefreshScheduler.reconcile(context)
        update(context, manager, ids)
    }

    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val tasks = runCatching { TaskStore.tasks(context) }.onFailure { Log.e(TAG, "Unable to load tasks for widget update", it) }.getOrNull()
        val settings = runCatching { WidgetSettings.load(context) }.onFailure { Log.e(TAG, "Unable to load widget settings", it) }.getOrNull()
        ids.forEach { id ->
            try {
                if (tasks == null || settings == null) error("Widget state is unavailable")
                val views = render(context, tasks.count { !it.completed }, settings)
                // Include typography in the adapter identity so launcher-cached list rows
                // cannot survive a font change with their old RemoteViews layout.
                views.setRemoteAdapter(
                    R.id.widget_task_rows,
                    Intent(context, TodoWidgetService::class.java).setData(
                        android.net.Uri.parse("clarity://todo/widget/$id/${settings.fontFamily.key}/${settings.fontStyle.key}")
                    )
                )
                views.setEmptyView(R.id.widget_task_rows, R.id.widget_empty)
                val clickTemplate = PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, TodoWidgetReceiver::class.java).setAction(ACTION_COLLECTION_CLICK),
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                )
                views.setPendingIntentTemplate(R.id.widget_task_rows, clickTemplate)
                manager.updateAppWidget(id, views)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to render To-Do widget id=$id; publishing dark fallback", error)
                runCatching { manager.updateAppWidget(id, fallback(context)) }
                    .onFailure { Log.e(TAG, "Failed to publish To-Do fallback id=$id", it) }
            }
        }
        if (ids.isNotEmpty()) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_rows)
    }

    private fun fallback(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.todo_widget).apply {
        setInt(R.id.widget_root_background, "setColorFilter", Color.rgb(23, 23, 23))
        setInt(R.id.widget_count_panel_background, "setColorFilter", Color.RED)
        setViewVisibility(R.id.widget_task_rows, View.GONE)
        setViewVisibility(R.id.widget_empty, View.VISIBLE)
        setTextViewText(R.id.widget_empty, "Tasks are temporarily unavailable")
        setTextColor(R.id.widget_empty, Color.WHITE)
        setOnClickPendingIntent(R.id.widget_add_container, createTaskPendingIntent(context))
    }

    private fun createTaskPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1001,
        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CREATE_TASK)
            .setData(android.net.Uri.parse("clarity://tasks/create")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun render(context: Context, incompleteCount: Int, settings: WidgetPrefs): RemoteViews {
        val views = RemoteViews(context.packageName, WidgetFontLayouts.todo(settings.fontFamily, settings.fontStyle))
        val titleColor = parseColor(settings.taskTitle)
        val incompleteColor = parseColor(settings.incomplete)
        val completeColor = parseColor(settings.complete)
        val cardColor = parseColor(settings.taskCard)
        views.setInt(R.id.widget_root_background, "setColorFilter", parseColor(settings.widgetBackground))
        views.setInt(R.id.widget_count_panel_background, "setColorFilter", parseColor(settings.countPanel))
        views.setInt(R.id.widget_task_card_background, "setColorFilter", cardColor)
        setText(views, R.id.widget_count, incompleteCount.toString())
        setText(views, R.id.widget_count_label, "Tasks")
        setText(views, R.id.widget_refresh, "\u21BB")
        setText(views, R.id.widget_add, "+")
        setText(views, R.id.widget_empty, "No tasks yet")
        views.setInt(R.id.widget_count, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_count_label, "setTextColor", Color.WHITE)
        views.setInt(R.id.widget_refresh, "setTextColor", incompleteColor)
        views.setContentDescription(R.id.widget_refresh_container, "Refresh task countdowns")
        views.setInt(R.id.widget_add, "setTextColor", incompleteColor)

        val openApp = createTaskPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_add_container, openApp)
        views.setContentDescription(R.id.widget_add_container, "Add task")

        val refreshIntent = Intent(context, TodoWidgetReceiver::class.java).setAction(ACTION_REFRESH_WIDGET)
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_container, refreshPendingIntent)

        views.setInt(R.id.widget_empty, "setTextColor", titleColor)
        return views
    }

    internal fun renderTaskRow(context: Context, task: Task): RemoteViews {
        val settings = WidgetSettings.load(context)
        val row = RemoteViews(context.packageName, WidgetFontLayouts.taskRow(settings.fontFamily, settings.fontStyle))
        val titleColor = parseColor(settings.taskTitle)
        val incompleteColor = parseColor(settings.incomplete)
        val completeColor = parseColor(settings.complete)
        val cardColor = parseColor(settings.taskCard)
        val opacity = if (task.completed) (255 * (100 - settings.fadePercent) / 100).coerceIn(0, 255) else 255
        row.setTextViewText(R.id.widget_task_check, if (task.completed) "\u25cf" else "\u25cb")
        row.setInt(R.id.widget_task_check, "setTextColor", withAlpha(if (task.completed) completeColor else incompleteColor, opacity))
        val label = if (!task.completed && task.reminderType != "none") "${task.title} | ${TaskTimeFormatter.widgetLabel(context, task)}" else task.title
        setText(row, R.id.widget_task_title, label)
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

    private fun setText(views: RemoteViews, viewId: Int, text: String) {
        views.setTextViewText(viewId, text)
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
    private const val TAG = "TodoWidget"
}

class TodoWidgetReceiver : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        com.example.darktodo.reminders.ReminderScheduler.restore(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i("TodoWidget", "onUpdate received: widgetIds=${appWidgetIds.contentToString()}")
        // Publish a complete RemoteViews tree before reminder recovery does any work.
        TodoWidget.update(context, manager, appWidgetIds)
        Log.i("TodoWidget", "Initial RemoteViews submitted; restoring reminders")
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
