package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWidgetDefaultsTest {
    @Test fun firstUseAndResetDefaultsMatchClarityCalendarDesign() {
        val defaults = CalendarWidgetPrefs()
        assertEquals("#000000", defaults.background)
        assertEquals("#FFFFFF", defaults.date)
        assertEquals("#8A8A8A", defaults.weekday)
        assertEquals("#FFFFFF", defaults.heading)
        assertEquals("#F20D24", defaults.selectedBackground)
        assertEquals("#FFFFFF", defaults.selectedText)
        assertEquals("#35D07F", defaults.completeDot)
        assertEquals("#FF9F1C", defaults.mixedDot)
        assertEquals("#F20D24", defaults.openDot)
        assertEquals(8, defaults.cornerRadius)
        assertEquals(17, defaults.dateSize)
        assertEquals(11, defaults.weekdaySize)
        assertEquals(17, defaults.headingSize)
        assertEquals(2, defaults.rowSpacing)
        assertTrue(defaults.showDots)
    }
}
