package com.example.darktodo.widget

import com.example.darktodo.data.WidgetFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetFontLayoutsTest {
    @Test fun eachSystemFamilyUsesDedicatedLayoutsAcrossBothWidgets() {
        val families = WidgetFontFamily.entries
        assertEquals(5, families.size)
        assertDistinct(families.map { WidgetFontLayouts.todo(it) })
        assertDistinct(families.map { WidgetFontLayouts.taskRow(it) })
        assertDistinct(families.map { WidgetFontLayouts.calendar(it) })
        assertDistinct(families.map { WidgetFontLayouts.calendarDay(it) })
        assertDistinct(families.map { WidgetFontLayouts.calendarWeekday(it) })
        assertEquals(5, WidgetFontLayouts.TASK_ROW_VIEW_TYPE_COUNT)
    }

    private fun assertDistinct(layouts: List<Int>) {
        layouts.forEachIndexed { index, layout ->
            layouts.drop(index + 1).forEach { assertNotEquals(layout, it) }
        }
    }
}
