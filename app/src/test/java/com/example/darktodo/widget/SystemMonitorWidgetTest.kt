package com.example.darktodo.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SystemMonitorWidgetTest {
    @Test fun resizingUsesEachInstancesMinimumHeight() {
        assertFalse(SystemMonitorWidgetState.expanded(0))
        assertFalse(SystemMonitorWidgetState.expanded(260))
        assertFalse(SystemMonitorWidgetState.expanded(339))
        assertTrue(SystemMonitorWidgetState.expanded(340))
        assertTrue(SystemMonitorWidgetState.expanded(400))
    }

    @Test fun missingAndPartialMetricsNeverBecomeFabricatedNumbers() {
        assertEquals("Unavailable", SystemMonitorWidgetState.value(emptyMap(), "Battery"))
        assertEquals("Unavailable", SystemMonitorWidgetState.value(mapOf("Battery" to " "), "Battery"))
        assertEquals("42%", SystemMonitorWidgetState.value(mapOf("Battery" to "42%"), "Battery"))
        assertEquals("Unavailable (permission denied)", SystemMonitorWidgetState.value(
            mapOf("Battery" to "Unavailable (permission denied)"), "Battery"
        ))
    }

    @Test fun timestampAlwaysRepresentsLastSuccessNotRefreshAttempt() {
        assertEquals("Updated · yesterday", SystemMonitorWidgetState.status(100L, 0L, 200L, "yesterday"))
        assertEquals("Refresh failed · Stale · yesterday", SystemMonitorWidgetState.status(100L, 150L, 200L, "yesterday"))
        assertEquals("Updated · today", SystemMonitorWidgetState.status(200L, 150L, 300L, "today"))
        assertEquals("Not updated · Tap Refresh", SystemMonitorWidgetState.status(null, 0L, 200L, ""))
        assertEquals("Refresh failed · No successful update", SystemMonitorWidgetState.status(null, 100L, 200L, ""))
    }

    @Test fun oldAndFutureSnapshotsAreExplicitlyStale() {
        assertFalse(SystemMonitorWidgetState.stale(100L, 0L, 101L))
        assertTrue(SystemMonitorWidgetState.stale(100L, 0L, 100L + SystemMonitorWidgetState.STALE_AFTER_MS))
        assertTrue(SystemMonitorWidgetState.stale(100L, 100L, 101L))
        assertTrue(SystemMonitorWidgetState.stale(100L, 0L, 99L))
    }

    @Test fun providerUsesOnlyWorkManagerPollingAndResizableRemoteViews() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val android = "http://schemas.android.com/apk/res/android"
        val provider = factory.newDocumentBuilder()
            .parse(File(main, "res/xml/system_monitor_widget_info.xml")).documentElement
        assertEquals("0", provider.getAttributeNS(android, "updatePeriodMillis"))
        assertEquals("horizontal|vertical", provider.getAttributeNS(android, "resizeMode"))
        assertEquals("@layout/system_monitor_widget", provider.getAttributeNS(android, "initialLayout"))
        assertEquals("@drawable/system_monitor_widget_preview", provider.getAttributeNS(android, "previewImage"))
        val preview = factory.newDocumentBuilder()
            .parse(File(main, "res/drawable/system_monitor_widget_preview.xml")).documentElement
        assertEquals("vector", preview.tagName)
        assertEquals("260dp", preview.getAttributeNS(android, "height"))
        val layout = factory.newDocumentBuilder()
            .parse(File(main, "res/layout/system_monitor_widget.xml")).documentElement
        val nodes = layout.getElementsByTagName("*")
        val elements = listOf(layout) + (0 until nodes.length).map { nodes.item(it) as Element }
        assertTrue(elements.all { it.tagName in setOf("LinearLayout", "TextView") })
        val ids = elements.map { it.getAttributeNS(android, "id") }.toSet()
        listOf("refresh", "info", "battery", "storage", "downloads", "details", "updated").forEach {
            assertTrue("Missing $it control", "@+id/monitor_widget_$it" in ids)
        }
        listOf("refresh", "downloads").forEach { name ->
            val control = elements.single { it.getAttributeNS(android, "id") == "@+id/monitor_widget_$name" }
            assertEquals("1", control.getAttributeNS(android, "maxLines"))
            assertEquals("end", control.getAttributeNS(android, "ellipsize"))
        }
    }

    @Test fun compactMinimumAndExpandedThresholdReserveRoomForLargerFonts() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val provider = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "res/xml/system_monitor_widget_info.xml")).documentElement
        val minimum = SystemMonitorWidgetState.COMPACT_MIN_HEIGHT_DP
        assertEquals("${minimum}dp", provider.getAttributeNS(android, "minHeight"))
        assertEquals("${minimum}dp", provider.getAttributeNS(android, "minResizeHeight"))
        assertEquals("4", provider.getAttributeNS(android, "targetCellHeight"))
        // Conservative doubled-font row budgets, including font padding and a two-line timestamp.
        val compactBudget = 40 + 38 + 34 + 34 + 34 + 56 + 20
        assertTrue("Compact content must fit its declared minimum", minimum >= compactBudget)
        assertTrue("Expanded rows need additional font-scaling headroom",
            SystemMonitorWidgetState.EXPANDED_HEIGHT_DP >= minimum + 2 * 34)
        assertFalse(SystemMonitorWidgetState.expanded(minimum))
    }

    @Test fun providerIsRegisteredWithTheExpectedManifestMetadata() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "AndroidManifest.xml"))
        val receivers = manifest.getElementsByTagName("receiver")
        val receiver = (0 until receivers.length).map { receivers.item(it) as Element }.single {
            it.getAttributeNS(android, "name") == ".widget.SystemMonitorWidgetReceiver"
        }
        assertEquals("true", receiver.getAttributeNS(android, "exported"))
        assertTrue(receiver.getAttributeNS(android, "label").isNotBlank())
        val metadata = receiver.getElementsByTagName("meta-data")
        val provider = (0 until metadata.length).map { metadata.item(it) as Element }.single {
            it.getAttributeNS(android, "name") == "android.appwidget.provider"
        }
        assertEquals("@xml/system_monitor_widget_info", provider.getAttributeNS(android, "resource"))
        val filters = receiver.getElementsByTagName("intent-filter")
        assertTrue((0 until filters.length).any { index ->
            val actions = (filters.item(index) as Element).getElementsByTagName("action")
            (0 until actions.length).any {
                (actions.item(it) as Element).getAttributeNS(android, "name") ==
                    "android.appwidget.action.APPWIDGET_UPDATE"
            }
        })
    }
}
