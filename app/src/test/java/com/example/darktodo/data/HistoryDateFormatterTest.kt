package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDateFormatterTest {
    @Test fun formatsIsoDatesWithEnglishMonthAndTwoDigitDay() {
        assertEquals("05-Jan-2026", HistoryDateFormatter.format("2026-01-05"))
        assertEquals("17-Mar-2026", HistoryDateFormatter.format("2026-03-17"))
        assertEquals("14-Oct-2026", HistoryDateFormatter.format("2026-10-14"))
        assertEquals("31-Dec-2026", HistoryDateFormatter.format("2026-12-31"))
    }

    @Test fun invalidDateKeyIsReturnedUnchanged() {
        assertEquals("2026-02-30", HistoryDateFormatter.format("2026-02-30"))
    }
}
