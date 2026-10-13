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
        val completedMinutes = remaining / 60_000L
        // Keep minute-level precision without letting a few seconds after an
        // exact hour appear as a whole-hour countdown.
        val wholeMinutes = when {
            completedMinutes == 0L -> 1L
            completedMinutes % 60L == 0L && remaining % 60_000L != 0L -> completedMinutes + 1L
            else -> completedMinutes
        }
        val hours = wholeMinutes / 60L
        val minutes = wholeMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
            hours > 0L -> "${hours}h left"
            else -> "${wholeMinutes}m left"
        }
    }

    fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)
}
