package com.example.darktodo.monitor

import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeTest {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun event(at: Long, type: Int, activity: String = "app/Main") = ScreenTime.Event(at, type, activity)

    @Test fun midnightUsesLocalDateAndHandlesDaylightSavingDays() {
        val india = ZoneId.of("Asia/Kolkata")
        val now = time("2026-10-25T04:00:00Z")
        assertEquals(time("2026-10-24T18:30:00Z"), ScreenTime.midnight(now, india))
        assertEquals(time("2026-10-25T18:30:00Z"), ScreenTime.nextMidnight(now, india))
        val ny = ZoneId.of("America/New_York")
        val spring = time("2026-03-08T16:00:00Z")
        val fall = time("2026-11-01T16:00:00Z")
        assertEquals(23 * 3_600_000L, ScreenTime.nextMidnight(spring, ny) - ScreenTime.midnight(spring, ny))
        assertEquals(25 * 3_600_000L, ScreenTime.nextMidnight(fall, ny) - ScreenTime.midnight(fall, ny))
        assertTrue(ScreenTime.midnight(now, india) != ScreenTime.midnight(now, ZoneId.of("America/Los_Angeles")))
    }

    @Test fun formatsCompactDurationsWithoutRoundingUp() {
        assertEquals("0m", ScreenTime.duration(0))
        assertEquals("0m", ScreenTime.duration(59_999))
        assertEquals("1m", ScreenTime.duration(60_000))
        assertEquals("2h 35m", ScreenTime.duration(155 * 60_000L))
        assertEquals("25h 0m", ScreenTime.duration(25 * 3_600_000L))
        assertEquals("0m", ScreenTime.duration(-1))
    }

    @Test fun clipsCrossMidnightSessionsAndCountsOngoingUsageOnlyUntilNow() {
        val result = ScreenTime.calculate(listOf(event(50, ScreenTime.RESUMED), event(150, ScreenTime.PAUSED)), 100, 200)
        assertEquals(ScreenTimeStatus.AVAILABLE, result.status)
        assertEquals(50L, result.durationMillis)
        assertEquals(100L, ScreenTime.calculate(listOf(event(50, ScreenTime.RESUMED)), 100, 200).durationMillis)
        assertEquals(0L, ScreenTime.calculate(listOf(event(50, ScreenTime.SCREEN_OFF)), 100, 100).durationMillis)
    }

    @Test fun unionsConcurrentActivitiesAndStopsAtScreenOffOrLock() {
        val events = listOf(event(50, ScreenTime.SCREEN_OFF),
            event(110, ScreenTime.RESUMED, "a"), event(120, ScreenTime.RESUMED, "b"),
            event(130, ScreenTime.PAUSED, "a"), event(150, ScreenTime.SCREEN_OFF),
            event(170, ScreenTime.RESUMED), event(180, ScreenTime.KEYGUARD_SHOWN))
        assertEquals(50L, ScreenTime.calculate(events, 100, 200).durationMillis)
        val pauseAfterLock = ScreenTime.calculate(listOf(
            event(50, ScreenTime.RESUMED), event(150, ScreenTime.SCREEN_OFF), event(151, ScreenTime.PAUSED)), 100, 200)
        assertEquals(ScreenTimeStatus.AVAILABLE, pauseAfterLock.status)
        assertEquals(50L, pauseAfterLock.durationMillis)
    }

    @Test fun emptyMissingBoundariesAndRestartGapsAreNotInventedTotals() {
        assertEquals(ScreenTimeStatus.UNAVAILABLE, ScreenTime.calculate(emptyList(), 100, 200).status)
        assertEquals(ScreenTimeStatus.PARTIAL,
            ScreenTime.calculate(listOf(event(110, ScreenTime.RESUMED)), 100, 200).status)
        val reboot = ScreenTime.calculate(listOf(event(50, ScreenTime.RESUMED),
            event(150, ScreenTime.STARTUP), event(170, ScreenTime.RESUMED)), 100, 200)
        assertEquals(ScreenTimeStatus.PARTIAL, reboot.status)
        assertEquals(30L, reboot.durationMillis)
        val shutdown = ScreenTime.calculate(listOf(event(50, ScreenTime.RESUMED),
            event(120, ScreenTime.SHUTDOWN), event(150, ScreenTime.STARTUP)), 100, 200)
        assertEquals(20L, shutdown.durationMillis)
    }

    @Test fun permissionAndUnavailableStatesNeverDisplayZero() {
        val permission = ScreenTimeResult(ScreenTimeStatus.PERMISSION_REQUIRED)
        assertEquals("Allow access", permission.value)
        assertEquals("Usage Access required", permission.supporting)
        assertEquals("Unavailable", ScreenTimeResult(ScreenTimeStatus.UNAVAILABLE).value)
        assertEquals("Partial: 1m", ScreenTimeResult(ScreenTimeStatus.PARTIAL, 60_000).value)
        assertEquals("Since midnight", ScreenTimeResult(ScreenTimeStatus.AVAILABLE, 60_000).supporting)
    }

    @Test fun refreshAndPrivacyContractsKeepUsageOutOfPersistentMonitorData() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()
        val widget = source("widget/SystemMonitorWidget.kt")
        assertTrue(widget.contains("val screenTime = ScreenTime.read(context)"))
        assertTrue(widget.contains("ACTION_NEW_DAY"))
        assertTrue(widget.contains("Intent.ACTION_TIMEZONE_CHANGED"))
        assertTrue(widget.contains("Intent.ACTION_BOOT_COMPLETED"))
        assertTrue(widget.contains("ScreenTime.nextMidnight"))
        assertTrue(widget.contains("setAndAllowWhileIdle"))
        assertTrue(widget.contains("R.id.monitor_widget_screen_time, screenTimeAction"))
        val activity = source("MainActivity.kt").substringAfter("override fun onResume()").substringBefore("override fun onStop()")
        assertTrue(activity.contains("SystemMonitorWidget.renderAll(this)"))
        listOf("monitor/MonitorRepository.kt", "monitor/MonitorReports.kt", "backup/BackupManager.kt").forEach {
            assertFalse(it, source(it).contains("ScreenTime"))
        }
        val usage = source("monitor/ScreenTime.kt")
        assertFalse(usage.contains("getSharedPreferences"))
        assertFalse(usage.contains("writeText"))
        assertTrue(usage.contains("queryEvents(lookback, now)"))
        val actions = source("widget/SystemMonitorActionActivity.kt")
        assertFalse(actions.contains("SCREEN_TIME"))
        assertFalse(actions.contains("ACTION_USAGE_ACCESS_SETTINGS"))
        val details = source("monitor/ScreenTimeDetailsActivity.kt")
        assertTrue(details.contains("resolveActivityInfo"))
        assertTrue(details.contains("Settings.ACTION_USAGE_ACCESS_SETTINGS"))
        assertTrue(details.contains("SystemMonitorWidget.renderAll(applicationContext)"))
        assertFalse(details.contains("getSharedPreferences"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.PACKAGE_USAGE_STATS"))
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
        listOf("BOOT_COMPLETED", "TIME_SET", "TIMEZONE_CHANGED", "DATE_CHANGED", "USER_UNLOCKED").forEach {
            assertTrue(manifest.contains("android.intent.action.$it"))
        }
    }

    @Test fun widgetTileOpensClarityDetailsNotPermissionSettings() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val widget = File(main, "java/com/example/darktodo/widget/SystemMonitorWidget.kt").readText()
        val action = widget.substringAfter("val screenTimeAction = PendingIntent.getActivity(").substringBefore("views.setOnClickPendingIntent")
        assertTrue(action.contains("id * 10 + 5"))
        assertTrue(action.contains("Intent(context, ScreenTimeDetailsActivity::class.java)"))
        assertTrue(action.contains("ScreenTimeDetailsActivity.EXTRA_FROM_WIDGET, true"))
        assertTrue(action.contains("clarity://system-monitor/screen-time/\$id"))
        assertTrue(action.contains("FLAG_ACTIVITY_CLEAR_TASK"))
        assertFalse(action.contains("SystemMonitorActionActivity"))
        assertFalse(action.contains("USAGE_ACCESS"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        val declaration = manifest.substringAfter("android:name=\".monitor.ScreenTimeDetailsActivity\"").substringBefore("/>")
        assertTrue(declaration.contains("android:exported=\"false\""))
    }

    @Test fun usageAccessOpensOnlyWhenMissingOnFreshWidgetLaunch() {
        assertTrue(ScreenTime.shouldOpenUsageAccess(fromWidget = true, restored = false, hasAccess = false))
        assertFalse(ScreenTime.shouldOpenUsageAccess(fromWidget = true, restored = false, hasAccess = true))
        assertFalse(ScreenTime.shouldOpenUsageAccess(fromWidget = true, restored = true, hasAccess = false))
        assertFalse(ScreenTime.shouldOpenUsageAccess(fromWidget = false, restored = false, hasAccess = false))
    }

    @Test fun attributesPerAppTimeSortedAndClippedToMidnight() {
        val events = listOf(
            event(50, ScreenTime.RESUMED, "maps/Main"), event(130, ScreenTime.PAUSED, "maps/Main"),
            event(130, ScreenTime.RESUMED, "chat/Main"), event(140, ScreenTime.RESUMED, "chat/Thread"),
            event(150, ScreenTime.PAUSED, "chat/Main"), event(160, ScreenTime.PAUSED, "chat/Thread"),
            event(170, ScreenTime.RESUMED, "idle/Main"), event(170, ScreenTime.PAUSED, "idle/Main")
        )
        val result = ScreenTime.calculate(events, 100, 200)
        assertEquals(60L, result.durationMillis)
        assertEquals(listOf(AppUsage("maps", 30), AppUsage("chat", 30)).sortedBy { it.packageName },
            result.ranked.sortedBy { it.packageName })
        assertFalse(result.apps.getOrDefault("idle", 0L) > 0)
        assertTrue(result.ranked.none { it.packageName == "idle" })
        val ranked = ScreenTimeResult(ScreenTimeStatus.AVAILABLE, 0,
            mapOf("b" to 5L, "a" to 90L, "c" to 0L, "d" to 5L, "" to 40L)).ranked
        assertEquals(listOf("a", "b", "d"), ranked.map { it.packageName })
    }

    @Test fun splitScreenCreditsEachAppButNotTheTotalTwice() {
        val result = ScreenTime.calculate(listOf(event(50, ScreenTime.SCREEN_OFF),
            event(110, ScreenTime.RESUMED, "a/X"), event(110, ScreenTime.RESUMED, "b/Y"),
            event(140, ScreenTime.PAUSED, "a/X"), event(140, ScreenTime.PAUSED, "b/Y")), 100, 200)
        assertEquals(30L, result.durationMillis)
        assertEquals(30L, result.apps["a"])
        assertEquals(30L, result.apps["b"])
    }

    @Test fun appDurationsNeverShowZeroForRealUsage() {
        assertEquals("<1m", ScreenTime.appDuration(1))
        assertEquals("<1m", ScreenTime.appDuration(59_999))
        assertEquals("1m", ScreenTime.appDuration(60_000))
        assertEquals("1h 24m", ScreenTime.appDuration(84 * 60_000L))
        assertEquals("18m", ScreenTime.appDuration(18 * 60_000L))
    }
}
