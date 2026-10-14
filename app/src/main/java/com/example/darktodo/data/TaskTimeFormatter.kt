package com.example.darktodo.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Shared task deadline calculation and compact labels for Compose and RemoteViews. */
object TaskTimeFormatter {
    fun remainingMillis(context: Context, task: Task, wallNow: Long = System.currentTimeMillis(), elapsedNow: Long = SystemClock.elapsedRealtime()): Long {
        if (task.reminderType == "duration" && task.countdownDeadlineElapsed > 0L) {
            val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
            if (boot >= 0 && task.countdownBootCount == boot) return task.countdownDeadlineElapsed - elapsedNow
        }
        val due = if (task.reminderType == "time") task.reminderAt else task.deadlineAt
        return due - wallNow
    }

    fun label(context: Context, task: Task, wallNow: Long = System.currentTimeMillis(), elapsedNow: Long = SystemClock.elapsedRealtime()): String? {
        if (task.completed || task.reminderType == "none") return null
        val remaining = remainingMillis(context, task, wallNow, elapsedNow)
        if (remaining < 0L) return if (task.reminderType == "duration") "OVERDUE" else "Due now"
        if (remaining == 0L) return "Due now"
        if (task.reminderType == "time") {
            val time = if (task.followsDeviceTimeZone || task.timeZoneId.isBlank()) DateFormat.getTimeFormat(context).format(Date(task.reminderAt)) else runCatching {
                val zone = ZoneId.of(task.timeZoneId)
                val zoned = Instant.ofEpochMilli(task.reminderAt).atZone(zone)
                val skeleton = if (DateFormat.is24HourFormat(context)) "Hm z" else "hm z"
                val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
                DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(zoned)
            }.getOrElse { DateFormat.getTimeFormat(context).format(Date(task.reminderAt)) }
            return "Due $time"
        }
        return countdownLabel(remaining)
    }

    /** The widget shows time remaining for both clock-time and duration reminders. */
    fun widgetLabel(context: Context, task: Task, wallNow: Long = System.currentTimeMillis(), elapsedNow: Long = SystemClock.elapsedRealtime()): String? {
        if (task.completed || task.reminderType == "none") return null
        return countdownLabel(remainingMillis(context, task, wallNow, elapsedNow))
    }

    /** Pure formatter shared by app and widget, with positive durations rounded up. */
    fun countdownLabel(remainingMillis: Long): String {
        if (remainingMillis < 0L) return "OVERDUE"
        if (remainingMillis == 0L) return "Due now"
        val wholeMinutes = ((remainingMillis - 1L) / 60_000L) + 1L
        val hours = wholeMinutes / 60L
        val minutes = wholeMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
            hours > 0L -> "${hours}h left"
            else -> "${wholeMinutes}m left"
        }
    }

    /** Wall-clock time when the rendered minute label next changes. */
    fun nextMinuteTransition(wallNow: Long, remainingMillis: Long): Long? = when {
        remainingMillis > 0L -> {
            val shownMinutes = ((remainingMillis - 1L) / 60_000L) + 1L
            wallNow + remainingMillis - (shownMinutes - 1L) * 60_000L
        }
        remainingMillis == 0L -> wallNow + 1L
        else -> null
    }

    fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)
}
