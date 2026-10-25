package com.example.darktodo.monitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import android.util.Log
import java.time.Instant
import java.time.ZoneId

enum class ScreenTimeStatus { AVAILABLE, PARTIAL, PERMISSION_REQUIRED, UNAVAILABLE }

data class AppUsage(val packageName: String, val durationMillis: Long)

data class ScreenTimeResult(
    val status: ScreenTimeStatus,
    val durationMillis: Long = 0,
    val apps: Map<String, Long> = emptyMap()
) {
    val value: String get() = when (status) {
        ScreenTimeStatus.AVAILABLE -> ScreenTime.duration(durationMillis)
        ScreenTimeStatus.PARTIAL -> "Partial: ${ScreenTime.duration(durationMillis)}"
        ScreenTimeStatus.PERMISSION_REQUIRED -> "Allow access"
        ScreenTimeStatus.UNAVAILABLE -> "Unavailable"
    }
    val supporting: String get() = if (status == ScreenTimeStatus.PERMISSION_REQUIRED) "Usage Access required" else "Since midnight"

    /** Apps with usage today, longest first; zero-duration entries are omitted. */
    val ranked: List<AppUsage> get() = apps.filter { it.key.isNotBlank() && it.value > 0 }
        .map { AppUsage(it.key, it.value) }
        .sortedWith(compareByDescending<AppUsage> { it.durationMillis }.thenBy { it.packageName })
}

/** Ephemeral usage data: never written into monitor snapshots, reports or backup preferences. */
object ScreenTime {
    data class Event(val at: Long, val type: Int, val activity: String = "")
    const val RESUMED = 1
    const val PAUSED = 2
    const val SCREEN_OFF = 16
    const val KEYGUARD_SHOWN = 17
    const val SHUTDOWN = 26
    const val STARTUP = 27
    private val eventTypes = setOf(RESUMED, PAUSED, SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN, STARTUP)

    fun midnight(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun nextMidnight(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun duration(millis: Long): String {
        val minutes = millis.coerceAtLeast(0) / 60_000
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    /** Per-app label: sub-minute usage is shown as "<1m" rather than a misleading "0m". */
    fun appDuration(millis: Long): String = if (millis in 1 until 60_000) "<1m" else duration(millis)

    /** A widget tap goes straight to Usage Access only when access is missing on a fresh launch. */
    fun shouldOpenUsageAccess(fromWidget: Boolean, restored: Boolean, hasAccess: Boolean): Boolean =
        fromWidget && !restored && !hasAccess

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun read(context: Context): ScreenTimeResult {
        try {
            if (!hasAccess(context)) return ScreenTimeResult(ScreenTimeStatus.PERMISSION_REQUIRED)
            if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true)
                return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val start = midnight(now, zone)
            // Seed an activity that was already resumed at midnight, without counting yesterday's usage.
            val lookback = Instant.ofEpochMilli(start).atZone(zone).minusDays(1).toInstant().toEpochMilli()
            val events = manager.queryEvents(lookback, now) ?: return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
            val relevant = ArrayList<Event>()
            val event = UsageEvents.Event()
            val own = context.packageName
            var count = 0
            while (events.hasNextEvent()) {
                if (++count > 100_000) {
                    Log.w("ScreenTime", "Usage event query exceeded the processing limit")
                    return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
                }
                events.getNextEvent(event)
                // Clarity's own foreground time is excluded from device screen time.
                if (event.eventType in eventTypes &&
                    !(event.packageName == own && (event.eventType == RESUMED || event.eventType == PAUSED))) {
                    relevant.add(Event(event.timeStamp, event.eventType, "${event.packageName}/${event.className}"))
                }
            }
            return calculate(relevant, start, now)
        } catch (error: SecurityException) {
            Log.w("ScreenTime", "Usage access was denied", error)
            return ScreenTimeResult(ScreenTimeStatus.PERMISSION_REQUIRED)
        } catch (error: RuntimeException) {
            Log.e("ScreenTime", "Usage events unavailable", error)
            return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
        }
    }

    /**
     * Union of resumed-activity intervals, clipped to today; concurrent activities never double count
     * in the total. Each interval is also credited once to every package with a resumed activity.
     */
    fun calculate(events: List<Event>, start: Long, now: Long): ScreenTimeResult {
        if (now < start || events.isEmpty()) return ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE)
        val active = mutableSetOf<String>()
        val apps = HashMap<String, Long>()
        var previous = start
        var total = 0L
        var knownBoundary = false
        var partial = false
        var reset = false
        fun credit(delta: Long) {
            total += delta
            active.mapTo(HashSet()) { it.substringBefore('/') }.forEach { apps[it] = (apps[it] ?: 0L) + delta }
        }
        events.sortedBy { it.at }.filter { it.at <= now }.forEach { event ->
            if (event.at >= start) {
                if (!knownBoundary) partial = true
                if (active.isNotEmpty()) {
                    if (event.type == STARTUP) partial = true // Missing shutdown: do not count powered-off time.
                    else credit(event.at - previous)
                }
                previous = event.at
            }
            when (event.type) {
                RESUMED -> {
                    active.add(event.activity)
                    reset = false
                }
                PAUSED -> {
                    if (!active.remove(event.activity) && event.at >= start && !reset) partial = true
                }
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN, STARTUP -> {
                    active.clear()
                    reset = true
                }
            }
            if (event.at <= start) knownBoundary = true
        }
        if (!knownBoundary) partial = true
        if (active.isNotEmpty()) credit(now - previous)
        val limit = now - start
        return ScreenTimeResult(if (partial) ScreenTimeStatus.PARTIAL else ScreenTimeStatus.AVAILABLE,
            total.coerceIn(0, limit), apps.mapValues { it.value.coerceIn(0, limit) })
    }
}
