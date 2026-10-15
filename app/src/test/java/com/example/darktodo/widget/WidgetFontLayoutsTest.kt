package com.example.darktodo.widget

import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.data.WidgetFontStyle
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetFontLayoutsTest {
    @Test fun everyFamilyAndStyleSelectsDedicatedWidgetLayouts() {
        WidgetFontFamily.entries.forEach { family ->
            assertThreeDistinct(
                WidgetFontLayouts.todo(family, WidgetFontStyle.REGULAR),
                WidgetFontLayouts.todo(family, WidgetFontStyle.BOLD),
                WidgetFontLayouts.todo(family, WidgetFontStyle.ITALIC)
            )
            assertThreeDistinct(
                WidgetFontLayouts.taskRow(family, WidgetFontStyle.REGULAR),
                WidgetFontLayouts.taskRow(family, WidgetFontStyle.BOLD),
                WidgetFontLayouts.taskRow(family, WidgetFontStyle.ITALIC)
            )
            assertThreeDistinct(
                WidgetFontLayouts.calendar(family, WidgetFontStyle.REGULAR),
                WidgetFontLayouts.calendar(family, WidgetFontStyle.BOLD),
                WidgetFontLayouts.calendar(family, WidgetFontStyle.ITALIC)
            )
            assertThreeDistinct(
                WidgetFontLayouts.calendarDay(family, WidgetFontStyle.REGULAR),
                WidgetFontLayouts.calendarDay(family, WidgetFontStyle.BOLD),
                WidgetFontLayouts.calendarDay(family, WidgetFontStyle.ITALIC)
            )
            assertThreeDistinct(
                WidgetFontLayouts.calendarWeekday(family, WidgetFontStyle.REGULAR),
                WidgetFontLayouts.calendarWeekday(family, WidgetFontStyle.BOLD),
                WidgetFontLayouts.calendarWeekday(family, WidgetFontStyle.ITALIC)
            )
        }
    }

    private fun assertThreeDistinct(regular: Int, bold: Int, italic: Int) {
        assertNotEquals(regular, bold)
        assertNotEquals(regular, italic)
        assertNotEquals(bold, italic)
    }
}
