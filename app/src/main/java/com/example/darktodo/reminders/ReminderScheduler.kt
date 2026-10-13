package com.example.darktodo.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.Task
import com.example.darktodo.data.TaskStore
import com.example.darktodo.widget.TodoWidget
import org.json.JSONObject
import kotlin.random.Random
import java.util.concurrent.TimeUnit

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
        val (whenAt, kind) = when (task.reminderType) {
            "time" -> task.reminderAt to "specific"
            "duration" -> {
                if (task.deadlineAt <= now) return
                val start = task.countdownStartedAt.takeIf { it > 0L } ?: now
                val next = start + (((now - start) / HOUR) + 1L) * HOUR
                (if (next < task.deadlineAt) next else task.deadlineAt) to if (next < task.deadlineAt) "progress" else "deadline"
            }
            else -> return
        }
        if (whenAt <= 0L) return
        setAlarm(context, task.id, kind, whenAt)
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf("specific", "progress", "deadline").forEach { kind ->
            val pi = pending(context, id, kind, 0L, PendingIntent.FLAG_NO_CREATE)
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }

    private fun setAlarm(context: Context, id: String, kind: String, at: Long) {
        val pi = pending(context, id, kind, at, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
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
            if (task.reminderType != "time" || task.reminderAt > now + 60_000L) { schedule(context, task); return }
            notify(context, task, task.title, quote(context, "specific:${task.id}:${task.reminderAt}"), false)
            TaskStore.consumeReminder(context, id)
            TodoWidget.updateAll(context); return
        }
        if (task.reminderType != "duration") return
        if (task.deadlineAt <= now) {
            if (!task.overdueNotified) {
                notify(context, task, task.title, "Deadline missed. ${quote(context, "deadline:${task.id}:${task.deadlineAt}")}", true)
                TaskStore.markOverdueNotified(context, id)
            }
        } else {
            val remaining = task.deadlineAt - now
            val hours = TimeUnit.MILLISECONDS.toHours(remaining)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
            val left = when { hours > 0 && minutes > 0 -> "$hours h $minutes min left"; hours > 0 -> "$hours ${if (hours == 1L) "hour" else "hours"} left"; else -> "${maxOf(1, TimeUnit.MILLISECONDS.toMinutes(remaining))} min left" }
            notify(context, task, task.title, "$left. ${quote(context, "progress:${task.id}:${scheduledAt.takeIf { it > 0L } ?: now}")}", false)
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
        TaskStore.tasks(context).forEach { task ->
            if (task.reminderType == "duration" && task.deadlineAt <= System.currentTimeMillis() && !task.overdueNotified) handle(context, task.id, "deadline", task.deadlineAt)
            else schedule(context, task)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == "android.intent.action.TIME_SET" || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            ReminderScheduler.restore(context); return
        }
        if (intent.action == "com.example.darktodo.reminders.FIRE") {
            val id = intent.getStringExtra("reminder_task") ?: return
            val kind = intent.getStringExtra("reminder_kind") ?: return
            ReminderScheduler.handle(context, id, kind, intent.getLongExtra("reminder_time", 0L))
        }
    }
}
