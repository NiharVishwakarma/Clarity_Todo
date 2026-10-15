package com.example.darktodo.widget

import com.example.darktodo.R

/** Selects XML variants because RemoteViews cannot safely apply TextView.setTextAppearance(). */
internal object WidgetFontLayouts {
    private data class Variants(
        val todo: IntArray,
        val taskRow: IntArray,
        val calendar: IntArray,
        val calendarDay: IntArray,
        val calendarWeekday: IntArray
    )

    private val byFamily = mapOf(
        "opensans" to Variants(
            intArrayOf(R.layout.todo_widget_opensans_regular, R.layout.todo_widget_opensans_italic),
            intArrayOf(R.layout.widget_task_row_opensans_regular, R.layout.widget_task_row_opensans_italic),
            intArrayOf(R.layout.calendar_widget_opensans_regular, R.layout.calendar_widget_opensans_italic),
            intArrayOf(R.layout.calendar_widget_day_opensans_regular, R.layout.calendar_widget_day_opensans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_opensans_regular, R.layout.calendar_widget_weekday_opensans_italic)
        ),
        "googlesans" to Variants(
            intArrayOf(R.layout.todo_widget_googlesans_regular, R.layout.todo_widget_googlesans_italic),
            intArrayOf(R.layout.widget_task_row_googlesans_regular, R.layout.widget_task_row_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_googlesans_regular, R.layout.calendar_widget_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_day_googlesans_regular, R.layout.calendar_widget_day_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_googlesans_regular, R.layout.calendar_widget_weekday_googlesans_italic)
        ),
        "montserrat" to Variants(
            intArrayOf(R.layout.todo_widget_montserrat_regular, R.layout.todo_widget_montserrat_italic),
            intArrayOf(R.layout.widget_task_row_montserrat_regular, R.layout.widget_task_row_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_montserrat_regular, R.layout.calendar_widget_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_day_montserrat_regular, R.layout.calendar_widget_day_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_weekday_montserrat_regular, R.layout.calendar_widget_weekday_montserrat_italic)
        ),
        "notosans" to Variants(
            intArrayOf(R.layout.todo_widget_notosans_regular, R.layout.todo_widget_notosans_italic),
            intArrayOf(R.layout.widget_task_row_notosans_regular, R.layout.widget_task_row_notosans_italic),
            intArrayOf(R.layout.calendar_widget_notosans_regular, R.layout.calendar_widget_notosans_italic),
            intArrayOf(R.layout.calendar_widget_day_notosans_regular, R.layout.calendar_widget_day_notosans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_notosans_regular, R.layout.calendar_widget_weekday_notosans_italic)
        ),
        "ubuntu" to Variants(
            intArrayOf(R.layout.todo_widget_ubuntu_regular, R.layout.todo_widget_ubuntu_italic),
            intArrayOf(R.layout.widget_task_row_ubuntu_regular, R.layout.widget_task_row_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_ubuntu_regular, R.layout.calendar_widget_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_day_ubuntu_regular, R.layout.calendar_widget_day_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_weekday_ubuntu_regular, R.layout.calendar_widget_weekday_ubuntu_italic)
        ),
        "nunito" to Variants(
            intArrayOf(R.layout.todo_widget_nunito_regular, R.layout.todo_widget_nunito_italic),
            intArrayOf(R.layout.widget_task_row_nunito_regular, R.layout.widget_task_row_nunito_italic),
            intArrayOf(R.layout.calendar_widget_nunito_regular, R.layout.calendar_widget_nunito_italic),
            intArrayOf(R.layout.calendar_widget_day_nunito_regular, R.layout.calendar_widget_day_nunito_italic),
            intArrayOf(R.layout.calendar_widget_weekday_nunito_regular, R.layout.calendar_widget_weekday_nunito_italic)
        )
    )

    private fun select(family: String, italic: Boolean, layout: (Variants) -> IntArray): Int =
        layout(byFamily[family] ?: byFamily.getValue("googlesans"))[if (italic) 1 else 0]

    fun todo(family: String, italic: Boolean) = select(family, italic) { it.todo }
    fun taskRow(family: String, italic: Boolean) = select(family, italic) { it.taskRow }
    /** The collection contains one row layout for each of six fonts and two styles. */
    const val TASK_ROW_VIEW_TYPE_COUNT = 12
    fun calendar(family: String, italic: Boolean) = select(family, italic) { it.calendar }
    fun calendarDay(family: String, italic: Boolean) = select(family, italic) { it.calendarDay }
    fun calendarWeekday(family: String, italic: Boolean) = select(family, italic) { it.calendarWeekday }
}
