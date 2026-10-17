package com.example.darktodo.reminders

import java.time.Instant
import java.time.ZoneId

/** Pure helpers used for reminder delivery decisions and diagnostics. */
object ReminderTiming {
    /** A delivery later than this is reported as delayed (typical of inexact/Doze/OEM-throttled alarms). */
    const val LATE_THRESHOLD_MS = 60_000L
    const val EARLY_TOLERANCE_MS = 60_000L

    fun isDue(triggerAt: Long, now: Long): Boolean = triggerAt in 1..(now + EARLY_TOLERANCE_MS)

    fun latenessMs(triggerAt: Long, now: Long): Long = now - triggerAt

    fun isLate(triggerAt: Long, now: Long): Boolean = triggerAt > 0L && latenessMs(triggerAt, now) > LATE_THRESHOLD_MS

    /** Human-readable instant with zone and offset, for logs that must be comparable with device time. */
    fun describe(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        if (epochMs <= 0L) "none" else "$epochMs(${Instant.ofEpochMilli(epochMs).atZone(zone)})"
}
