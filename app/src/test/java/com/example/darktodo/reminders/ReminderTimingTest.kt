package com.example.darktodo.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimingTest {
    @Test fun dueWhenTriggerPassed() = assertTrue(ReminderTiming.isDue(1_000L, 5_000L))
    @Test fun dueWithinEarlyTolerance() = assertTrue(ReminderTiming.isDue(60_000L, 1_000L))
    @Test fun notDueWhenFarInFuture() = assertFalse(ReminderTiming.isDue(200_000L, 1_000L))
    @Test fun invalidTriggerIsNeverDue() = assertFalse(ReminderTiming.isDue(0L, 1_000L))

    @Test fun exactDeliveryIsNotLate() = assertFalse(ReminderTiming.isLate(10_000L, 10_500L))
    @Test fun inexactDozeDeliveryIsLate() = assertTrue(ReminderTiming.isLate(10_000L, 10_000L + 9 * 60_000L))
    @Test fun latenessIsSigned() = assertEquals(-500L, ReminderTiming.latenessMs(10_500L, 10_000L))

    @Test fun describeIncludesZoneOffset() {
        val zone = ZoneId.of("Asia/Kolkata")
        val ms = LocalDateTime.of(2026, 1, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(ReminderTiming.describe(ms, zone).contains("+05:30"))
        assertEquals("none", ReminderTiming.describe(0L, zone))
    }

    @Test fun absoluteInstantIsStableAcrossDeviceZoneChange() {
        val ny = ZoneId.of("America/New_York")
        val ms = LocalDateTime.of(2026, 3, 8, 9, 0).atZone(ny).toInstant().toEpochMilli()
        // 09:00 EDT (after the spring-forward) is 13:00 UTC regardless of the device zone.
        assertEquals(13, java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).hour)
    }
}
