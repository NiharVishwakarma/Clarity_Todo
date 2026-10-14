package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownEditPolicyTest {
    @Test fun overdueCountdownOpensWithOneMinuteMinimumAndSavingRenewsIt() {
        assertEquals(1L, CountdownEditPolicy.displayedMinutes(-1L))
        assertEquals(1L, CountdownEditPolicy.displayedMinutes(0L))
        assertTrue(CountdownEditPolicy.shouldConfigure(reminderWasEdited = false, isExpiredCountdown = true))
    }

    @Test fun untouchedActiveCountdownDoesNotRestartWhenOnlyTaskDetailsAreSaved() {
        assertFalse(CountdownEditPolicy.shouldConfigure(reminderWasEdited = false, isExpiredCountdown = false))
    }

    @Test fun explicitReminderEditAlwaysReconfiguresCountdown() {
        assertTrue(CountdownEditPolicy.shouldConfigure(reminderWasEdited = true, isExpiredCountdown = false))
    }

    @Test fun positiveRemainingTimeRoundsUpToWholeMinutes() {
        assertEquals(1L, CountdownEditPolicy.displayedMinutes(1L))
        assertEquals(2L, CountdownEditPolicy.displayedMinutes(60_001L))
        assertEquals(6L, CountdownEditPolicy.displayedMinutes(5 * 60_000L + 1L))
    }
}
