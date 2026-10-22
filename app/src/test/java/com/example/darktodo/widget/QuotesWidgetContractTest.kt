package com.example.darktodo.widget

import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuotesWidgetSettings
import com.example.darktodo.navigation.BackStep
import com.example.darktodo.navigation.ModuleNavigation
import com.example.darktodo.navigation.ModuleRoute
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class QuotesWidgetContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private fun source(path: String) = File(main, "java/com/example/darktodo/$path.kt").readText()

    @Test fun widgetUsesIndependentPreferencesAndSharedRenderer() {
        val widget = source("widget/StickerWidget")
        assertTrue(widget.contains("QuotesWidgetPrefs.load(context)"))
        assertTrue(widget.contains("QuotesWidgetRendering.render("))
        assertTrue(widget.contains("WidgetQuoteSelection.pool("))
        assertFalse(widget.contains("QuoteLibrary.selectedTopics"))
        assertFalse(widget.contains("QuoteLibrary.recentIds"))
        assertFalse(widget.contains("QuoteLibrary.recordViewed"))
        assertTrue(widget.contains("quote_seen_"))
        assertTrue(source("ui/QuotesWidgetSettingsScreen").contains("QuotesWidgetRendering.render("))
        assertFalse(source("ui/NotesScreen").contains("QuotesSection("))
    }

    @Test fun quoteSettingsBelongToNotesTileAndBackReturnsToNotes() {
        assertEquals(ModuleNavigation.STICKERS, ModuleNavigation.moduleFor("QuotesWidgetSettings", "Tasks"))
        assertEquals(BackStep.GoTo(ModuleRoute("Stickers")), ModuleNavigation.backStep("QuotesWidgetSettings", "Tasks", 0))
        val activity = source("MainActivity")
        assertFalse(activity.contains("SettingsNavRow(ClarityIcons.Quotes, \"Quotes Widget\""))
        assertTrue(activity.contains("onOpenQuotesSettings = { page = \"QuotesWidgetSettings\" }"))
        assertTrue(source("ui/NotesScreen").contains("QuotesTile(onOpenQuotesSettings)"))
        assertTrue(activity.contains("page == \"QuotesWidgetSettings\""))
    }

    @Test fun quoteImageUsesSharedEditorAndRealWidgetRenderer() {
        val screen = source("ui/QuotesWidgetSettingsScreen")
        assertTrue(screen.contains("WidgetBackgroundEditor(WidgetBackgrounds.QUOTES)"))
        assertTrue(screen.contains("imageRevision++"))
        assertTrue(source("widget/QuotesWidgetRendering").contains("WidgetBackgrounds.renderPx(context, WidgetBackgrounds.QUOTES"))
        assertTrue(source("WidgetBackgroundEditor").contains("Quotes widget background image"))
    }

    @Test fun schedulerUsesExistingWorkManagerWithRebootRecoveryAndMinimumInterval() {
        val rotation = source("widget/QuotesWidgetRotation")
        assertTrue(rotation.contains("PeriodicWorkRequestBuilder<QuotesWidgetWorker>"))
        assertTrue(rotation.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(rotation.contains("cancelUniqueWork"))
        assertTrue(rotation.contains("rotation.scheduled"))
        assertTrue(source("ClarityApplication").contains("QuotesWidgetRotation.reconcile(this)"))
        val rules = main.resolve("..").resolve("..").resolve("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keep class com.example.darktodo.widget.QuotesWidgetWorker"))
        assertEquals(15, QuotesWidgetSettings(intervalMinutes = 0).sanitized().intervalMinutes)
    }

    @Test fun saveRefreshesEveryExistingQuoteInstanceWithoutRecreatingNotes() {
        val widget = source("widget/StickerWidget")
        val apply = widget.substringAfter("fun applyQuoteSettings").substringBefore("private fun selectQuote")
        assertTrue(apply.contains("QuotesWidgetPrefs.save"))
        assertTrue(apply.contains("quoteIds(context).forEach"))
        assertTrue(apply.contains("update(context, manager, it)"))
        assertTrue(apply.contains("QuotesWidgetRotation.reconcile"))
        assertFalse(apply.contains("setNote"))
        val ui = source("ui/QuotesWidgetSettingsScreen")
        assertTrue(ui.contains("Refresh widgets"))
        assertTrue(ui.contains("Apply Changes"))
        assertTrue(ui.contains("Reset to defaults"))
    }

    @Test fun rendererBoundsTextAndSeparatesAuthorFromQuote() {
        val renderer = source("widget/QuotesWidgetRendering")
        assertTrue(renderer.contains("StaticLayout.Builder.obtain"))
        assertTrue(renderer.contains("TextUtils.TruncateAt.END"))
        assertTrue(renderer.contains("footer.sumOf"))
        assertTrue(renderer.contains("bodyRoom"))
        assertTrue(renderer.contains("canvas.clipRect(content)"))
        assertTrue(renderer.contains("QuotesWidgetPalette.resolve"))
        QuoteAlignment.entries.forEach { assertTrue(renderer.contains("QuoteAlignment.${it.name}")) }
    }

    @Test fun standaloneQuotesProviderNeedsNoNotesConfigurationAndPreservesLegacyInstances() {
        val ns = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val provider = factory.newDocumentBuilder().parse(File(main, "res/xml/quotes_widget_info.xml")).documentElement
        assertEquals("", provider.getAttributeNS(ns, "configure"))
        assertEquals("horizontal|vertical", provider.getAttributeNS(ns, "resizeMode"))
        assertEquals("180dp", provider.getAttributeNS(ns, "minResizeWidth"))
        assertEquals("56dp", provider.getAttributeNS(ns, "minResizeHeight"))
        val manifest = File(main, "AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".widget.QuotesWidgetReceiver"))
        assertTrue(manifest.contains("@xml/quotes_widget_info"))
        val widget = source("widget/StickerWidget")
        assertTrue(widget.contains("ComponentName(context, QuotesWidgetReceiver::class.java)"))
        assertTrue(widget.contains("ComponentName(context, StickerWidgetReceiver::class.java)"))
        assertTrue(widget.contains("getSharedPreferences(\"quotes_widget_state\""))
        assertTrue(widget.contains("Could not migrate widget quote state."))
        val receiver = source("widget/QuotesWidgetReceiver")
        assertTrue(receiver.contains("StickerWidget.update(context, manager, it)"))
        assertTrue(receiver.contains("StickerWidget.forget(context, ids)"))
    }

    @Test fun twoLayerStylingAndTypographyAreBoundToPreviewAndLiveRendering() {
        val renderer = source("widget/QuotesWidgetRendering")
        listOf("s.outerBackgroundStyle", "s.outerOpacityPercent", "s.outerPaddingDp", "s.outerCornerRadiusDp",
            "s.outerGradientDirection", "s.gradientDirection", "s.cardOpacityPercent",
            "s.borderColor", "s.borderWidthDp", "s.lineSpacing",
            "s.outerBorderColor", "s.outerBorderWidthDp").forEach { assertTrue(it, renderer.contains(it)) }
        val ui = source("ui/QuotesWidgetSettingsScreen")
        listOf("Widget Background", "Quote Card", "Automatic rotation", "Apply Changes",
            "settings.copy(outerBackground", "settings.copy(cardOpacityPercent", "settings.copy(borderWidthDp",
            "settings.copy(lineSpacing", "enabled = settings.rotation.scheduled").forEach { assertTrue(it, ui.contains(it)) }
        assertFalse(ui.contains("choose Random quotes"))
    }
}
