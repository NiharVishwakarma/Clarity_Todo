package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class CalendarDatesTest {
    @Test fun monthGridStartsMondayAndUsesVariableRowCount() {
        assertEquals(35, CalendarDates.monthCells(YearMonth.of(2026, 10), Locale.US).size)
        assertEquals(42, CalendarDates.monthCells(YearMonth.of(2026, 8), Locale.US).size)
        assertEquals(35, CalendarDates.monthCells(YearMonth.of(2015, 2), Locale.US).size)
        assertEquals(28, CalendarDates.monthCells(YearMonth.of(2021, 2), Locale.US).size)
        assertEquals(35, CalendarDates.monthCells(YearMonth.of(2024, 2), Locale.US).size)
        val october = CalendarDates.monthCells(YearMonth.of(2026, 10), Locale.US)
        assertEquals(3, october.indexOfFirst { it != null })
        assertEquals(java.time.DayOfWeek.MONDAY, CalendarDates.weekdays(Locale.US).first())
        assertEquals(LocalDate.of(2026, 10, 1), october.first { it != null })
    }

    @Test fun statusDotRulesReflectCompletionOnly() {
        val day = LocalDate.of(2026, 10, 15)
        val tasks = listOf(
            Task("a", "done", true, taskDate = day.toString()),
            Task("b", "open", false, taskDate = day.toString()),
            Task("c", "other day", false, taskDate = day.plusDays(1).toString())
        )
        assertEquals(CalendarTaskStatus.MIXED, CalendarDates.status(tasks, day))
        assertEquals(CalendarTaskStatus.OPEN, CalendarDates.status(tasks, day.plusDays(1)))
        assertEquals(CalendarTaskStatus.NONE, CalendarDates.status(tasks, day.plusDays(2)))
        assertEquals(CalendarTaskStatus.COMPLETE, CalendarDates.status(tasks.take(1), day))
    }

    @Test fun statusDotMatchesCompletionRatios() {
        val day = LocalDate.of(2026, 10, 17)
        fun tasks(done: Int, open: Int) = List(done) { Task("d$it", "done", true, taskDate = day.toString()) } +
            List(open) { Task("o$it", "open", false, taskDate = day.toString()) }
        assertEquals(CalendarTaskStatus.COMPLETE, CalendarDates.status(tasks(1, 0), day))
        assertEquals(CalendarTaskStatus.COMPLETE, CalendarDates.status(tasks(5, 0), day))
        assertEquals(CalendarTaskStatus.OPEN, CalendarDates.status(tasks(0, 1), day))
        assertEquals(CalendarTaskStatus.OPEN, CalendarDates.status(tasks(0, 5), day))
        assertEquals(CalendarTaskStatus.MIXED, CalendarDates.status(tasks(1, 1), day))
        assertEquals(CalendarTaskStatus.MIXED, CalendarDates.status(tasks(3, 2), day))
        assertEquals(CalendarTaskStatus.MIXED, CalendarDates.status(tasks(7, 3), day))
        assertEquals(CalendarTaskStatus.NONE, CalendarDates.status(emptyList(), day))
    }

    @Test fun newTaskDefaultsToCurrentLocalDate() {
        assertEquals(LocalDate.now().toString(), Task("new", "new task", false).taskDate)
    }
}
