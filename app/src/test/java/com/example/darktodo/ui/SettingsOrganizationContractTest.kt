package com.example.darktodo.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOrganizationContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()

    @Test fun everySettingsSectionUsesTransientCollapsedState() {
        val shared = source("ui/ClarityUi.kt")
            .substringAfter("fun rememberSettingsSectionExpanded")
            .substringBefore("fun CollapsibleSettingsSection")
        assertTrue(shared.contains("remember(key) { mutableStateOf(false) }"))
        assertFalse(shared.contains("rememberSaveable"))
        listOf("MainActivity.kt", "ui/NoteSettings.kt", "ui/VaultAppearanceScreen.kt").forEach { path ->
            val screen = source(path)
            val sections = Regex("var \\w+Expanded by ([^\\r\\n]+)").findAll(screen)
                .filterNot { it.value.contains("addExpanded") }.toList()
            assertTrue(path, sections.isNotEmpty())
            sections.forEach { assertTrue(it.value, it.groupValues[1].startsWith("rememberSettingsSectionExpanded(")) }
        }
        assertTrue(source("ui/NoteSettings.kt").contains("rememberSettingsSectionExpanded(noteId)"))
    }

    @Test fun collapsedSectionsKeepContentComposedAndExposeExpansionState() {
        val section = source("ui/ClarityUi.kt")
            .substringAfter("fun CollapsibleSettingsSection")
            .substringBefore("@Composable fun ClarityGlyph")

        assertTrue(section.contains("animateContentSize"))
        assertTrue(section.contains("Modifier.height(0.dp)"))
        assertTrue(section.contains("heightIn(min = 56.dp)"))
        assertTrue(section.contains("content = content"))
        assertTrue(section.contains("role = Role.Button"))
        assertTrue(section.contains("focusManager.clearFocus()"))
        assertTrue(section.contains("stateDescription = if (expanded)"))
        assertTrue(section.contains("clearAndSetSemantics"))
        assertFalse(section.contains("AnimatedVisibility"))
    }

    @Test fun existingAppearanceControlsAndSavePathsRemainAvailable() {
        val mainActivity = source("MainActivity.kt")
        listOf(
            "Font size (task title)", "Completed-task fade", "Incomplete checkbox",
            "Completed checkbox", "Task-count panel", "Task title", "Widget background",
            "Task card background", "Task card background transparency", "To-Do widget wallpaper",
            "Widget padding", "Row spacing", "Save As Default Style", "Reset To-Do settings",
            "Font size (date)", "Font size (weekday)", "Font size (month heading)",
            "Selected-date circle", "Month navigation arrows", "Completed-task dot",
            "Mixed-status dot", "Incomplete-task dot", "Calendar widget wallpaper",
            "Show task-status dots", "Reset Calendar settings", "Font family"
        ).forEach { assertTrue("Missing MainActivity control: $it", mainActivity.contains(it)) }
        listOf(
            "WidgetSettings.saveColor", "WidgetSettings.saveFade", "WidgetSettings.saveTaskCardTransparency",
            "WidgetSettings.saveFontFamily", "WidgetSettings.saveTaskTitleSize",
            "CalendarWidgetSettings.saveColor", "CalendarWidgetSettings.saveInt",
            "CalendarWidgetSettings.saveDots"
        ).forEach { assertTrue("Missing existing save path: $it", mainActivity.contains(it)) }

        val notes = source("ui/NoteSettings.kt")
        listOf(
            "StickerColor.entries", "Background color HEX", "Border color HEX",
            "Title appearance", "Note appearance", "Text scaling",
            "Background opacity", "imageSection()", "Text shadow", "Glow",
            "Border thickness", "Widget size", "Corner radius", "Padding", "Margin", "Card elevation",
            "StickerStore.setStyle(context, noteId, style)"
        ).forEach { assertTrue("Missing note setting or save path: $it", notes.contains(it)) }
        val textControls = source("ui/NoteTextAppearance.kt")
        listOf("Automatic contrast", "Custom text color", "Font size", "Semibold", "Italic",
            "Line spacing", "Use recommended color", "LocalWidgetFonts.import", "WidgetFontFamily.entries"
        ).forEach { assertTrue("Missing independent text control: $it", textControls.contains(it)) }

        val vault = source("ui/VaultAppearanceScreen.kt")
        listOf(
            "Font family", "Font size (title)", "Font size (status)", "Font weight (title)",
            "Font weight (status)", "Title color", "Status color", "Background color",
            "Accent / outline color", "Background opacity", "Rounded corners",
            "VaultAppearancePrefs.save(context, style)", "onValueChangeFinished = onFinished"
        ).forEach { assertTrue("Missing Vault appearance control or save path: $it", vault.contains(it)) }
    }
}
