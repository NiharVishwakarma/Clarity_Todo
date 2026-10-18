package com.example.darktodo.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorFormatsTest {
    @Test fun byteUnitsAndUnknowns() {
        assertEquals("Unavailable", MonitorFormats.bytes(null))
        assertEquals("Unavailable", MonitorFormats.bytes(-1))
        assertEquals("0 B", MonitorFormats.bytes(0))
        assertEquals("1023 B", MonitorFormats.bytes(1023))
        assertEquals("1.00 KiB", MonitorFormats.bytes(1024))
        assertEquals("1.50 GiB", MonitorFormats.bytes(1610612736))
        assertEquals("8.00 EiB", MonitorFormats.bytes(Long.MAX_VALUE))
    }

    @Test fun memoryAndBatteryMathRejectInvalidSamples() {
        assertEquals(75L, MonitorFormats.used(100, 25))
        assertEquals(null, MonitorFormats.used(0, 0))
        assertEquals(null, MonitorFormats.used(100, -1))
        assertEquals(null, MonitorFormats.used(100, 101))
        assertEquals("25.0%", MonitorFormats.percent(25, 100))
        assertEquals("100.0%", MonitorFormats.percent(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals("Unavailable", MonitorFormats.batteryPercent(-1, 100))
        assertEquals("Unavailable", MonitorFormats.batteryPercent(50, 0))
        assertEquals("Unavailable", MonitorFormats.batteryPercent(101, 100))
        assertEquals("50.0%", MonitorFormats.batteryPercent(50, 100))
    }

    @Test fun reportEscapesCsvAndUsesCoherentTimestamp() {
        val snapshot = MonitorSnapshot(0L, linkedMapOf("Model" to "a,\"b\"\r\nc"))
        val report = MonitorFormats.report(snapshot, true)
        assertTrue(report.startsWith("Metric,Value\r\n"))
        assertTrue(report.contains("\"Captured at (UTC)\",\"1970-01-01T00:00:00.000Z\""))
        assertTrue(report.contains("\"Model\",\"a,\"\"b\"\"\r\nc\""))
        assertTrue(MonitorFormats.report(snapshot, false).contains("Model: a,\"b\"\\r\\nc"))
    }

    @Test fun csvNeutralizesSpreadsheetFormulaCells() {
        assertEquals("\"'=1+1\"", MonitorFormats.csv("=1+1"))
        assertEquals("\"'  @SUM(1)\"", MonitorFormats.csv("  @SUM(1)"))
        assertEquals("\"normal\"", MonitorFormats.csv("normal"))
    }
}
