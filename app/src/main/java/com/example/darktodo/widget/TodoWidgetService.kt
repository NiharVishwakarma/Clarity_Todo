package com.example.darktodo.widget

import android.content.Intent
import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.util.Log
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val widthDp = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) 320 else
            AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(1)
        return TaskRowsFactory(applicationContext, widthDp)
    }
}

private class TaskRowsFactory(private val context: android.content.Context, private val widgetWidthDp: Int) : RemoteViewsService.RemoteViewsFactory {
    private var tasks: List<Task> = emptyList()
    private var settings: com.example.darktodo.data.WidgetPrefs? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        tasks = runCatching { TaskStore.tasks(context) }
            .onFailure { Log.e("TodoWidgetService", "Unable to load widget task rows", it) }
            .getOrDefault(emptyList())
        settings = runCatching { com.example.darktodo.data.WidgetSettings.load(context) }.getOrNull()
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews? =
        tasks.getOrNull(position)?.let { TodoWidget.renderTaskRow(context, it, widgetWidthDp, settings) }

    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, com.example.darktodo.R.layout.widget_task_loading)

    override fun getViewTypeCount(): Int = WidgetFontLayouts.TASK_ROW_VIEW_TYPE_COUNT

    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
