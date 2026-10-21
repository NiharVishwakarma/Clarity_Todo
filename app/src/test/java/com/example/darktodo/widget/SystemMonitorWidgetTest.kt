package com.example.darktodo.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SystemMonitorWidgetTest {
    @Test fun detailsRespectAvailableHeightAndFontScale() {
        assertFalse(SystemMonitorWidgetState.showDetails(220, 1f))
        assertTrue(SystemMonitorWidgetState.showDetails(260, 1f))
        assertFalse(SystemMonitorWidgetState.showDetails(300, 1.5f))
        assertTrue(SystemMonitorWidgetState.showDetails(480, 2f))
        assertFalse(SystemMonitorWidgetState.essentialOnly(220, 1f))
        assertTrue(SystemMonitorWidgetState.essentialOnly(220, 2f))
        assertTrue(SystemMonitorWidgetState.essentialOnly(240, 1.5f))
        assertFalse(SystemMonitorWidgetState.essentialOnly(480, 2f))
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
        assertTrue(elements.all { it.tagName in setOf("FrameLayout", "LinearLayout", "TextView", "ImageView") })
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

    @Test fun resizingUsesCompactMetadataAndExplicitAccessToHiddenDetails() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val provider = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "res/xml/system_monitor_widget_info.xml")).documentElement
        val minimum = SystemMonitorWidgetState.COMPACT_MIN_HEIGHT_DP
        assertEquals("240dp", provider.getAttributeNS(android, "minHeight"))
        assertEquals("${minimum}dp", provider.getAttributeNS(android, "minResizeHeight"))
        assertEquals("3", provider.getAttributeNS(android, "targetCellHeight"))
        assertEquals("5", provider.getAttributeNS(android, "targetCellWidth"))
        assertEquals("320dp", provider.getAttributeNS(android, "minWidth"))
        assertEquals("220dp", provider.getAttributeNS(android, "minResizeWidth"))
        val source = File(main, "java/com/example/darktodo/widget/SystemMonitorWidget.kt").readText()
        assertTrue(source.contains("Android & patch details"))
        assertTrue(source.contains("views.setOnClickPendingIntent(R.id.monitor_widget_more,"))
    }

    @Test fun everyTextFieldHasATypedBitmapTargetAndKeepsMetricContainerClicks() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val layout = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "res/layout/system_monitor_widget.xml")).documentElement
        val nodes = layout.getElementsByTagName("*")
        val elements = (listOf(layout) + (0 until nodes.length).map { nodes.item(it) as Element })
            .filter { it.hasAttributeNS(android, "id") }
            .associateBy { it.getAttributeNS(android, "id").substringAfter('/') }
        val source = File(main, "java/com/example/darktodo/widget/SystemMonitorWidget.kt").readText()
        val textImages = Regex(
            """R\.id\.(monitor_widget_\w+) to R\.id\.(monitor_widget_\w+_image)"""
        ).findAll(source).toList()
        val textIds = elements.filterValues { it.tagName == "TextView" }.keys
        assertEquals(textIds, textImages.map { it.groupValues[1] }.toSet())
        textImages.forEach { action ->
            assertEquals("ImageView", elements[action.groupValues[2]]?.tagName)
        }
        assertTrue(source.contains("SystemMonitorWidgetRendering.text("))
        assertTrue(source.contains("views.setContentDescription(image, text)"))
        assertTrue(source.contains("style.titleWeight"))
        assertTrue(source.contains("style.valueWeight"))
        val renderer = File(main, "java/com/example/darktodo/widget/SystemMonitorWidgetRendering.kt").readText()
        assertTrue(renderer.contains("LocalWidgetFonts.typeface(context, style.fontFileId)"))
        assertTrue(renderer.contains("Typeface.create(style.fontFamily"))
        assertTrue(renderer.contains("StaticLayout.Builder.obtain("))
        listOf("refresh", "downloads", "clear_cache", "compact_clear", "more").forEach {
            assertTrue("Bitmap action must remain clickable: $it", source.contains("views.setOnClickPendingIntent(R.id.monitor_widget_${it}_image,"))
        }
        listOf("storage", "battery").forEach { name ->
            assertEquals("LinearLayout", elements["monitor_widget_$name"]?.tagName)
            assertTrue(source.contains("views.setOnClickPendingIntent(R.id.monitor_widget_$name,"))
        }
        assertFalse("Missing cache measurements must not be fabricated", source.contains("?: \"0 B\""))
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

    @Test fun clearCacheActionActivityIsDeclaredInManifest() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")
        val activity = (0 until activities.length).map { activities.item(it) as Element }.single {
            it.getAttributeNS(android, "name") == ".widget.SystemMonitorActionActivity"
        }
        assertEquals("false", activity.getAttributeNS(android, "exported"))
        assertEquals("true", activity.getAttributeNS(android, "excludeFromRecents"))
    }

    @Test fun systemMonitorAppearanceDefaultsMatchClarityDarkTheme() {
        val defaults = com.example.darktodo.data.SystemMonitorAppearance.DEFAULT
        assertEquals("#FFFFFF", defaults.titleColor)
        assertEquals("#B9BBC8", defaults.labelColor)
        assertEquals("#FFFFFF", defaults.valueColor)
        assertEquals("#FF8A96", defaults.accentColor)
        assertEquals(com.example.darktodo.data.CalendarWidgetPrefs().background, defaults.backgroundColor)
        assertEquals(100, defaults.backgroundOpacityPercent)
        assertEquals("#25252D", defaults.cardBackgroundColor)
        assertEquals(20, defaults.cornerRadiusDp)
        assertEquals(12, defaults.cardCornerRadiusDp)
    }

    @Test fun cardAndOuterOpacityPreserveColorAndRemainIndependent() {
        val defaults = com.example.darktodo.data.SystemMonitorAppearance.DEFAULT
        listOf("#000000" to 0x000000, "#FF0000" to 0xFF0000).forEach { (color, rgb) ->
            listOf(0 to 0, 59 to 150, 100 to 255).forEach { (percent, alpha) ->
                val style = defaults.copy(
                    backgroundColor = color, backgroundOpacityPercent = percent,
                    cardBackgroundColor = color, cardOpacityPercent = percent
                ).sanitized()
                assertEquals(alpha, style.cardBackgroundArgb().ushr(24))
                assertEquals(rgb, style.cardBackgroundArgb() and 0xFFFFFF)
                assertEquals(alpha, style.backgroundArgb().ushr(24))
                assertEquals(rgb, style.backgroundArgb() and 0xFFFFFF)
                assertEquals(style.cardBackgroundArgb(), style.copy(backgroundOpacityPercent = 25).cardBackgroundArgb())
                assertEquals(style.backgroundArgb(), style.copy(cardOpacityPercent = 25).backgroundArgb())
            }
        }
    }

    @Test fun savedCardPreferencesReachLiveImageLayersAndRefreshDoesNotWriteAppearance() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val source = File(main, "java/com/example/darktodo/widget/SystemMonitorWidget.kt").readText()
        assertTrue(source.contains("SystemMonitorAppearancePrefs.load(context)"))
        assertTrue(source.contains("SystemMonitorWidgetRendering.cardBackground("))
        listOf("ram", "storage", "battery", "cache").forEach {
            assertTrue(source.contains("R.id.monitor_widget_${it}_bg"))
        }
        assertFalse(source.contains("SystemMonitorAppearancePrefs.save("))
        assertFalse(source.contains("SystemMonitorAppearancePrefs.reset("))
        val prefs = File(main, "java/com/example/darktodo/data/SystemMonitorAppearance.kt").readText()
        listOf("background_color", "background_opacity", "card_background_color", "card_opacity", "wallpaper_enabled").forEach {
            assertTrue("Preference must be loaded and saved: $it", prefs.split("\"$it\"").size >= 3)
        }
    }

    @Test fun metricGridUsesSpareHeightAndEachCardHasAnIndependentImageLayer() {
        val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
        val android = "http://schemas.android.com/apk/res/android"
        val layout = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(main, "res/layout/system_monitor_widget.xml")).documentElement
        val nodes = layout.getElementsByTagName("*")
        val elements = (0 until nodes.length).map { nodes.item(it) as Element }
        fun element(id: String) = elements.single { it.getAttributeNS(android, "id") == "@+id/monitor_widget_$id" }
        assertEquals("0dp", element("metrics").getAttributeNS(android, "layout_height"))
        assertEquals("1", element("metrics").getAttributeNS(android, "layout_weight"))
        listOf("ram", "storage", "battery", "cache").forEach {
            assertEquals("ImageView", element("${it}_bg").tagName)
        }
        listOf("android", "security").forEach {
            assertEquals("match_parent", element(it).getAttributeNS(android, "layout_width"))
            assertEquals("2", element(it).getAttributeNS(android, "maxLines"))
        }
    }
}
