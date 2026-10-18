package com.example.darktodo.monitor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val UNAVAILABLE = "Unavailable"

data class MonitorSnapshot(val capturedAt: Long, val values: Map<String, String>)

object MonitorFormats {
    fun bytes(value: Long?): String {
        if (value == null || value < 0) return UNAVAILABLE
        val units = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
        var amount = value.toDouble()
        var unit = 0
        while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
        return if (unit == 0) "$value B" else String.format(Locale.US, "%.2f %s", amount, units[unit])
    }

    fun used(total: Long, available: Long): Long? =
        if (total <= 0 || available !in 0..total) null else total - available

    fun percent(part: Long, total: Long): String =
        if (total <= 0 || part !in 0..total) UNAVAILABLE
        else String.format(Locale.US, "%.1f%%", part.toDouble() / total * 100)

    fun batteryPercent(level: Int, scale: Int): String = percent(level.toLong(), scale.toLong())

    fun timestamp(time: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(time))

    fun csv(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    fun report(snapshot: MonitorSnapshot, csv: Boolean): String {
        val entries = linkedMapOf("Captured at (UTC)" to timestamp(snapshot.capturedAt)).apply {
            putAll(snapshot.values)
        }
        return if (csv) "Metric,Value\r\n" + entries.entries.joinToString("\r\n", postfix = "\r\n") {
            "${csv(it.key)},${csv(it.value)}"
        } else "Clarity Info report\n" + entries.entries.joinToString("\n", postfix = "\n") {
            "${it.key}: ${it.value.replace("\r", "\\r").replace("\n", "\\n")}"
        }
    }
}
