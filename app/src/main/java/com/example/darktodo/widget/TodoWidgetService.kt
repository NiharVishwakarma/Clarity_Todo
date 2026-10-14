package com.example.darktodo.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TaskRowsFactory(applicationContext)
}

private class TaskRowsFactory(private val context: android.content.Context) : RemoteViewsService.RemoteViewsFactory {
    private var tasks: List<Task> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        tasks = TaskStore.tasks(context)
    }

    override fun onDestroy() {
        tasks = emptyList()
    }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews? =
        tasks.getOrNull(position)?.let { TodoWidget.renderTaskRow(context, it) }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
