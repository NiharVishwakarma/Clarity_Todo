package com.example.darktodo.data

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Task(val id: String, val title: String, val completed: Boolean, val reminderType: String = "none", val reminderAt: Long = 0L, val deadlineAt: Long = 0L, val overdueNotified: Boolean = false, val countdownStartedAt: Long = 0L, val countdownDurationMs: Long = 0L, val countdownStartElapsed: Long = 0L, val countdownDeadlineElapsed: Long = 0L, val countdownBootCount: Int = -1, val localDateTime: String = "", val timeZoneId: String = "", val followsDeviceTimeZone: Boolean = true, val taskDate: String = LocalDate.now().toString())
data class TaskEvent(val id: String, val taskId: String, val title: String, val type: String, val at: Long)

/** Active tasks and the append-only event log are intentionally kept in separate keys. */
object TaskStore {
    private const val PREFS = "todo_store"
    private const val ACTIVE_KEY = "active_tasks_v1"
    private const val HISTORY_KEY = "task_history_v1"
    private val lock = Any()
    // Parsed copy of the stored JSON, valid only while the stored string is unchanged.
    private var cachedRaw: String? = null
    private var cachedTasks: List<Task> = emptyList()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tasks(context: Context): List<Task> = synchronized(lock) {
        val raw = prefs(context).getString(ACTIVE_KEY, "[]") ?: "[]"
        if (raw == cachedRaw) return@synchronized cachedTasks
        val array = JSONArray(raw)
        val zone = ZoneId.systemDefault()
        val loaded = (0 until array.length()).map { i -> array.getJSONObject(i).let { o ->
            val taskDate = o.optString("taskDate", "").takeIf { value -> runCatching { LocalDate.parse(value) }.isSuccess } ?: runCatching {
                when {
                    o.optString("localDateTime").isNotBlank() -> LocalDateTime.parse(o.getString("localDateTime")).toLocalDate().toString()
                    o.optString("reminderType") == "time" && o.optLong("reminderAt") > 0L -> {
                        val reminderZone = runCatching { ZoneId.of(o.optString("timeZoneId").ifBlank { zone.id }) }.getOrDefault(zone)
                        Instant.ofEpochMilli(o.getLong("reminderAt")).atZone(reminderZone).toLocalDate().toString()
                    }
                    o.optString("reminderType") == "duration" && o.optLong("deadlineAt") > 0L -> Instant.ofEpochMilli(o.getLong("deadlineAt")).atZone(zone).toLocalDate().toString()
                    else -> LocalDate.now(zone).toString()
                }
            }.getOrDefault(LocalDate.now(zone).toString())
            Task(o.getString("id"), o.getString("title"), o.getBoolean("completed"), o.optString("reminderType", "none"), o.optLong("reminderAt"), o.optLong("deadlineAt"), o.optBoolean("overdueNotified"), o.optLong("countdownStartedAt"), o.optLong("countdownDurationMs"), o.optLong("countdownStartElapsed"), o.optLong("countdownDeadlineElapsed"), o.optInt("countdownBootCount", -1), o.optString("localDateTime", ""), o.optString("timeZoneId", ""), o.optBoolean("followsDeviceTimeZone", true), taskDate)
        } }
        if ((0 until array.length()).any { i -> runCatching { LocalDate.parse(array.getJSONObject(i).optString("taskDate")) }.isFailure }) saveTasks(context, loaded)
        else { cachedRaw = raw; cachedTasks = loaded }
        loaded
    }

    fun history(context: Context): List<TaskEvent> = synchronized(lock) {
        val array = JSONArray(prefs(context).getString(HISTORY_KEY, "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { TaskEvent(it.getString("id"), it.getString("taskId"), it.getString("title"), it.getString("type"), it.getLong("at")) } }.sortedBy { it.at }
    }

    private fun saveTasks(context: Context, values: List<Task>) {
        // Calendar dots depend only on id, date and completion; skip its refresh when none changed.
        val previous = if (cachedRaw != null) cachedTasks else null
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("completed", it.completed).put("reminderType", it.reminderType).put("reminderAt", it.reminderAt).put("deadlineAt", it.deadlineAt).put("overdueNotified", it.overdueNotified).put("countdownStartedAt", it.countdownStartedAt).put("countdownDurationMs", it.countdownDurationMs).put("countdownStartElapsed", it.countdownStartElapsed).put("countdownDeadlineElapsed", it.countdownDeadlineElapsed).put("countdownBootCount", it.countdownBootCount).put("localDateTime", it.localDateTime).put("timeZoneId", it.timeZoneId).put("followsDeviceTimeZone", it.followsDeviceTimeZone).put("taskDate", it.taskDate)) }
        // Alarm scheduling happens immediately after this call. Commit the task state first so
        // a process/device crash cannot leave an alarm whose task data was only in memory.
        check(prefs(context).edit().putString(ACTIVE_KEY, array.toString()).commit()) {
            "Could not persist active tasks"
        }
        cachedRaw = null
        fun calendarView(list: List<Task>) = list.map { Triple(it.id, it.taskDate, it.completed) }.toSet()
        if (previous == null || calendarView(previous) != calendarView(values)) com.example.darktodo.widget.CalendarWidget.updateAll(context)
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
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun addConfigured(context: Context, title: String, type: String, at: Long, timeZone: String = "", taskDate: String = LocalDate.now().toString()) = synchronized(lock) {
        val now = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime(); val duration = (at - now).coerceAtLeast(0L)
        val local = if (type == "time") Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime() else null
        val zone = timeZone.ifBlank { ZoneId.systemDefault().id }
        val resolvedAt = if (local != null) TimeZoneCatalog.resolveLocalDateTime(local, zone).toInstant().toEpochMilli() else 0L
        val task = Task(UUID.randomUUID().toString(), title.trim(), false, type, if (type == "time") resolvedAt else 0L, if (type == "duration") at else 0L,
            countdownStartedAt = if (type == "duration") now else 0L,
            countdownDurationMs = if (type == "duration") duration else 0L,
            countdownStartElapsed = if (type == "duration") elapsed else 0L,
            countdownDeadlineElapsed = if (type == "duration") elapsed + duration else 0L,
            countdownBootCount = if (type == "duration") TaskTimeFormatter.bootCount(context) else -1,
            localDateTime = local?.toString() ?: "", timeZoneId = if (type == "time") zone else "", followsDeviceTimeZone = timeZone.isBlank(), taskDate = validDate(taskDate))
        saveTasks(context, (tasks(context) + task).sortedBy { it.completed }); log(context, task, "Created")
        com.example.darktodo.reminders.ReminderScheduler.schedule(context, task)
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun configure(context: Context, id: String, title: String, type: String, at: Long, timeZone: String = "", taskDate: String = LocalDate.now().toString()) = synchronized(lock) {
        if (title.isBlank()) return@synchronized
        val current = tasks(context); val old = current.firstOrNull { it.id == id } ?: return@synchronized
        val now = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime(); val boot = TaskTimeFormatter.bootCount(context)
        val duration = (at - now).coerceAtLeast(0L)
        val unchangedSpecificReminder = type == "time" && old.reminderType == "time" && at == old.reminderAt && timeZone.trim() == if (old.followsDeviceTimeZone) "" else old.timeZoneId
        val local = if (type == "time") Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDateTime() else null
        val zone = timeZone.ifBlank { ZoneId.systemDefault().id }
        val resolvedAt = if (local != null) TimeZoneCatalog.resolveLocalDateTime(local, zone).toInstant().toEpochMilli() else 0L
        val next = if (old.completed) old.copy(title = title.trim(), reminderType = "none", reminderAt = 0L, deadlineAt = 0L, overdueNotified = false, countdownStartedAt = 0L, countdownDurationMs = 0L, countdownStartElapsed = 0L, countdownDeadlineElapsed = 0L, countdownBootCount = -1, localDateTime = "", timeZoneId = "", taskDate = validDate(taskDate)) else if (unchangedSpecificReminder) old.copy(title = title.trim(), taskDate = validDate(taskDate)) else old.copy(
            title = title.trim(), reminderType = type, reminderAt = if (type == "time") resolvedAt else 0L,
            deadlineAt = if (type == "duration") at else 0L, overdueNotified = false,
            countdownStartedAt = if (type == "duration") now else 0L,
            countdownDurationMs = if (type == "duration") duration else 0L,
            countdownStartElapsed = if (type == "duration") elapsed else 0L,
            countdownDeadlineElapsed = if (type == "duration") elapsed + duration else 0L,
            countdownBootCount = if (type == "duration") boot else -1,
            localDateTime = local?.toString() ?: "", timeZoneId = if (type == "time") zone else "", followsDeviceTimeZone = timeZone.isBlank(), taskDate = validDate(taskDate))
        saveTasks(context, current.map { if (it.id == id) next else it }); com.example.darktodo.reminders.ReminderScheduler.cancel(context, id); com.example.darktodo.reminders.ReminderScheduler.schedule(context, next)
        if (old.title != next.title || old.reminderType != next.reminderType || old.reminderAt != next.reminderAt || old.deadlineAt != next.deadlineAt || old.localDateTime != next.localDateTime || old.timeZoneId != next.timeZoneId) log(context, next, "Edited")
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun updateTitleAndDate(context: Context, id: String, title: String, taskDate: String) = synchronized(lock) {
        if (title.isBlank()) return@synchronized
        val current = tasks(context)
        val old = current.firstOrNull { it.id == id } ?: return@synchronized
        val next = old.copy(title = title.trim(), taskDate = validDate(taskDate))
        saveTasks(context, current.map { if (it.id == id) next else it })
        if (old.title != next.title || old.taskDate != next.taskDate) log(context, next, "Edited")
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun toggle(context: Context, id: String) = synchronized(lock) {
        val current = tasks(context)
        val old = current.firstOrNull { it.id == id } ?: return@synchronized
        val next = old.copy(completed = !old.completed)
        saveTasks(context, current.map { if (it.id == id) next else it }.sortedBy { it.completed })
        if (next.completed) com.example.darktodo.reminders.ReminderScheduler.cancel(context, id)
        else if (next.reminderType == "duration" && TaskTimeFormatter.remainingMillis(context, next) <= 0L && !next.overdueNotified) {
            com.example.darktodo.reminders.ReminderScheduler.handle(context, id, "deadline")
        } else com.example.darktodo.reminders.ReminderScheduler.schedule(context, next)
        log(context, next, if (next.completed) "Completed" else "Reopened")
        com.example.darktodo.widget.TodoWidget.updateAll(context)
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
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun delete(context: Context, id: String) = synchronized(lock) {
        val current = tasks(context)
        val item = current.firstOrNull { it.id == id } ?: return@synchronized
        saveTasks(context, current.filterNot { it.id == id })
        com.example.darktodo.reminders.ReminderScheduler.cancel(context, id)
        log(context, item, if (item.completed) "Deleted completed task" else "Deleted incomplete task")
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun markOverdueNotified(context: Context, id: String) = synchronized(lock) {
        saveTasks(context, tasks(context).map { if (it.id == id) it.copy(overdueNotified = true) else it })
    }

    fun consumeReminder(context: Context, id: String) = synchronized(lock) {
        saveTasks(context, tasks(context).map { if (it.id == id) it.copy(reminderType = "none", reminderAt = 0L, deadlineAt = 0L, countdownStartedAt = 0L, countdownDurationMs = 0L, countdownStartElapsed = 0L, countdownDeadlineElapsed = 0L, countdownBootCount = -1, localDateTime = "", timeZoneId = "") else it })
    }

    fun consumeReminders(context: Context, ids: Set<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        saveTasks(context, tasks(context).map { task ->
            if (task.id in ids) task.copy(
                reminderType = "none", reminderAt = 0L, deadlineAt = 0L, overdueNotified = false,
                countdownStartedAt = 0L, countdownDurationMs = 0L, countdownStartElapsed = 0L,
                countdownDeadlineElapsed = 0L, countdownBootCount = -1, localDateTime = "", timeZoneId = ""
            ) else task
        })
    }

    fun recalculateSystemDeadlines(context: Context) = synchronized(lock) {
        val now = System.currentTimeMillis(); val elapsed = SystemClock.elapsedRealtime(); val boot = TaskTimeFormatter.bootCount(context)
        val zone = ZoneId.systemDefault()
        val updated = tasks(context).map { task ->
            when {
                task.reminderType == "time" -> runCatching {
                    val hasSavedLocalTime = task.localDateTime.isNotBlank()
                    val local = if (hasSavedLocalTime) LocalDateTime.parse(task.localDateTime) else Instant.ofEpochMilli(task.reminderAt).atZone(zone).toLocalDateTime()
                    val followsDevice = if (hasSavedLocalTime) task.followsDeviceTimeZone else true
                    val targetZone = if (followsDevice) zone else ZoneId.of(task.timeZoneId)
                    task.copy(reminderAt = local.atZone(targetZone).toInstant().toEpochMilli(), localDateTime = local.toString(), timeZoneId = targetZone.id, followsDeviceTimeZone = followsDevice)
                }.getOrDefault(task)
                task.reminderType == "duration" -> {
                    val sameBoot = boot >= 0 && task.countdownDeadlineElapsed > 0L && task.countdownBootCount == boot
                    val remaining = if (sameBoot) task.countdownDeadlineElapsed - elapsed else task.deadlineAt - now
                    val totalDuration = task.countdownDurationMs.takeIf { it > 0L } ?: (task.deadlineAt - task.countdownStartedAt).coerceAtLeast(remaining).coerceAtLeast(0L)
                    val elapsedDeadline = if (sameBoot) task.countdownDeadlineElapsed else elapsed + remaining
                    val elapsedStart = if (sameBoot && task.countdownStartElapsed > 0L) task.countdownStartElapsed else elapsedDeadline - totalDuration
                    task.copy(deadlineAt = now + remaining, countdownDurationMs = totalDuration, countdownStartElapsed = elapsedStart, countdownDeadlineElapsed = elapsedDeadline, countdownBootCount = boot)
                }
                else -> task
            }
        }
        saveTasks(context, updated)
    }

    fun clearCompleted(context: Context) = synchronized(lock) {
        val current = tasks(context)
        current.filter { it.completed }.forEach { log(context, it, "Deleted completed task") }
        saveTasks(context, current.filterNot { it.completed })
        current.filter { it.completed }.forEach { com.example.darktodo.reminders.ReminderScheduler.cancel(context, it.id) }
        com.example.darktodo.widget.TodoWidget.updateAll(context)
    }

    fun deleteEvent(context: Context, id: String) = synchronized(lock) {
        saveHistory(context, history(context).filterNot { it.id == id })
    }

    fun rename(context: Context, id: String, title: String) = synchronized(lock) {
        if (title.isBlank()) return@synchronized
        saveTasks(context, tasks(context).map { if (it.id == id) it.copy(title = title.trim()) else it })
        com.example.darktodo.widget.TodoWidget.updateAll(context)
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

    private fun validDate(value: String): String = runCatching { LocalDate.parse(value).toString() }.getOrDefault(LocalDate.now().toString())

    fun clearHistory(context: Context) = synchronized(lock) { prefs(context).edit().putString(HISTORY_KEY, "[]").apply() }

    fun dateTime(context: Context, millis: Long, timeZoneId: String = ""): String {
        if (timeZoneId.isBlank()) return "${android.text.format.DateFormat.format("dd MMM yyyy", java.util.Date(millis))}, ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))}"
        return runCatching {
            val locale = java.util.Locale.getDefault()
            val zone = ZoneId.of(timeZoneId.trim())
            val local = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val zoned = local.atZone(zone)
            val timeSkeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "Hm z" else "hm z"
            val datePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMd")
            val timePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, timeSkeleton)
            java.time.format.DateTimeFormatter.ofPattern("$datePattern $timePattern", locale).format(zoned)
        }.getOrElse { "${android.text.format.DateFormat.format("dd MMM yyyy", java.util.Date(millis))}, ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))}" }
    }
}
