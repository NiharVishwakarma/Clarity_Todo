package com.example.darktodo.widget

import com.example.darktodo.R
import com.example.darktodo.data.WidgetFontFamily

/** Each entry is a RemoteViews-compatible layout with one Android system font family in XML. */
internal object WidgetFontLayouts {
    private data class FamilyLayouts(
        val todo: Int,
        val taskRow: Int,
        val calendar: Int,
        val calendarDay: Int,
        val calendarWeekday: Int,
        val todoImported: Int = R.layout.todo_widget_imported_font,
        val taskRowImported: Int = R.layout.widget_task_row_imported_font,
        val calendarImported: Int = R.layout.calendar_widget_imported_font,
        val calendarDayImported: Int = R.layout.calendar_widget_day_imported_font,
        val calendarWeekdayImported: Int = R.layout.calendar_widget_weekday_imported_font
    )

    private val byFamily = mapOf(
        WidgetFontFamily.SANS_SERIF to FamilyLayouts(R.layout.todo_widget_sans_serif, R.layout.widget_task_row_sans_serif, R.layout.calendar_widget_sans_serif, R.layout.calendar_widget_day_sans_serif, R.layout.calendar_widget_weekday_sans_serif),
        WidgetFontFamily.SANS_SERIF_CONDENSED to FamilyLayouts(R.layout.todo_widget_sans_serif_condensed, R.layout.widget_task_row_sans_serif_condensed, R.layout.calendar_widget_sans_serif_condensed, R.layout.calendar_widget_day_sans_serif_condensed, R.layout.calendar_widget_weekday_sans_serif_condensed),
        WidgetFontFamily.SANS_SERIF_LIGHT to FamilyLayouts(R.layout.todo_widget_sans_serif_light, R.layout.widget_task_row_sans_serif_light, R.layout.calendar_widget_sans_serif_light, R.layout.calendar_widget_day_sans_serif_light, R.layout.calendar_widget_weekday_sans_serif_light),
        WidgetFontFamily.SERIF to FamilyLayouts(R.layout.todo_widget_serif, R.layout.widget_task_row_serif, R.layout.calendar_widget_serif, R.layout.calendar_widget_day_serif, R.layout.calendar_widget_weekday_serif),
        WidgetFontFamily.MONOSPACE to FamilyLayouts(R.layout.todo_widget_monospace, R.layout.widget_task_row_monospace, R.layout.calendar_widget_monospace, R.layout.calendar_widget_day_monospace, R.layout.calendar_widget_weekday_monospace)
    )

    private fun layouts(family: WidgetFontFamily): FamilyLayouts =
        checkNotNull(byFamily[family]) { "No widget layouts registered for ${family.key}" }

    fun todo(family: WidgetFontFamily, imported: Boolean = false) = if (imported) layouts(family).todoImported else layouts(family).todo
    fun taskRow(family: WidgetFontFamily, imported: Boolean = false) = if (imported) layouts(family).taskRowImported else layouts(family).taskRow
    const val TASK_ROW_VIEW_TYPE_COUNT = 6
    fun calendar(family: WidgetFontFamily, imported: Boolean = false) = if (imported) layouts(family).calendarImported else layouts(family).calendar
    fun calendarDay(family: WidgetFontFamily, imported: Boolean = false) = if (imported) layouts(family).calendarDayImported else layouts(family).calendarDay
    fun calendarWeekday(family: WidgetFontFamily, imported: Boolean = false) = if (imported) layouts(family).calendarWeekdayImported else layouts(family).calendarWeekday
}
