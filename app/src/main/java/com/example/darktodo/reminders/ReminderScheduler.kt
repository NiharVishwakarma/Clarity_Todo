package com.example.darktodo.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.TaskTimeFormatter
import com.example.darktodo.widget.TodoWidget
import org.json.JSONObject
import kotlin.random.Random

object ReminderScheduler {
    private const val CHANNEL = "task_reminders"
    private const val EXTRA_TASK = "reminder_task"
    private const val EXTRA_KIND = "reminder_kind"
    private const val EXTRA_TIME = "reminder_time"
    private const val ACTION_FIRE = "com.example.darktodo.reminders.FIRE"
    private const val HOUR = 3_600_000L

    fun schedule(context: Context, task: Task) {
        if (task.completed || task.reminderType == "none") return
        val now = System.currentTimeMillis()
        val (whenAt, kind, elapsedAlarm) = when (task.reminderType) {
            "time" -> Triple(task.reminderAt, "specific", false)
            "duration" -> {
                val elapsedNow = SystemClock.elapsedRealtime()
                val remaining = TaskTimeFormatter.remainingMillis(context, task, now, elapsedNow)
                if (remaining <= 0L) return
                val (next, kind) = nextDurationTrigger(context, task, elapsedNow, remaining)
                Triple(next, kind, true)
            }
            else -> return
        }
        if (whenAt <= 0L) return
        setAlarm(context, task.id, kind, whenAt, elapsedAlarm)
    }

    fun cancel(context: Context, id: String) {
        clearAlarms(context, id)
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun clearAlarms(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf("specific", "progress", "deadline").forEach { kind ->
            val pi = pending(context, id, kind, 0L, PendingIntent.FLAG_NO_CREATE)
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
    }

    fun replaceAlarms(context: Context, task: Task) {
        clearAlarms(context, task.id)
        schedule(context, task)
    }

    private fun setAlarm(context: Context, id: String, kind: String, at: Long, elapsed: Boolean) {
        val pi = pending(context, id, kind, at, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        val type = if (elapsed) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(type, at, pi); return } catch (_: SecurityException) { }
        }
        if (Build.VERSION.SDK_INT >= 23) manager.setAndAllowWhileIdle(type, at, pi) else manager.set(type, at, pi)
    }

    private fun nextDurationTrigger(context: Context, task: Task, elapsedNow: Long, remaining: Long): Pair<Long, String> {
        val boot = TaskTimeFormatter.bootCount(context)
        val sameBoot = boot >= 0 && task.countdownBootCount == boot && task.countdownDeadlineElapsed > elapsedNow
        val deadline = if (sameBoot) task.countdownDeadlineElapsed else elapsedNow + remaining
        val start = task.countdownStartElapsed.takeIf { sameBoot && it > 0L && it <= elapsedNow }
            ?: (deadline - task.countdownDurationMs.coerceAtLeast(remaining))
        val nextProgress = start + (((elapsedNow - start) / HOUR) + 1L) * HOUR
        return if (nextProgress < deadline) nextProgress to "progress" else deadline to "deadline"
    }

    private fun isCurrentDurationTrigger(context: Context, task: Task, kind: String, scheduledAt: Long, elapsedNow: Long, remaining: Long): Boolean {
        if (scheduledAt > elapsedNow) return false
        val boot = TaskTimeFormatter.bootCount(context)
        val sameBoot = boot >= 0 && task.countdownBootCount == boot && task.countdownDeadlineElapsed > 0L
        val deadline = if (sameBoot) task.countdownDeadlineElapsed else elapsedNow + remaining
        val start = task.countdownStartElapsed.takeIf { it > 0L && it <= elapsedNow }
            ?: (deadline - task.countdownDurationMs.coerceAtLeast(remaining))
        return when (kind) {
            "deadline" -> scheduledAt == deadline
            "progress" -> scheduledAt < deadline && scheduledAt > start && (scheduledAt - start) % HOUR == 0L
            else -> false
        }
    }

    private fun pending(context: Context, id: String, kind: String, at: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).setData(android.net.Uri.parse("todo://reminder/$id/$kind"))
            .putExtra(EXTRA_TASK, id).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_TIME, at)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun handle(context: Context, id: String, kind: String, scheduledAt: Long = 0L) {
        val task = TaskStore.tasks(context).firstOrNull { it.id == id } ?: return
        if (task.completed || task.reminderType == "none") return
        val now = System.currentTimeMillis()
        if (kind == "specific") {
            if (scheduledAt > 0L && scheduledAt != task.reminderAt) { schedule(context, task); return }
            if (task.reminderType != "time" || task.reminderAt > now + 60_000L) { schedule(context, task); return }
            TaskStore.consumeReminder(context, id)
            notify(context, task, task.title, quote(context, "specific:${task.id}:${task.reminderAt}"), false)
            TodoWidget.updateAll(context); return
        }
        if (task.reminderType != "duration") return
        val remaining = TaskTimeFormatter.remainingMillis(context, task, now)
        if (remaining > 0L && scheduledAt > 0L) {
            if (!isCurrentDurationTrigger(context, task, kind, scheduledAt, SystemClock.elapsedRealtime(), remaining)) {
                schedule(context, task); return
            }
        }
        if (remaining <= 0L) {
            if (!task.overdueNotified) {
                TaskStore.markOverdueNotified(context, id)
                notify(context, task, task.title, "Deadline missed. ${quote(context, "deadline:${task.id}:${task.deadlineAt}")}", true)
            }
        } else {
            val left = TaskTimeFormatter.label(context, task, wallNow = now)
            notify(context, task, task.title, "${left ?: "Due now"}. ${quote(context, "progress:${task.id}:${scheduledAt.takeIf { it > 0L } ?: now}")}", false)
        }
        schedule(context, task); TodoWidget.updateAll(context)
    }

    private fun notify(context: Context, task: Task, title: String, body: String, reschedule: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Task Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, task.id.hashCode(), Intent(context, MainActivity::class.java).putExtra("edit_task_id", task.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (reschedule) {
            val action = PendingIntent.getActivity(context, task.id.hashCode() xor 0x51A, Intent(context, MainActivity::class.java).putExtra("edit_task_id", task.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Reschedule", action)
        }
        try { NotificationManagerCompat.from(context).notify(task.id.hashCode(), builder.build()) } catch (_: SecurityException) { }
    }

    private fun quote(context: Context, eventKey: String): String = synchronized(this) {
        val prefs = context.applicationContext.getSharedPreferences("reminder_quotes", Context.MODE_PRIVATE)
        val events = JSONObject(prefs.getString("events", "{}"))
        if (events.has(eventKey)) return@synchronized events.getString(eventKey)
        val quotes = listOf(
            "Discipline beats motivation.", "Small steps. Daily progress.", "Do it, even when it's hard.",
            "Consistency creates results.", "Action over excuses.", "Stay focused. Keep moving.",
            "Your future is built today.", "One task at a time.", "Show up. Get it done.",
            "Progress requires discipline.", "Win the hour.", "Less talking. More doing.",
            "Keep the promise you made.", "Focus now. Benefit later.", "Today's effort builds tomorrow.",
            "Start now. Improve as you go.", "Make today count.", "Finish what you start.",
            "Be consistent, not perfect.", "Earn your own respect."
        )
        val previous = prefs.getString("last_quote", null)
        val choices = quotes.filter { it != previous }.ifEmpty { quotes }
        val selected = choices[Random.nextInt(choices.size)]
        events.put(eventKey, selected)
        prefs.edit().putString("events", events.toString()).putString("last_quote", selected).apply()
        selected
    }

    fun restore(context: Context) {
        TaskStore.recalculateSystemDeadlines(context)
        val tasks = TaskStore.tasks(context)
        tasks.forEach { task ->
            if (task.completed || task.reminderType == "none") clearAlarms(context, task.id)
            else replaceAlarms(context, task)
        }
        tasks.forEach { task ->
            if (task.reminderType == "duration" && TaskTimeFormatter.remainingMillis(context, task) <= 0L && !task.overdueNotified) handle(context, task.id, "deadline", task.deadlineAt)
        }
        TodoWidget.updateAll(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == "android.intent.action.TIME_SET" || intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) java.util.TimeZone.setDefault(null)
            ReminderScheduler.restore(context); return
        }
        if (intent.action == "com.example.darktodo.reminders.FIRE") {
            val id = intent.getStringExtra("reminder_task") ?: return
            val kind = intent.getStringExtra("reminder_kind") ?: return
            ReminderScheduler.handle(context, id, kind, intent.getLongExtra("reminder_time", 0L))
        }
    }
}
