package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorDepthTest {
    @Test fun everyStopPreservesAlphaIncludingTransparentColors() {
        for (alpha in 0..255) {
            for (rgb in listOf(0, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x123456)) {
                val color = (alpha shl 24) or rgb
                val stops = ColorDepth.colors(color)
                assertEquals(ColorDepth.positions.size, stops.size)
                assertEquals(color, stops[2])
                stops.forEach { assertEquals(alpha, it ushr 24) }
            }
        }
    }

    @Test fun whiteBlackAndVividColorsHaveDirectionalDepthWithoutWrapping() {
        for (rgb in listOf(0, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x123456)) {
            val stops = ColorDepth.colors(0xFF000000.toInt() or rgb)
            for (shift in listOf(16, 8, 0)) {
                val base = (rgb ushr shift) and 255
                assertTrue(((stops.first() ushr shift) and 255) >= base)
                assertTrue(((stops.last() ushr shift) and 255) <= base)
            }
            assertTrue(stops.first() != stops.last())
        }
        assertEquals(0f, ColorDepth.positions.first())
        assertEquals(1f, ColorDepth.positions.last())
        assertTrue(ColorDepth.positions.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun depthDefaultsOffAndDoesNotChangeIndependentAppearanceProperties() {
        assertFalse(WidgetPrefs().backgroundDepth)
        assertFalse(CalendarWidgetPrefs().backgroundDepth)
        val tasks = WidgetPrefs(taskCard = "#123456", taskCardTransparencyPercent = 50, fadePercent = 45)
        val raised = tasks.copy(backgroundDepth = true)
        assertEquals(tasks, raised.copy(backgroundDepth = false))
        assertEquals(128, raised.taskCardAlpha)
        assertEquals("#123456", raised.taskCard)
        assertEquals(45, raised.fadePercent)
        val calendar = CalendarWidgetPrefs(background = "#ABCDEF", cornerRadius = 22)
        assertEquals(calendar, calendar.copy(backgroundDepth = true).copy(backgroundDepth = false))
        assertFalse(calendar.backgroundDepth)
    }
}
