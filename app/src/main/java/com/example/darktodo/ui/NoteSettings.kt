package com.example.darktodo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.NoteAppearance
import com.example.darktodo.data.StickerColor
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.StickerWidgetSize
import com.example.darktodo.data.WidgetBackgrounds
import com.example.darktodo.data.WidgetFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun noteFontFamily(key: String, custom: Typeface? = null): FontFamily = if (custom != null) FontFamily(custom) else when (key) {
    WidgetFontFamily.SERIF.key -> FontFamily.Serif
    WidgetFontFamily.MONOSPACE.key -> FontFamily.Monospace
    WidgetFontFamily.SANS_SERIF.key -> FontFamily.SansSerif
    else -> FontFamily(Typeface.create(key, Typeface.NORMAL))
}

/**
 * Single rendering path for notes, shared by the Notes grid and the settings preview. [style] must already be
 * the effective style (see NoteAppearance). [imageRevision] only forces a redraw after the image changes.
 */
@Composable fun NoteSurface(
    title: String, content: String, style: StickerStyle, imageKey: String?, modifier: Modifier = Modifier,
    maxContentLines: Int = 6, imageRevision: Int = 0, trailing: @Composable () -> Unit = {}
) {
    val appContext = LocalContext.current
    val ink = Color(StickerHex.toArgb(style.text))
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    val size = (style.fontSizeSp * style.textScalePercent / 100f).sp
    val family = noteFontFamily(style.fontFamily, LocalWidgetFonts.typeface(appContext, style.fontFileId))
    val bitmap = if (imageKey == null) null else WidgetBackgrounds.image(appContext, imageKey)
    val hasImage = imageKey != null && WidgetBackgrounds.has(appContext, imageKey)
    val image = remember(bitmap, imageRevision) { bitmap?.asImageBitmap() }
    val fade = if (imageKey == null || bitmap == null) 0f else WidgetBackgrounds.fade(appContext, imageKey) / 100f
    val opacity = style.opacityPercent / 100f
    val effect = when {
        style.glow -> Shadow(ink.copy(alpha = .85f), Offset.Zero, 14f)
        style.textShadow -> Shadow(Color.Black.copy(alpha = .6f), Offset(2f, 2f), 4f)
        else -> null
    }
    val textStyle = TextStyle(shadow = effect)
    Box(modifier.padding(style.marginDp.dp).shadow(style.elevationDp.dp, shape)) {
        // Fill, image, fade and border all sit inside the same clipped rounded shape.
        Column(
            Modifier.fillMaxWidth().heightIn(min = style.widgetSize.minHeightDp.dp)
                .clip(shape)
                .depthBackground(
                    Color(StickerHex.toArgb(style.background)).copy(alpha = opacity),
                    enabled = style.backgroundDepth && !hasImage
                )
                .then(if (image == null) Modifier else Modifier.drawBehind {
                    val scale = maxOf(this.size.width / image.width, this.size.height / image.height)
                    val srcW = (this.size.width / scale).toInt().coerceIn(1, image.width)
                    val srcH = (this.size.height / scale).toInt().coerceIn(1, image.height)
                    drawImage(
                        image, IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2), IntSize(srcW, srcH),
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), alpha = opacity
                    )
                    drawRect(Color.Black.copy(alpha = fade * opacity))
                })
                .then(if (style.borderWidthDp > 0) Modifier.border(style.borderWidthDp.dp, Color(StickerHex.toArgb(style.borderColor)), shape) else Modifier)
                .padding(style.paddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title.ifBlank { "Untitled" }, color = ink, fontFamily = family, fontSize = (size.value * 1.2f).sp, style = textStyle,
                    lineHeight = (size.value * 1.2f * style.lineHeight).sp, fontWeight = FontWeight((style.fontWeight + 200).coerceAtMost(900)),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { trailing() }
            }
            if (content.isNotBlank()) Text(
                content, color = ink.copy(alpha = .92f), fontFamily = family, fontSize = size, lineHeight = (size.value * style.lineHeight).sp, style = textStyle,
                fontWeight = FontWeight(style.fontWeight), maxLines = maxContentLines, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = UiTokens.Ink, unfocusedTextColor = UiTokens.Ink,
    focusedBorderColor = UiTokens.Accent, unfocusedBorderColor = UiTokens.Subdued,
    focusedLabelColor = UiTokens.Accent, unfocusedLabelColor = UiTokens.Muted, cursorColor = UiTokens.Accent
)

@Composable private fun Swatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).background(Color(StickerHex.toArgb(hex)), CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) UiTokens.Accent else Color.White.copy(alpha = .3f), CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = "Colour $hex" }
    )
}

/** HEX entry with paste, copy, visual picker and favourites. Valid values are committed instantly. */
@Composable fun HexColorField(label: String, value: String, book: StickerColorBook, onBook: (StickerColorBook) -> Unit, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember(value) { mutableStateOf(value) }
    var picker by remember { mutableStateOf(false) }
    val normalized = StickerHex.normalize(text)
    val isFavorite = normalized != null && normalized in book.favorites
    fun commit(hex: String) {
        val clean = StickerHex.normalize(hex) ?: return
        text = clean; onChange(clean); onBook(book.withRecent(clean))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).background(Color(StickerHex.toArgb(normalized ?: value)), RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(10.dp)).clickable { picker = true }.semantics { contentDescription = "Open colour picker for $label" })
            OutlinedTextField(
                text, { text = it.take(8); if (Regex("^#?[0-9A-Fa-f]{6}$").matches(it.trim())) StickerHex.normalize(it)?.let { hex -> onChange(hex) } },
                label = { Text(label) }, singleLine = true, isError = normalized == null, colors = fieldColors,
                modifier = Modifier.weight(1f), supportingText = { if (normalized == null) Text("Use a hex code like #FFD700", color = UiTokens.Accent) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                if (StickerHex.normalize(clip) != null) commit(clip.orEmpty()) else Toast.makeText(context, "Clipboard has no hex colour", Toast.LENGTH_SHORT).show()
            }) { Text("Paste", color = UiTokens.Accent, fontSize = UiTokens.DetailSp.sp) }
            TextButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("HEX colour", normalized ?: value))
                Toast.makeText(context, "Copied ${normalized ?: value}", Toast.LENGTH_SHORT).show()
            }) { Text("Copy", color = UiTokens.Accent, fontSize = UiTokens.DetailSp.sp) }
            TextButton(onClick = { normalized?.let { onBook(book.toggleFavorite(it)) } }, enabled = normalized != null) {
                Text(if (isFavorite) "\u2605 Saved" else "\u2606 Favourite", color = UiTokens.Accent, fontSize = UiTokens.DetailSp.sp)
            }
        }
        if (book.favorites.isNotEmpty()) {
            Text("Favourites", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { book.favorites.forEach { Swatch(it, it == normalized) { commit(it) } } }
        }
    }
    if (picker) ColorPickerDialog(label, normalized ?: value, onApply = { commit(it); picker = false }, onDismiss = { picker = false })
}

@Composable private fun StyleSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = UiTokens.Accent, activeTrackColor = UiTokens.Accent, inactiveTrackColor = UiTokens.Subdued))
    }
}

@Composable private fun ChoiceRow(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, (label, selected) ->
            Surface(
                color = if (selected) UiTokens.Accent.copy(alpha = .18f) else UiTokens.SurfaceRaised, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(i) }
            ) {
                Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) UiTokens.Accent else UiTokens.Ink, fontSize = UiTokens.DetailSp.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp)
            Text(hint, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = UiTokens.Accent))
    }
}

@Composable private fun GroupTitle(text: String, hint: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(text, color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
    }
}

/** Every styling control for one note. [imageSection] is placed right after the colours. */
@Composable fun NoteStyleControls(
    style: StickerStyle, book: StickerColorBook, onBook: (StickerColorBook) -> Unit,
    imageSection: @Composable () -> Unit, onChange: (StickerStyle) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importedFonts by remember { mutableStateOf(LocalWidgetFonts.list(context)) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LocalWidgetFonts.import(context, uri) } }
            result.fold(
                onSuccess = { font -> importedFonts = LocalWidgetFonts.list(context); onChange(style.copy(fontFileId = font.id)) },
                onFailure = { Toast.makeText(context, it.message ?: "Could not import that font file.", Toast.LENGTH_LONG).show() }
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupTitle("Colours", "Changes the background colour of this note and, with auto contrast off, its text colour.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StickerColor.entries.forEach { preset ->
                val background = "#%06X".format(preset.background and 0xFFFFFF)
                val ink = "#%06X".format(preset.ink and 0xFFFFFF)
                Swatch(background, style.background.equals(background, ignoreCase = true)) { onChange(style.copy(background = background, text = ink)) }
            }
        }
        HexColorField("Background HEX", style.background, book, onBook) { onChange(style.copy(background = it)) }
        if (!style.autoContrast) HexColorField("Text HEX", style.text, book, onBook) { onChange(style.copy(text = it)) }
        ToggleRow(
            "3D background effect", "Applies to colors only; image wallpapers stay unchanged", style.backgroundDepth
        ) { onChange(style.copy(backgroundDepth = it)) }

        imageSection()

        GroupTitle("Text readability", "Keeps the text readable on any colour or image.")
        ToggleRow("Auto contrast", "Picks light or dark text from the background. Turn off to choose the text colour yourself.", style.autoContrast) { onChange(style.copy(autoContrast = it)) }
        ToggleRow("Text shadow", "A soft shadow behind the text.", style.textShadow) { onChange(style.copy(textShadow = it)) }
        ToggleRow("Glow", "A glow around the text. Used instead of the shadow when both are on.", style.glow) { onChange(style.copy(glow = it)) }

        GroupTitle("Font", "Changes the font, size and weight of this note's title and text, in the app and on the home-screen widget.")
        WidgetFontFamily.entries.chunked(2).forEach { row ->
            ChoiceRow(row.map { it.label to (style.fontFileId == null && it.key == style.fontFamily) }) { onChange(style.copy(fontFamily = row[it].key, fontFileId = null)) }
        }
        Text("Custom fonts", color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp)
        importedFonts.chunked(2).forEach { row ->
            ChoiceRow(row.map { it.name.substringBeforeLast('.') to (it.id == style.fontFileId) }) { onChange(style.copy(fontFileId = row[it].id)) }
        }
        TextButton(onClick = { fontPicker.launch(arrayOf("*/*")) }) { Text("Import font file (.ttf, .otf, .ttc)", color = UiTokens.Accent, fontSize = UiTokens.DetailSp.sp) }
        StyleSlider("Font size", "${style.fontSizeSp.toInt()} sp", style.fontSizeSp, 10f..28f) { onChange(style.copy(fontSizeSp = it)) }
        Text("Font weight", color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp)
        val weights = listOf("Light" to 300, "Regular" to 400, "Medium" to 500, "Bold" to 700)
        ChoiceRow(weights.map { it.first to (it.second == style.fontWeight) }) { onChange(style.copy(fontWeight = weights[it].second)) }
        StyleSlider("Line height", "%.1f\u00D7".format(style.lineHeight), style.lineHeight, 1f..2f) { onChange(style.copy(lineHeight = (it * 10).toInt() / 10f)) }
        StyleSlider("Text scaling", "${style.textScalePercent}%", style.textScalePercent.toFloat(), 80f..150f) { onChange(style.copy(textScalePercent = it.toInt())) }

        GroupTitle("Border", "Changes the outline around this note. Set thickness to 0 for no border.")
        StyleSlider("Border thickness", if (style.borderWidthDp == 0) "None" else "${style.borderWidthDp} dp", style.borderWidthDp.toFloat(), 0f..12f) { onChange(style.copy(borderWidthDp = it.toInt())) }
        HexColorField("Border HEX", style.borderColor, book, onBook) { onChange(style.copy(borderColor = it)) }

        GroupTitle("Widget size", "Changes how tall this note's card is.")
        val sizes = StickerWidgetSize.entries
        ChoiceRow(sizes.map { it.label to (it == style.widgetSize) }) { onChange(style.copy(widgetSize = sizes[it])) }
        if (style.widgetSize == StickerWidgetSize.Auto) Text("Auto sizes the note to fit its text.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)

        GroupTitle("Card shape & spacing", "Changes the corner roundness, inner padding, outer margin, transparency and shadow of this note.")
        StyleSlider("Corner radius", "${style.cornerRadiusDp} dp", style.cornerRadiusDp.toFloat(), 0f..32f) { onChange(style.copy(cornerRadiusDp = it.toInt())) }
        StyleSlider("Padding", "${style.paddingDp} dp", style.paddingDp.toFloat(), 4f..32f) { onChange(style.copy(paddingDp = it.toInt())) }
        StyleSlider("Margin", "${style.marginDp} dp", style.marginDp.toFloat(), 0f..24f) { onChange(style.copy(marginDp = it.toInt())) }
        StyleSlider("Opacity", "${style.opacityPercent}%", style.opacityPercent.toFloat(), 30f..100f) { onChange(style.copy(opacityPercent = it.toInt())) }
        StyleSlider("Shadow", "${style.elevationDp} dp", style.elevationDp.toFloat(), 0f..16f) { onChange(style.copy(elevationDp = it.toInt())) }
    }
}

/** Styling for one note. Every change is saved to that note only and refreshes its home-screen widget. */
@Composable fun NoteSettingsScreen(noteId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val note = remember(noteId) { StickerStore.load(context).firstOrNull { it.id == noteId } }
    if (note == null) {
        LaunchedEffect(noteId) { onBack() }
        return
    }
    var style by remember(noteId) { mutableStateOf(StickerStore.styleFor(context, note)) }
    var dirty by remember(noteId) { mutableStateOf(false) }
    var book by remember { mutableStateOf(StickerStore.colorBook(context)) }
    var editingImage by remember { mutableStateOf(false) }
    var imageRevision by remember { mutableIntStateOf(0) }
    val imageKey = remember(noteId) { WidgetBackgrounds.noteKey(noteId) }
    val hasImage = remember(imageKey, imageRevision) { WidgetBackgrounds.has(context, imageKey) }
    val latestStyle by rememberUpdatedState(style)
    val latestDirty by rememberUpdatedState(dirty)

    // Debounced while dragging; the latest value is always flushed when the screen closes.
    LaunchedEffect(style) { if (dirty) { delay(150); StickerStore.setStyle(context, noteId, style) } }
    DisposableEffect(noteId) { onDispose { if (latestDirty) StickerStore.setStyle(context, noteId, latestStyle) } }

    if (editingImage) Dialog(onDismissRequest = { editingImage = false }) {
        Surface(color = Color(0xFF19191E), shape = RoundedCornerShape(20.dp)) {
            Box(Modifier.padding(16.dp)) {
                com.example.darktodo.WidgetBackgroundEditor(imageKey) { editingImage = false; imageRevision++ }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Note settings", color = UiTokens.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(note.title.ifBlank { "Untitled" }, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onBack) { Text("Done", color = UiTokens.Accent) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live preview", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            NoteSurface(
                note.title, note.content.ifBlank { "Your note text appears here." },
                NoteAppearance.effectiveStyle(context, style, noteId), imageKey, maxContentLines = 8, imageRevision = imageRevision
            )
            Text("These settings apply to this note only, and the home-screen widget showing it updates instantly.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            NoteStyleControls(
                style, book, { book = it; StickerStore.saveColorBook(context, it) },
                imageSection = {
                    Surface(color = UiTokens.SurfaceRaised, shape = RoundedCornerShape(UiTokens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Background image", color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
                            Text("Puts a picture behind this note. Raise the image fade to make text easier to read.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                            Row {
                                TextButton(onClick = { editingImage = true }) { Text(if (hasImage) "Change image" else "Choose image", color = UiTokens.Accent) }
                                if (hasImage) TextButton(onClick = {
                                    WidgetBackgrounds.clear(context, imageKey)
                                    com.example.darktodo.widget.StickerWidget.updateAll(context)
                                    imageRevision++
                                }) { Text("Remove image", color = UiTokens.Accent) }
                            }
                        }
                    }
                },
                onChange = { style = it; dirty = true }
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}
