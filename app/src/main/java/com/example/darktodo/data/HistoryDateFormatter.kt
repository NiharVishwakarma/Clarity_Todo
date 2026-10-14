package com.example.darktodo.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Presentation-only formatter for ISO history date keys. */
object HistoryDateFormatter {
    private val input = DateTimeFormatter.ISO_LOCAL_DATE
    private val output = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    fun format(isoDate: String): String = try {
        LocalDate.parse(isoDate, input).format(output)
    } catch (_: DateTimeParseException) {
        isoDate
    }
}
