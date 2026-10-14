package com.example.darktodo.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.darktodo.data.TaskStore
import com.example.darktodo.data.TaskTimeFormatter

/** Maintains one alarm for the next visible countdown text transition. */
object WidgetRefreshScheduler {
    private const val ACTION_REFRESH = "com.example.darktodo.widget.REFRESH_COUNTDOWNS"
    private const val MINUTE = 60_000L

    fun reconcile(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, TodoWidgetReceiver::class.java))
        val tasks = TaskStore.tasks(app).filter { !it.completed && it.reminderType == "duration" }
        val wallNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        val nextAt = tasks.mapNotNull { task ->
            val remaining = TaskTimeFormatter.remainingMillis(app, task, wallNow, elapsedNow)
            TaskTimeFormatter.nextMinuteTransition(wallNow, remaining)
        }.minOrNull()
        val pending = pendingIntent(app, PendingIntent.FLAG_NO_CREATE)
        if (ids.isEmpty() || nextAt == null) {
            if (pending != null) {
                app.getSystemService(AlarmManager::class.java).cancel(pending)
                pending.cancel()
            }
            return
        }

        val alarm = app.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // Inexact allow-while-idle avoids requiring special exact-alarm access.
        // The receiver always recalculates from the persisted deadline.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt.coerceAtLeast(wallNow + 1L), pi)
    }

    fun refresh(context: Context) {
        TodoWidget.updateAll(context)
    }

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 7341, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.darktodo.widget.REFRESH_COUNTDOWNS") {
            WidgetRefreshScheduler.refresh(context)
        }
    }
}
