package com.example.darktodo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Task(val id: String, val title: String, val completed: Boolean, val reminderType: String = "none", val reminderAt: Long = 0L, val deadlineAt: Long = 0L, val overdueNotified: Boolean = false, val countdownStartedAt: Long = 0L)
data class TaskEvent(val id: String, val taskId: String, val title: String, val type: String, val at: Long)

/** Active tasks and the append-only event log are intentionally kept in separate keys. */
object TaskStore {
    private const val PREFS = "todo_store"
    private const val ACTIVE_KEY = "active_tasks_v1"
    private const val HISTORY_KEY = "task_history_v1"
    private val lock = Any()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tasks(context: Context): List<Task> = synchronized(lock) {
        val array = JSONArray(prefs(context).getString(ACTIVE_KEY, "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { o -> Task(o.getString("id"), o.getString("title"), o.getBoolean("completed"), o.optString("reminderType", "none"), o.optLong("reminderAt"), o.optLong("deadlineAt"), o.optBoolean("overdueNotified"), o.optLong("countdownStartedAt")) } }
    }

    fun history(context: Context): List<TaskEvent> = synchronized(lock) {
        val array = JSONArray(prefs(context).getString(HISTORY_KEY, "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { TaskEvent(it.getString("id"), it.getString("taskId"), it.getString("title"), it.getString("type"), it.getLong("at")) } }.sortedBy { it.at }
    }

    private fun saveTasks(context: Context, values: List<Task>) {
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("completed", it.completed).put("reminderType", it.reminderType).put("reminderAt", it.reminderAt).put("deadlineAt", it.deadlineAt).put("overdueNotified", it.overdueNotified).put("countdownStartedAt", it.countdownStartedAt)) }
        prefs(context).edit().putString(ACTIVE_KEY, array.toString()).apply()
    }

    private fun log(context: Context, task: Task, type: String) {
        val values = JSONArray(prefs(context).getString(HISTORY_KEY, "[]"))
        values.put(JSONObject().put("id", UUID.randomUUID().toString()).put("taskId", task.id).put("title", task.title).put("type", type).put("at", System.currentTimeMillis()))
        prefs(context).edit().putString(HISTORY_KEY, values.toString()).apply()
    }

    fun add(context: Context, title: String) = synchronized(lock) {
        val task = Task(UUID.randomUUID().toString(), title.trim(), false)
        saveTasks(context, (tasks(context) + task).sortedBy { it.completed })
        log(context, task, "Created")
    }

    fun addConfigured(context: Context, title: String, type: String, at: Long) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val task = Task(UUID.randomUUID().toString(), title.trim(), false, type, if (type == "time") at else 0L, if (type == "duration") at else 0L, countdownStartedAt = if (type == "duration") now else 0L)
        saveTasks(context, (tasks(context) + task).sortedBy { it.completed }); log(context, task, "Created")
        com.example.darktodo.reminders.ReminderScheduler.schedule(context, task)
    }

    fun configure(context: Context, id: String, title: String, type: String, at: Long) = synchronized(lock) {
        if (title.isBlank()) return@synchronized
        val current = tasks(context); val old = current.firstOrNull { it.id == id } ?: return@synchronized
        val now = System.currentTimeMillis()
        val next = if (old.completed) old.copy(title = title.trim(), reminderType = "none", reminderAt = 0L, deadlineAt = 0L, overdueNotified = false, countdownStartedAt = 0L) else old.copy(title = title.trim(), reminderType = type, reminderAt = if (type == "time") at else 0L, deadlineAt = if (type == "duration") at else 0L, overdueNotified = false, countdownStartedAt = if (type == "duration") now else 0L)
        saveTasks(context, current.map { if (it.id == id) next else it }); com.example.darktodo.reminders.ReminderScheduler.cancel(context, id); com.example.darktodo.reminders.ReminderScheduler.schedule(context, next)
    }

    fun toggle(context: Context, id: String) = synchronized(lock) {
        val current = tasks(context)
        val old = current.firstOrNull { it.id == id } ?: return@synchronized
        val next = old.copy(completed = !old.completed)
        saveTasks(context, current.map { if (it.id == id) next else it }.sortedBy { it.completed })
        if (next.completed) com.example.darktodo.reminders.ReminderScheduler.cancel(context, id)
        log(context, next, if (next.completed) "Completed" else "Reopened")
    }

    /** Moves a task within its own incomplete/completed group, preserving the widget's group order. */
    fun move(context: Context, id: String, direction: Int) = synchronized(lock) {
        if (direction != -1 && direction != 1) return@synchronized
        val current = tasks(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized
        val neighbor = index + direction
        if (neighbor !in current.indices || current[neighbor].completed != current[index].completed) return@synchronized
        val task = current[index]
        current[index] = current[neighbor]
        current[neighbor] = task
        saveTasks(context, current)
    }

    fun delete(context: Context, id: String) = synchronized(lock) {
        val current = tasks(context)
        val item = current.firstOrNull { it.id == id } ?: return@synchronized
        saveTasks(context, current.filterNot { it.id == id })
        com.example.darktodo.reminders.ReminderScheduler.cancel(context, id)
        log(context, item, if (item.completed) "Deleted completed task" else "Deleted incomplete task")
    }

    fun markOverdueNotified(context: Context, id: String) = synchronized(lock) {
        saveTasks(context, tasks(context).map { if (it.id == id) it.copy(overdueNotified = true) else it })
    }

    fun clearCompleted(context: Context) = synchronized(lock) {
        val current = tasks(context)
        current.filter { it.completed }.forEach { log(context, it, "Deleted completed task") }
        saveTasks(context, current.filterNot { it.completed })
        current.filter { it.completed }.forEach { com.example.darktodo.reminders.ReminderScheduler.cancel(context, it.id) }
    }

    fun deleteEvent(context: Context, id: String) = synchronized(lock) {
        saveHistory(context, history(context).filterNot { it.id == id })
    }

    fun rename(context: Context, id: String, title: String) = synchronized(lock) {
        if (title.isBlank()) return@synchronized
        saveTasks(context, tasks(context).map { if (it.id == id) it.copy(title = title.trim()) else it })
    }

    fun deleteEvents(context: Context, scope: String) = synchronized(lock) {
        val zone = ZoneId.systemDefault()
        val formatter = when (scope.length) { 4 -> DateTimeFormatter.ofPattern("yyyy"); 7 -> DateTimeFormatter.ofPattern("yyyy-MM"); else -> DateTimeFormatter.ofPattern("yyyy-MM-dd") }
        saveHistory(context, history(context).filterNot { formatter.withZone(zone).format(Instant.ofEpochMilli(it.at)) == scope })
    }

    private fun saveHistory(context: Context, events: List<TaskEvent>) {
        val array = JSONArray()
        events.sortedBy { it.at }.forEach { array.put(JSONObject().put("id", it.id).put("taskId", it.taskId).put("title", it.title).put("type", it.type).put("at", it.at)) }
        prefs(context).edit().putString(HISTORY_KEY, array.toString()).apply()
    }

    fun clearHistory(context: Context) = synchronized(lock) { prefs(context).edit().putString(HISTORY_KEY, "[]").apply() }

    fun dateTime(millis: Long): String = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
}
