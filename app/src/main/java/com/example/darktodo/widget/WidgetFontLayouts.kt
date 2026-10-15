package com.example.darktodo.widget

import com.example.darktodo.R
import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.data.WidgetFontStyle

/** RemoteViews receives complete XML layouts because widget hosts cannot accept Typeface objects. */
internal object WidgetFontLayouts {
    private data class Styles(val regular: Int, val bold: Int, val italic: Int) {
        operator fun get(style: WidgetFontStyle): Int = when (style) {
            WidgetFontStyle.REGULAR -> regular
            WidgetFontStyle.BOLD -> bold
            WidgetFontStyle.ITALIC -> italic
        }
    }

    private data class FamilyLayouts(
        val todo: Styles,
        val taskRow: Styles,
        val calendar: Styles,
        val calendarDay: Styles,
        val calendarWeekday: Styles
    )

    private val byFamily = mapOf(
        WidgetFontFamily.OPEN_SANS to FamilyLayouts(
            Styles(R.layout.todo_widget_opensans_regular, R.layout.todo_widget_opensans_bold, R.layout.todo_widget_opensans_italic),
            Styles(R.layout.widget_task_row_opensans_regular, R.layout.widget_task_row_opensans_bold, R.layout.widget_task_row_opensans_italic),
            Styles(R.layout.calendar_widget_opensans_regular, R.layout.calendar_widget_opensans_bold, R.layout.calendar_widget_opensans_italic),
            Styles(R.layout.calendar_widget_day_opensans_regular, R.layout.calendar_widget_day_opensans_bold, R.layout.calendar_widget_day_opensans_italic),
            Styles(R.layout.calendar_widget_weekday_opensans_regular, R.layout.calendar_widget_weekday_opensans_bold, R.layout.calendar_widget_weekday_opensans_italic)
        ),
        WidgetFontFamily.GOOGLE_SANS to FamilyLayouts(
            Styles(R.layout.todo_widget_googlesans_regular, R.layout.todo_widget_googlesans_bold, R.layout.todo_widget_googlesans_italic),
            Styles(R.layout.widget_task_row_googlesans_regular, R.layout.widget_task_row_googlesans_bold, R.layout.widget_task_row_googlesans_italic),
            Styles(R.layout.calendar_widget_googlesans_regular, R.layout.calendar_widget_googlesans_bold, R.layout.calendar_widget_googlesans_italic),
            Styles(R.layout.calendar_widget_day_googlesans_regular, R.layout.calendar_widget_day_googlesans_bold, R.layout.calendar_widget_day_googlesans_italic),
            Styles(R.layout.calendar_widget_weekday_googlesans_regular, R.layout.calendar_widget_weekday_googlesans_bold, R.layout.calendar_widget_weekday_googlesans_italic)
        ),
        WidgetFontFamily.MONTSERRAT to FamilyLayouts(
            Styles(R.layout.todo_widget_montserrat_regular, R.layout.todo_widget_montserrat_bold, R.layout.todo_widget_montserrat_italic),
            Styles(R.layout.widget_task_row_montserrat_regular, R.layout.widget_task_row_montserrat_bold, R.layout.widget_task_row_montserrat_italic),
            Styles(R.layout.calendar_widget_montserrat_regular, R.layout.calendar_widget_montserrat_bold, R.layout.calendar_widget_montserrat_italic),
            Styles(R.layout.calendar_widget_day_montserrat_regular, R.layout.calendar_widget_day_montserrat_bold, R.layout.calendar_widget_day_montserrat_italic),
            Styles(R.layout.calendar_widget_weekday_montserrat_regular, R.layout.calendar_widget_weekday_montserrat_bold, R.layout.calendar_widget_weekday_montserrat_italic)
        ),
        WidgetFontFamily.NOTO_SANS to FamilyLayouts(
            Styles(R.layout.todo_widget_notosans_regular, R.layout.todo_widget_notosans_bold, R.layout.todo_widget_notosans_italic),
            Styles(R.layout.widget_task_row_notosans_regular, R.layout.widget_task_row_notosans_bold, R.layout.widget_task_row_notosans_italic),
            Styles(R.layout.calendar_widget_notosans_regular, R.layout.calendar_widget_notosans_bold, R.layout.calendar_widget_notosans_italic),
            Styles(R.layout.calendar_widget_day_notosans_regular, R.layout.calendar_widget_day_notosans_bold, R.layout.calendar_widget_day_notosans_italic),
            Styles(R.layout.calendar_widget_weekday_notosans_regular, R.layout.calendar_widget_weekday_notosans_bold, R.layout.calendar_widget_weekday_notosans_italic)
        ),
        WidgetFontFamily.UBUNTU to FamilyLayouts(
            Styles(R.layout.todo_widget_ubuntu_regular, R.layout.todo_widget_ubuntu_bold, R.layout.todo_widget_ubuntu_italic),
            Styles(R.layout.widget_task_row_ubuntu_regular, R.layout.widget_task_row_ubuntu_bold, R.layout.widget_task_row_ubuntu_italic),
            Styles(R.layout.calendar_widget_ubuntu_regular, R.layout.calendar_widget_ubuntu_bold, R.layout.calendar_widget_ubuntu_italic),
            Styles(R.layout.calendar_widget_day_ubuntu_regular, R.layout.calendar_widget_day_ubuntu_bold, R.layout.calendar_widget_day_ubuntu_italic),
            Styles(R.layout.calendar_widget_weekday_ubuntu_regular, R.layout.calendar_widget_weekday_ubuntu_bold, R.layout.calendar_widget_weekday_ubuntu_italic)
        ),
        WidgetFontFamily.NUNITO to FamilyLayouts(
            Styles(R.layout.todo_widget_nunito_regular, R.layout.todo_widget_nunito_bold, R.layout.todo_widget_nunito_italic),
            Styles(R.layout.widget_task_row_nunito_regular, R.layout.widget_task_row_nunito_bold, R.layout.widget_task_row_nunito_italic),
            Styles(R.layout.calendar_widget_nunito_regular, R.layout.calendar_widget_nunito_bold, R.layout.calendar_widget_nunito_italic),
            Styles(R.layout.calendar_widget_day_nunito_regular, R.layout.calendar_widget_day_nunito_bold, R.layout.calendar_widget_day_nunito_italic),
            Styles(R.layout.calendar_widget_weekday_nunito_regular, R.layout.calendar_widget_weekday_nunito_bold, R.layout.calendar_widget_weekday_nunito_italic)
        )
    )

    private fun layouts(family: WidgetFontFamily): FamilyLayouts =
        checkNotNull(byFamily[family]) { "No widget layouts registered for ${family.key}" }

    fun todo(family: WidgetFontFamily, style: WidgetFontStyle) = layouts(family).todo[style]
    fun taskRow(family: WidgetFontFamily, style: WidgetFontStyle) = layouts(family).taskRow[style]
    const val TASK_ROW_VIEW_TYPE_COUNT = 18
    fun calendar(family: WidgetFontFamily, style: WidgetFontStyle) = layouts(family).calendar[style]
    fun calendarDay(family: WidgetFontFamily, style: WidgetFontStyle) = layouts(family).calendarDay[style]
    fun calendarWeekday(family: WidgetFontFamily, style: WidgetFontStyle) = layouts(family).calendarWeekday[style]
}
