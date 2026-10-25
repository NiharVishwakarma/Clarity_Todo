package com.example.darktodo.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
@OptIn(ExperimentalFoundationApi::class)
@Composable fun NotesScreen(initialEditId: String? = null, onInitialEditHandled: () -> Unit = {}, onOpenNoteSettings: (String) -> Unit = {}, onOpenQuotesSettings: () -> Unit = {}) {
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
                item(key = "quotes-widget") {
                    QuotesTile(onOpenQuotesSettings)
                }
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
        onSave = { title, content, style -> notes = StickerStore.add(context, title, content, style); creating = false },
        onDelete = null
    )
    editing?.let { note ->
        NoteEditor(
            initial = note,
            onDismiss = { editing = null },
            onSave = { title, content, style -> notes = StickerStore.edit(context, note.id, title, content, style); editing = null },
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

@Composable
private fun QuotesTile(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { com.example.darktodo.data.QuotesWidgetPrefs.load(context) }
    val quote = remember { com.example.darktodo.widget.StickerWidget.previewQuote(context) }
    val bitmap = remember(settings, quote) {
        com.example.darktodo.widget.QuotesWidgetRendering.render(context, quote, settings, 300, 160)
    }
    Surface(shape = RoundedCornerShape(UiTokens.CardRadius), color = UiTokens.SurfaceRaised) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ClarityGlyph(ClarityIcons.Quotes, tint = UiTokens.Accent, size = 20.dp)
                Text("Quotes", color = UiTokens.Ink, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Box(
                    Modifier.size(40.dp).clickable(onClick = onOpenSettings)
                        .semantics { contentDescription = "Quotes widget settings" },
                    contentAlignment = Alignment.Center
                ) {
                    ClarityGlyph(Icons.Outlined.Settings, tint = UiTokens.Ink, size = 20.dp)
                }
            }
            Image(bitmap.asImageBitmap(),
                com.example.darktodo.widget.QuotesWidgetRendering.description(quote, settings, false),
                Modifier.fillMaxWidth().aspectRatio(300f / 160f).clickable(onClick = onOpenSettings))
            Text("Home-screen quote widget", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
    }
}

/** Text and appearance remain a draft until Save; the settings screen retains its separate live-save workflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun NoteEditor(initial: Sticker?, onDismiss: () -> Unit, onSave: (String, String, StickerStyle) -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial?.title ?: ""))
    }
    val startText = initial?.content ?: ""
    var content by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(startText, TextRange(startText.length))) }
    var styleJson by rememberSaveable { mutableStateOf(
        (initial?.let { StickerStore.styleFor(context, it) }
            ?: StickerStyle.resolve(Sticker("", "", ""), StickerStore.globalStyle(context)).copy(autoContrast = true,
                titleStyle = null, bodyStyle = null)).toJson().toString()
    ) }
    val style = remember(styleJson) { requireNotNull(StickerStyle.fromJson(org.json.JSONObject(styleJson))) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var book by remember { mutableStateOf(StickerStore.colorBook(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val noteId = initial?.id ?: "new-note-preview"
    val imageKey = initial?.id?.let(WidgetBackgrounds::noteKey)
    val titleFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { if (initial == null) titleFocus.requestFocus() else contentFocus.requestFocus() }
        keyboard?.show()
    }
    val canSave = title.text.isNotBlank() || content.text.isNotBlank()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(color = UiTokens.Surface, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (initial == null) "New note" else "Edit note", color = UiTokens.Ink,
                            fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close note editor", tint = UiTokens.Muted) }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Title", color = UiTokens.Muted, modifier = Modifier.weight(1f))
                            IconButton(onClick = { panel = if (panel == "title") null else "title" }) {
                                Icon(Icons.Outlined.Palette, "Title appearance", tint = UiTokens.Ink)
                            }
                        }
                        OutlinedTextField(title, { title = it }, singleLine = true, colors = noteFieldColors,
                            label = { Text("Title") }, modifier = Modifier.fillMaxWidth().focusRequester(titleFocus))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Note", color = UiTokens.Muted, modifier = Modifier.weight(1f))
                            IconButton(onClick = { panel = if (panel == "body") null else "body" }) {
                                Icon(Icons.Outlined.Palette, "Note appearance", tint = UiTokens.Ink)
                            }
                        }
                        OutlinedTextField(content, { content = it }, label = { Text("Note") }, minLines = 3, maxLines = 8,
                            colors = noteFieldColors, modifier = Modifier.fillMaxWidth().focusRequester(contentFocus))
                        Text("Live preview", color = UiTokens.Muted, fontSize = 12.sp)
                        NoteSurface(title.text, content.text, NoteAppearance.effectiveStyle(context, style, noteId), imageKey,
                            maxContentLines = 5)
                        panel?.let { target ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (target == "title") "Title appearance" else "Note appearance", color = UiTokens.Ink,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { panel = null }) { Text("Done", color = UiTokens.Ink) }
                            }
                            NoteTextAppearance(target == "title", noteId, style, book, { book = it }) {
                                styleJson = it.toJson().toString()
                            }
                        }
                    }
                    error?.let { Text(it, color = UiTokens.Accent, fontSize = 12.sp) }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = UiTokens.Accent) }
                        TextButton(onClick = onDismiss) { Text("Cancel", color = UiTokens.Ink) }
                        Button(enabled = canSave, colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Accent),
                            onClick = {
                                try { onSave(title.text, content.text, style) }
                                catch (failure: Exception) {
                                    android.util.Log.e("NoteEditor", "Could not save note", failure)
                                    error = "Could not save note. Your draft is still open; please retry."
                                }
                            }) { Text("Save") }
                    }
                }
            }
        }
    }
}
