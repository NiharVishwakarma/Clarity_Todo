package com.example.darktodo.widget

import com.example.darktodo.R

/** Selects XML variants because RemoteViews cannot apply Typeface changes after inflation. */
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
            intArrayOf(R.layout.todo_widget_opensans_regular, R.layout.todo_widget_opensans_bold, R.layout.todo_widget_opensans_italic),
            intArrayOf(R.layout.widget_task_row_opensans_regular, R.layout.widget_task_row_opensans_bold, R.layout.widget_task_row_opensans_italic),
            intArrayOf(R.layout.calendar_widget_opensans_regular, R.layout.calendar_widget_opensans_bold, R.layout.calendar_widget_opensans_italic),
            intArrayOf(R.layout.calendar_widget_day_opensans_regular, R.layout.calendar_widget_day_opensans_bold, R.layout.calendar_widget_day_opensans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_opensans_regular, R.layout.calendar_widget_weekday_opensans_bold, R.layout.calendar_widget_weekday_opensans_italic)
        ),
        "googlesans" to Variants(
            intArrayOf(R.layout.todo_widget_googlesans_regular, R.layout.todo_widget_googlesans_bold, R.layout.todo_widget_googlesans_italic),
            intArrayOf(R.layout.widget_task_row_googlesans_regular, R.layout.widget_task_row_googlesans_bold, R.layout.widget_task_row_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_googlesans_regular, R.layout.calendar_widget_googlesans_bold, R.layout.calendar_widget_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_day_googlesans_regular, R.layout.calendar_widget_day_googlesans_bold, R.layout.calendar_widget_day_googlesans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_googlesans_regular, R.layout.calendar_widget_weekday_googlesans_bold, R.layout.calendar_widget_weekday_googlesans_italic)
        ),
        "montserrat" to Variants(
            intArrayOf(R.layout.todo_widget_montserrat_regular, R.layout.todo_widget_montserrat_bold, R.layout.todo_widget_montserrat_italic),
            intArrayOf(R.layout.widget_task_row_montserrat_regular, R.layout.widget_task_row_montserrat_bold, R.layout.widget_task_row_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_montserrat_regular, R.layout.calendar_widget_montserrat_bold, R.layout.calendar_widget_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_day_montserrat_regular, R.layout.calendar_widget_day_montserrat_bold, R.layout.calendar_widget_day_montserrat_italic),
            intArrayOf(R.layout.calendar_widget_weekday_montserrat_regular, R.layout.calendar_widget_weekday_montserrat_bold, R.layout.calendar_widget_weekday_montserrat_italic)
        ),
        "notosans" to Variants(
            intArrayOf(R.layout.todo_widget_notosans_regular, R.layout.todo_widget_notosans_bold, R.layout.todo_widget_notosans_italic),
            intArrayOf(R.layout.widget_task_row_notosans_regular, R.layout.widget_task_row_notosans_bold, R.layout.widget_task_row_notosans_italic),
            intArrayOf(R.layout.calendar_widget_notosans_regular, R.layout.calendar_widget_notosans_bold, R.layout.calendar_widget_notosans_italic),
            intArrayOf(R.layout.calendar_widget_day_notosans_regular, R.layout.calendar_widget_day_notosans_bold, R.layout.calendar_widget_day_notosans_italic),
            intArrayOf(R.layout.calendar_widget_weekday_notosans_regular, R.layout.calendar_widget_weekday_notosans_bold, R.layout.calendar_widget_weekday_notosans_italic)
        ),
        "ubuntu" to Variants(
            intArrayOf(R.layout.todo_widget_ubuntu_regular, R.layout.todo_widget_ubuntu_bold, R.layout.todo_widget_ubuntu_italic),
            intArrayOf(R.layout.widget_task_row_ubuntu_regular, R.layout.widget_task_row_ubuntu_bold, R.layout.widget_task_row_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_ubuntu_regular, R.layout.calendar_widget_ubuntu_bold, R.layout.calendar_widget_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_day_ubuntu_regular, R.layout.calendar_widget_day_ubuntu_bold, R.layout.calendar_widget_day_ubuntu_italic),
            intArrayOf(R.layout.calendar_widget_weekday_ubuntu_regular, R.layout.calendar_widget_weekday_ubuntu_bold, R.layout.calendar_widget_weekday_ubuntu_italic)
        ),
        "nunito" to Variants(
            intArrayOf(R.layout.todo_widget_nunito_regular, R.layout.todo_widget_nunito_bold, R.layout.todo_widget_nunito_italic),
            intArrayOf(R.layout.widget_task_row_nunito_regular, R.layout.widget_task_row_nunito_bold, R.layout.widget_task_row_nunito_italic),
            intArrayOf(R.layout.calendar_widget_nunito_regular, R.layout.calendar_widget_nunito_bold, R.layout.calendar_widget_nunito_italic),
            intArrayOf(R.layout.calendar_widget_day_nunito_regular, R.layout.calendar_widget_day_nunito_bold, R.layout.calendar_widget_day_nunito_italic),
            intArrayOf(R.layout.calendar_widget_weekday_nunito_regular, R.layout.calendar_widget_weekday_nunito_bold, R.layout.calendar_widget_weekday_nunito_italic)
        )
    )

    private fun select(family: String, style: String, layout: (Variants) -> IntArray): Int {
        val styleIndex = when (style) { "bold" -> 1; "italic" -> 2; else -> 0 }
        return layout(byFamily[family] ?: byFamily.getValue("googlesans"))[styleIndex]
    }

    fun todo(family: String, style: String) = select(family, style) { it.todo }
    fun taskRow(family: String, style: String) = select(family, style) { it.taskRow }
    const val TASK_ROW_VIEW_TYPE_COUNT = 18
    fun calendar(family: String, style: String) = select(family, style) { it.calendar }
    fun calendarDay(family: String, style: String) = select(family, style) { it.calendarDay }
    fun calendarWeekday(family: String, style: String) = select(family, style) { it.calendarWeekday }
}
