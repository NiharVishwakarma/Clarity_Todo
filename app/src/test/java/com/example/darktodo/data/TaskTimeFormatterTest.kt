package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskTimeFormatterTest {
    @Test fun formatsCountdownBoundariesWithCeilingMinutes() {
        assertEquals("8h 6m left", TaskTimeFormatter.countdownLabel((8 * 60L + 6L) * 60_000L))
        assertEquals("1h left", TaskTimeFormatter.countdownLabel(60L * 60_000L))
        assertEquals("1h 59m left", TaskTimeFormatter.countdownLabel(119L * 60_000L))
        assertEquals("45m left", TaskTimeFormatter.countdownLabel(45L * 60_000L))
        assertEquals("8m left", TaskTimeFormatter.countdownLabel(8L * 60_000L))
        assertEquals("1m left", TaskTimeFormatter.countdownLabel(1L))
    }

    @Test fun formatsDeadlineAndOverdue() {
        assertEquals("Due now", TaskTimeFormatter.countdownLabel(0L))
        assertEquals("OVERDUE", TaskTimeFormatter.countdownLabel(-1L))
    }

    @Test fun widgetScheduleTargetsTheNextDisplayedMinuteBoundary() {
        val now = 1_000_000L
        assertEquals(now + 1L, TaskTimeFormatter.nextMinuteTransition(now, 8L * 60_000L + 1L))
        assertEquals(now + 60_000L, TaskTimeFormatter.nextMinuteTransition(now, 8L * 60_000L))
        assertEquals(now + 1L, TaskTimeFormatter.nextMinuteTransition(now, 0L))
        assertNull(TaskTimeFormatter.nextMinuteTransition(now, -1L))
    }

    @Test fun tasksWithDifferentDeadlinesFormatIndependentlyFromSameClock() {
        val now = 50_000L
        val deadlines = listOf(now + 8L * 60_000L, now + 45L * 60_000L)
        assertEquals(listOf("8m left", "45m left"), deadlines.map { TaskTimeFormatter.countdownLabel(it - now) })
    }
}
