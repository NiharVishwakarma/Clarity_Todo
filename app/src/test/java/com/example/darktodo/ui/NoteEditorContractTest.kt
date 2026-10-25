package com.example.darktodo.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorContractTest {
    private val main = File("src/main/java/com/example/darktodo").let {
        if (it.isDirectory) it else File("app/src/main/java/com/example/darktodo")
    }
    private fun source(path: String) = File(main, path).readText()

    @Test fun editorKeepsStyleDraftLocalUntilSaveAndRetainsTextSelection() {
        val editor = source("ui/NotesScreen.kt").substringAfter("@Composable private fun NoteEditor")
        assertTrue(editor.contains("stateSaver = TextFieldValue.Saver"))
        assertTrue(editor.contains("styleJson = it.toJson().toString()"))
        assertTrue(editor.contains("onSave(title.text, content.text, style)"))
        assertTrue(editor.contains("onClick = onDismiss"))
        assertFalse(editor.contains("StickerStore.setStyle"))
        assertFalse(editor.contains("StickerStore.saveColorBook"))
        assertTrue(editor.contains(".imePadding()"))
        assertTrue(editor.contains("FlowRow("))
        assertTrue(editor.contains("Title appearance"))
        assertTrue(editor.contains("Note appearance"))
    }

    @Test fun appAndWidgetResolveBothTextElementsAndKeepWidgetRefreshBoundary() {
        val preview = source("ui/NoteSettings.kt")
        val widget = source("widget/StickerWidget.kt")
        listOf(preview, widget).forEach {
            assertTrue(it.contains("style.titleText()"))
            assertTrue(it.contains("style.bodyText()"))
        }
        assertTrue(widget.contains("titleStyle.alignment"))
        assertTrue(widget.contains("bodyStyle.alignment"))
        assertTrue(widget.contains("titleStyle.fontFileId"))
        assertTrue(widget.contains("bodyStyle.fontFileId"))
        assertTrue(widget.contains("NoteAppearance.effectiveStyle"))
        val store = source("data/StickerStore.kt")
        assertTrue(store.contains("StickerOps.setStyle(edited, id, style.sanitized())"))
        assertTrue(store.contains("StickerWidget.updateAll(context)"))
    }
}
