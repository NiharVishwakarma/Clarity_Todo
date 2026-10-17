package com.example.darktodo.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.example.darktodo.data.StickerOps
import com.example.darktodo.data.StickerStore

/** Shown when a Note widget is placed (and when it is reconfigured): lets the user pick which note it displays. */
class StickerWidgetConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val notes = StickerOps.display(StickerStore.load(this))
        fun finishWith(noteId: String?) {
            if (noteId != null) StickerWidget.setNote(this, widgetId, noteId)
            StickerWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Choose a note")
        if (notes.isEmpty()) {
            dialog.setMessage("You have no notes yet. Create one in Clarity and it will appear on this widget.")
                .setPositiveButton("OK") { _, _ -> finishWith(null) }
        } else {
            val labels = notes.map { note -> note.title.ifBlank { note.content.take(40) }.ifBlank { "Untitled" } }.toTypedArray()
            dialog.setItems(labels) { _, which -> finishWith(notes[which].id) }
        }
        dialog.setOnCancelListener { finish() }.show()
    }
}
