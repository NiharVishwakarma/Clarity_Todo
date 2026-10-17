package com.example.darktodo.ui

import com.example.darktodo.UiTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.data.Sticker
import com.example.darktodo.data.StickerColor
import com.example.darktodo.data.StickerOps
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle

@Composable fun ComingSoonScreen(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(icon, title, "Coming soon")
        EmptyState(icon, "$title is on the way", message)
    }
}

@Composable fun StickersScreen(onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    var stickers by remember { mutableStateOf(StickerStore.load(context)) }
    val globalStyle = remember { StickerStore.globalStyle(context) }
    val globalColors = remember { StickerStore.saveAsDefault(context) }
    var book by remember { mutableStateOf(StickerStore.colorBook(context)) }
    var editor by remember { mutableStateOf<Sticker?>(null) }
    var creating by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick notes that stay in view", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, modifier = Modifier.weight(1f))
                SettingsGearButton("Sticker settings", onOpenSettings)
            }
            if (stickers.isEmpty()) EmptyState(ClarityIcons.Stickers, "No stickers yet", "Tap + to jot down a quick note.")
            else LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(StickerOps.display(stickers), key = { it.id }) { sticker ->
                    val style = StickerStyle.resolve(sticker, globalStyle, globalColors)
                    Surface(onClick = { editor = sticker }, color = Color.Transparent, shape = RoundedCornerShape(style.cornerRadiusDp.dp)) {
                        StickerSurface(sticker.title, sticker.content, style, sticker.pinned) {
                            Box(Modifier.size(28.dp).clickable { stickers = StickerStore.setPinned(context, sticker.id, !sticker.pinned) }.semantics { contentDescription = if (sticker.pinned) "Unpin sticker" else "Pin sticker" }, contentAlignment = Alignment.Center) {
                                StickerPinGlyph(style, sticker.pinned)
                            }
                        }
                    }
                }
            }
        }
        Surface(
            onClick = { creating = true }, color = UiTokens.Accent, shape = RoundedCornerShape(UiTokens.CardRadius), shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp).size(56.dp).semantics { contentDescription = "Create sticker" }
        ) { Box(contentAlignment = Alignment.Center) { ClarityGlyph(Icons.Outlined.Add, tint = Color.White, size = 24.dp) } }
    }
    if (creating) StickerEditor(
        initial = null, globalStyle = globalStyle, book = book, onBook = { book = it; StickerStore.saveColorBook(context, it) },
        onDismiss = { creating = false },
        onSave = { title, content, color, _, customStyle ->
            stickers = StickerStore.add(context, title, content, color)
            if (customStyle != null) stickers.maxByOrNull { it.createdAt }?.let { stickers = StickerStore.setStyle(context, it.id, customStyle) }
            creating = false
        },
        onDelete = null
    )
    editor?.let { sticker ->
        StickerEditor(
            initial = sticker, globalStyle = globalStyle, book = book, onBook = { book = it; StickerStore.saveColorBook(context, it) },
            onDismiss = { editor = null },
            onSave = { title, content, color, pinned, customStyle ->
                stickers = StickerStore.edit(context, sticker.id, title, content, color)
                if (pinned != sticker.pinned) stickers = StickerStore.setPinned(context, sticker.id, pinned)
                if (customStyle != sticker.style) stickers = StickerStore.setStyle(context, sticker.id, customStyle)
                editor = null
            },
            onDelete = { stickers = StickerStore.delete(context, sticker.id); editor = null }
        )
    }
}
@Composable private fun StickerEditor(
    initial: Sticker?,
    globalStyle: StickerStyle,
    book: StickerColorBook,
    onBook: (StickerColorBook) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, StickerColor, Boolean, StickerStyle?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: StickerColor.Yellow) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    var customStyle by remember { mutableStateOf(initial?.style) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = UiTokens.Ink, unfocusedTextColor = UiTokens.Ink,
        focusedBorderColor = UiTokens.Accent, unfocusedBorderColor = UiTokens.Subdued,
        focusedLabelColor = UiTokens.Accent, unfocusedLabelColor = UiTokens.Muted, cursorColor = UiTokens.Accent
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UiTokens.SurfaceRaised,
        title = { Text(if (initial == null) "New sticker" else "Edit sticker", color = UiTokens.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(content, { content = it }, label = { Text("Content") }, minLines = 3, maxLines = 8, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StickerColor.entries.forEach { option ->
                        Box(
                            Modifier.size(36.dp).background(Color(option.background), CircleShape)
                                .border(if (option == color) 3.dp else 0.dp, if (option == color) UiTokens.Ink else Color.Transparent, CircleShape)
                                .clickable { color = option }.semantics { contentDescription = "${option.label} color" }
                        )
                    }
                }
                Row(Modifier.clickable { customStyle = if (customStyle == null) StickerStyle.resolve(Sticker("", title, content, color), globalStyle) else null }.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (customStyle == null) "Customize this sticker" else "Using custom style · tap to use global", color = if (customStyle == null) UiTokens.Muted else UiTokens.Accent, fontSize = UiTokens.LabelSp.sp)
                }
                customStyle?.let { current ->
                    StickerSurface(title, content, current, pinned) { StickerPinGlyph(current, pinned) }
                    StickerStyleControls(current, book, onBook) { customStyle = it }
                }
                Row(Modifier.clickable { pinned = !pinned }.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClarityGlyph(Icons.Outlined.PushPin, tint = if (pinned) UiTokens.Accent else UiTokens.Muted, size = 20.dp)
                    Text(if (pinned) "Pinned" else "Pin to top", color = if (pinned) UiTokens.Accent else UiTokens.Muted, fontSize = UiTokens.LabelSp.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, color, pinned, customStyle) }, enabled = title.isNotBlank() || content.isNotBlank()) {
                Text(if (initial == null) "Create" else "Save", color = if (title.isNotBlank() || content.isNotBlank()) UiTokens.Accent else UiTokens.Subdued)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = UiTokens.Accent) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = UiTokens.Ink) }
            }
        }
    )
}

@Composable fun SupportClarityScreen(onBack: () -> Unit) {
    val amounts = listOf("₹50", "₹100", "₹250", "₹500")
    var selected by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.clickable(onClick = onBack).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClarityGlyph(Icons.AutoMirrored.Outlined.ArrowBack, size = 22.dp)
            Text("Settings", color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp)
        }
        ScreenHeader(ClarityIcons.Support, "Support Clarity", "Optional and never required", Modifier.padding(bottom = 0.dp))
        Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius)) {
            Text(
                "Supporting Clarity is completely optional and helps fund future updates.\nNo features, rewards, or advantages are unlocked through donations.",
                color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.padding(16.dp)
            )
        }
        amounts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { amount -> OutlineChip(amount, selected == amount, { selected = amount; custom = "" }, Modifier.weight(1f)) }
            }
        }
        OutlinedTextField(
            custom, { value -> custom = value.filter { it.isDigit() }.take(6); if (custom.isNotEmpty()) selected = null },
            label = { Text("Custom Amount (₹)") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = UiTokens.Ink, unfocusedTextColor = UiTokens.Ink,
                focusedBorderColor = UiTokens.Accent, unfocusedBorderColor = UiTokens.Subdued,
                focusedLabelColor = UiTokens.Accent, unfocusedLabelColor = UiTokens.Muted, cursorColor = UiTokens.Accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Payments are not available yet. This screen is a preview of the layout.", color = UiTokens.Subdued, fontSize = UiTokens.DetailSp.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun AboutScreen(onBack: () -> Unit, versionName: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.clickable(onClick = onBack).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClarityGlyph(Icons.AutoMirrored.Outlined.ArrowBack, size = 22.dp)
            Text("Settings", color = UiTokens.Muted, fontSize = UiTokens.LabelSp.sp)
        }
        ScreenHeader(ClarityIcons.About, "About", "Clarity")
        Surface(color = UiTokens.Surface, shape = RoundedCornerShape(UiTokens.CardRadius)) {
            Text("Version $versionName", color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}
