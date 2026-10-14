package com.example.darktodo.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

enum class CalendarTaskStatus { NONE, COMPLETE, MIXED, OPEN }

object CalendarDates {
    fun monthCells(month: YearMonth, locale: Locale): List<LocalDate?> {
        val first = month.atDay(1)
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val offset = (first.dayOfWeek.value - firstDay.value + 7) % 7
        val rows = ((offset + month.lengthOfMonth() + 6) / 7).coerceIn(4, 6)
        return List(rows * 7) { index ->
            val day = index - offset + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }

    fun weekdays(locale: Locale): List<DayOfWeek> {
        val first = WeekFields.of(locale).firstDayOfWeek
        return List(7) { first.plus(it.toLong()) }
    }

    fun status(tasks: List<Task>, date: LocalDate): CalendarTaskStatus {
        val assigned = tasks.filter { it.taskDate == date.toString() }
        if (assigned.isEmpty()) return CalendarTaskStatus.NONE
        val complete = assigned.count { it.completed }
        return when {
            complete == assigned.size -> CalendarTaskStatus.COMPLETE
            complete > 0 -> CalendarTaskStatus.MIXED
            else -> CalendarTaskStatus.OPEN
        }
    }
}
