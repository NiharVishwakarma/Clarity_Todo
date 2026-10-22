package com.example.darktodo.widget

import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuotesWidgetSettings
import com.example.darktodo.navigation.BackStep
import com.example.darktodo.navigation.ModuleNavigation
import com.example.darktodo.navigation.ModuleRoute
import java.io.File
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

    @Test fun quoteSettingsBelongToSettingsTabAndBackReturnsToSettings() {
        assertEquals(ModuleNavigation.SETTINGS, ModuleNavigation.moduleFor("QuotesWidgetSettings", "Tasks"))
        assertEquals(BackStep.GoTo(ModuleRoute("Settings")), ModuleNavigation.backStep("QuotesWidgetSettings", "Tasks", 0))
        val activity = source("MainActivity")
        assertTrue(activity.contains("SettingsNavRow(ClarityIcons.Personalization, \"Quotes Widget\""))
        assertTrue(activity.contains("page == \"QuotesWidgetSettings\""))
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
        assertTrue(ui.contains("Apply / Save"))
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
}
