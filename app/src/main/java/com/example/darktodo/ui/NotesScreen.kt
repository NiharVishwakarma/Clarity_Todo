package com.example.darktodo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.NoteAppearance
import com.example.darktodo.data.Sticker
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerOps
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.WidgetBackgrounds
import kotlinx.coroutines.delay

private val noteFieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = UiTokens.Ink, unfocusedTextColor = UiTokens.Ink,
    focusedBorderColor = UiTokens.Accent, unfocusedBorderColor = UiTokens.Subdued,
    focusedLabelColor = UiTokens.Accent, unfocusedLabelColor = UiTokens.Muted, cursorColor = UiTokens.Accent
)

/**
 * The Notes tab. Tap a card to edit, tap its gear for that note's styling, long-press for rename, duplicate and delete.
 * [initialEditId] opens that note's editor straight away (used when a home-screen note is tapped).
 */
@Composable fun NotesScreen(initialEditId: String? = null, onInitialEditHandled: () -> Unit = {}, onOpenNoteSettings: (String) -> Unit = {}) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var notes by remember { mutableStateOf(StickerStore.load(context)) }
    val global = remember { StickerStore.globalStyle(context) }
    var editing by remember { mutableStateOf<Sticker?>(null) }
    var creating by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Sticker?>(null) }
    var renaming by remember { mutableStateOf<Sticker?>(null) }
    var deleting by remember { mutableStateOf<Sticker?>(null) }

    LaunchedEffect(initialEditId) {
        if (initialEditId != null) {
            notes.firstOrNull { it.id == initialEditId }?.let { editing = it }
            onInitialEditHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Notes \uD83D\uDCDD", color = UiTokens.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Quick notes that stay in view", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
            ) {
                if (notes.isEmpty()) {
                    item {
                        Text(
                            "No Notes Yet \uD83D\uDCDD\nTap + to jot down a quick note.",
                            color = UiTokens.Muted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    items(StickerOps.display(notes).chunked(2), key = { it.first().id }) { rowNotes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowNotes.forEach { note ->
                                val style = NoteAppearance.effectiveStyle(context, StickerStyle.resolve(note, global), note.id)
                                Box(
                                    Modifier.weight(1f).combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                                        onClick = { editing = note },
                                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); actionsFor = note }
                                    )
                                ) {
                                    NoteSurface(note.title, note.content, style, WidgetBackgrounds.noteKey(note.id)) {
                                        Box(
                                            Modifier.size(28.dp).clickable { onOpenNoteSettings(note.id) }
                                                .semantics { contentDescription = "Settings for ${note.title.ifBlank { "this note" }}" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ClarityGlyph(Icons.Outlined.Settings, tint = Color(StickerHex.toArgb(style.text)).copy(alpha = .9f), size = 20.dp)
                                        }
                                    }
                                }
                            }
                            if (rowNotes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item {
                    QuotesSection()
                }
            }
        }
        Surface(
            onClick = { creating = true }, color = UiTokens.Accent, shape = RoundedCornerShape(UiTokens.CardRadius), shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp).size(56.dp).semantics { contentDescription = "Create note" }
        ) { Box(contentAlignment = Alignment.Center) { ClarityGlyph(Icons.Outlined.Add, tint = Color.White, size = 24.dp) } }
    }

    if (creating) NoteEditor(
        initial = null,
        onDismiss = { creating = false },
        onSave = { title, content -> notes = StickerStore.add(context, title, content); creating = false },
        onDelete = null
    )
    editing?.let { note ->
        NoteEditor(
            initial = note,
            onDismiss = { editing = null },
            onSave = { title, content -> notes = StickerStore.edit(context, note.id, title, content); editing = null },
            onDelete = { deleting = note; editing = null }
        )
    }
    actionsFor?.let { note ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text(note.title.ifBlank { "Note" }, color = UiTokens.Ink, maxLines = 1) },
            text = {
                Column {
                    TextButton(onClick = { actionsFor = null; renaming = note }, modifier = Modifier.fillMaxWidth()) { Text("Rename", color = UiTokens.Ink) }
                    TextButton(onClick = { actionsFor = null; notes = StickerStore.duplicate(context, note.id) }, modifier = Modifier.fillMaxWidth()) { Text("Duplicate", color = UiTokens.Ink) }
                    TextButton(onClick = { actionsFor = null; deleting = note }, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = UiTokens.Accent) }
                }
            },
            confirmButton = { TextButton(onClick = { actionsFor = null }) { Text("Close", color = UiTokens.Muted) } }
        )
    }
    renaming?.let { note ->
        var name by remember(note.id) { mutableStateOf(note.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text("Rename note", color = UiTokens.Ink) },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Title") }, singleLine = true, colors = noteFieldColors, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { notes = StickerStore.rename(context, note.id, name); renaming = null }, enabled = name.isNotBlank()) {
                    Text("Save", color = if (name.isNotBlank()) UiTokens.Accent else UiTokens.Subdued)
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = UiTokens.Ink) } }
        )
    }
    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = UiTokens.SurfaceRaised,
            title = { Text("Delete note?", color = UiTokens.Ink) },
            text = { Text("\"${note.title.ifBlank { "This note" }}\" will be permanently deleted.", color = UiTokens.Muted) },
            confirmButton = { TextButton(onClick = { notes = StickerStore.delete(context, note.id); deleting = null }) { Text("Delete", color = UiTokens.Accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = UiTokens.Ink) } }
        )
    }
}

/** Title and text only; styling lives in the note's settings. Focus and keyboard are requested as soon as it opens. */
@Composable private fun NoteEditor(initial: Sticker?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    val startText = initial?.content ?: ""
    var content by remember { mutableStateOf(TextFieldValue(startText, TextRange(startText.length))) }
    val titleFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { if (initial == null) titleFocus.requestFocus() else contentFocus.requestFocus() }
        keyboard?.show()
    }
    val canSave = title.isNotBlank() || content.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UiTokens.SurfaceRaised,
        title = { Text(if (initial == null) "New note" else "Edit note", color = UiTokens.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, colors = noteFieldColors, modifier = Modifier.fillMaxWidth().focusRequester(titleFocus))
                OutlinedTextField(content, { content = it }, label = { Text("Note") }, minLines = 3, maxLines = 8, colors = noteFieldColors, modifier = Modifier.fillMaxWidth().focusRequester(contentFocus))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content.text) }, enabled = canSave) {
                Text(if (initial == null) "Create" else "Save", color = if (canSave) UiTokens.Accent else UiTokens.Subdued)
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = UiTokens.Accent) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = UiTokens.Ink) }
            }
        }
    )
}
