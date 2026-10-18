package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCardTransparencyTest {
    @Test fun transparencyEndpointsAndMidpointMapToBackgroundAlpha() {
        assertEquals(255, WidgetPrefs(taskCardTransparencyPercent = 0).taskCardAlpha)
        assertEquals(128, WidgetPrefs(taskCardTransparencyPercent = 50).taskCardAlpha)
        assertEquals(0, WidgetPrefs(taskCardTransparencyPercent = 100).taskCardAlpha)
    }

    @Test fun transparencyIsClampedAndDefaultsToOpaque() {
        assertEquals(255, WidgetPrefs().taskCardAlpha)
        assertEquals(255, WidgetPrefs(taskCardTransparencyPercent = -10).taskCardAlpha)
        assertEquals(0, WidgetPrefs(taskCardTransparencyPercent = 110).taskCardAlpha)
    }

    @Test fun colorTransparencyAndCompletedTaskFadeAreIndependent() {
        val original = WidgetPrefs(taskCard = "#123456", taskCardTransparencyPercent = 50, fadePercent = 45)
        val recolored = original.copy(taskCard = "#ABCDEF")
        assertEquals(50, recolored.taskCardTransparencyPercent)
        assertEquals(128, recolored.taskCardAlpha)
        assertEquals(45, recolored.fadePercent)

        val transparent = recolored.copy(taskCardTransparencyPercent = 100)
        assertEquals("#ABCDEF", transparent.taskCard)
        assertEquals(45, transparent.fadePercent)
        assertEquals(original.taskTitle, transparent.taskTitle)
        assertEquals(original.countPanel, transparent.countPanel)
    }
}
