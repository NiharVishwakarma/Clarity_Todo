package com.example.darktodo.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorFormatsTest {
    @Test fun dashboardTimeUsesRequestedLocalZoneWithoutChangingReportTimestamps() {
        val zone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        assertEquals("Jan 1, 1970, 5:30 AM", MonitorFormats.localTimestamp(0, zone, java.util.Locale.US))
        assertEquals("1970-01-01T00:00:00.000Z", MonitorFormats.timestamp(0))
        assertEquals("Unavailable", MonitorFormats.localIsoTimestamp("Unavailable"))
        assertEquals(MonitorFormats.localTimestamp(0), MonitorFormats.localIsoTimestamp("1970-01-01T00:00:00.000Z"))
    }

    @Test fun dashboardStatesPreserveFailureStalenessAndPreviousCaptureTime() {
        assertEquals("Loading local metrics", MonitorFormats.dashboardStatus(null, 0, true, false))
        assertTrue(MonitorFormats.dashboardStatus(null, 0, false, true).startsWith("Refresh failed"))
        assertEquals("No snapshot · Tap refresh", MonitorFormats.dashboardStatus(null, 0, false, false))
        val captured = MonitorFormats.localTimestamp(1_000)
        assertEquals("Updated $captured", MonitorFormats.dashboardStatus(1_000, 2_000, false, false))
        assertEquals("Refreshing · Saved $captured", MonitorFormats.dashboardStatus(1_000, 2_000, true, false))
        assertEquals("Refresh failed · Saved $captured", MonitorFormats.dashboardStatus(1_000, 2_000, false, true))
        assertEquals("Stale snapshot $captured",
            MonitorFormats.dashboardStatus(1_000, 1_000 + MonitorFormats.STALE_AFTER_MILLIS, false, false))
    }

    @Test fun deviceIdentityKeepsRawValuesAndHandlesMissingOrDuplicateIdentity() {
        val values = mapOf("Manufacturer" to "Example", "Model" to "Model 7", "Brand" to "Other", "Device" to "internal")
        assertEquals("Example Model 7", MonitorFormats.deviceIdentity(values))
        assertEquals("internal", values["Device"])
        assertEquals("Example", MonitorFormats.deviceIdentity(mapOf("Manufacturer" to "Example", "Model" to "example")))
        assertEquals("Model 7", MonitorFormats.deviceIdentity(mapOf("Manufacturer" to UNAVAILABLE, "Model" to "Model 7")))
        assertEquals("Device information unavailable", MonitorFormats.deviceIdentity(emptyMap()))
    }

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
        val report = MonitorFormats.report(snapshot, true, generatedAt = 1_000)
        assertTrue(report.startsWith("Metric,Value\r\n"))
        assertTrue(report.contains("\"Generated at (UTC)\",\"1970-01-01T00:00:01.000Z\""))
        assertTrue(report.contains("\"Captured at (UTC)\",\"1970-01-01T00:00:00.000Z\""))
        assertTrue(report.contains("\"Model\",\"a,\"\"b\"\"\r\nc\""))
        assertTrue(MonitorFormats.report(snapshot, false).contains("Model: a,\"b\"\\r\\nc"))
    }

    @Test fun textReportSeparatesGenerationAndSnapshotTimes() {
        val report = MonitorFormats.report(MonitorSnapshot(0, emptyMap()), false, generatedAt = 2_000)
        assertTrue(report.contains("Generated at (UTC): 1970-01-01T00:00:02.000Z"))
        assertTrue(report.contains("Captured at (UTC): 1970-01-01T00:00:00.000Z"))
    }

    @Test fun csvNeutralizesSpreadsheetFormulaCells() {
        assertEquals("\"'=1+1\"", MonitorFormats.csv("=1+1"))
        assertEquals("\"'  @SUM(1)\"", MonitorFormats.csv("  @SUM(1)"))
        assertEquals("\"normal\"", MonitorFormats.csv("normal"))
    }

    @Test fun batteryTemperatureRejectsMissingAndSentinelValues() {
        assertEquals("Unavailable", MonitorFormats.batteryTemperature(null))
        assertEquals("Unavailable", MonitorFormats.batteryTemperature(Int.MIN_VALUE))
        assertEquals("Unavailable", MonitorFormats.batteryTemperature(Int.MAX_VALUE))
        assertEquals("Unavailable", MonitorFormats.batteryTemperature(-1))
        assertEquals("Unavailable", MonitorFormats.batteryTemperature(-3000))
        assertEquals("0.0 °C", MonitorFormats.batteryTemperature(0))
        assertEquals("25.3 °C", MonitorFormats.batteryTemperature(253))
        assertEquals("-5.0 °C", MonitorFormats.batteryTemperature(-50))
    }

    @Test fun progressRejectsUnavailableAndOutOfRangeReadings() {
        assertEquals(0.25f, MonitorFormats.percentFraction("25.0%"))
        assertEquals(0f, MonitorFormats.percentFraction("0.0%"))
        assertEquals(1f, MonitorFormats.percentFraction("100.0%"))
        listOf(null, UNAVAILABLE, "25", "-1%", "101%", "NaN%", "Infinity%").forEach {
            assertEquals(null, MonitorFormats.percentFraction(it))
        }
    }

    @Test fun snapshotStatesCoverLoadingUnavailableFailureAndAge() {
        assertEquals("Loading device metrics…", MonitorFormats.snapshotStatus(null, 1_000, true, false))
        assertTrue(MonitorFormats.snapshotStatus(null, 1_000, false, true).startsWith("Error"))
        assertTrue(MonitorFormats.snapshotStatus(null, 1_000, false, false).startsWith("Unavailable"))
        assertTrue(MonitorFormats.snapshotStatus(1_000, 2_000, false, false).startsWith("Updated"))
        assertTrue(MonitorFormats.snapshotStatus(1_000, 2_000, true, false).startsWith("Refreshing"))
        assertTrue(MonitorFormats.snapshotStatus(1_000, 2_000, false, true).startsWith("Stale · Refresh failed"))
        assertTrue(MonitorFormats.snapshotStatus(1_000, 2_000, true, true).startsWith("Stale · Refreshing"))
        assertTrue(MonitorFormats.snapshotStatus(1_000, 1_000 + MonitorFormats.STALE_AFTER_MILLIS, false, false).startsWith("Stale"))
        assertTrue(MonitorFormats.snapshotStatus(2_000, 1_000, false, false).startsWith("Stale"))
    }

    @Test fun ownAppStorageFailuresDistinguishDeniedUnsupportedAndProviderFailure() {
        assertTrue(MonitorFormats.storageFailureStatus(SecurityException()).startsWith("Access denied"))
        assertTrue(MonitorFormats.storageFailureStatus(UnsupportedOperationException()).startsWith("Unsupported"))
        assertTrue(MonitorFormats.storageFailureStatus(java.io.IOException()).contains("service/provider"))
        assertTrue(MonitorFormats.storageFailureStatus(IllegalStateException()).contains("query failed"))
    }
}
