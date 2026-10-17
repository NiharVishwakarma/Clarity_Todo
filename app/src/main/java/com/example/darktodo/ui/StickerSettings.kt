package com.example.darktodo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.StickerWidgetSize
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.WidgetBackgrounds
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.darktodo.data.WidgetFontFamily

fun stickerFontFamily(key: String, custom: Typeface? = null): FontFamily = if (custom != null) FontFamily(custom) else when (key) {
    WidgetFontFamily.SERIF.key -> FontFamily.Serif
    WidgetFontFamily.MONOSPACE.key -> FontFamily.Monospace
    WidgetFontFamily.SANS_SERIF.key -> FontFamily.SansSerif
    else -> FontFamily(Typeface.create(key, Typeface.NORMAL))
}

/** Single rendering path for stickers, shared by the grid, the editor and the settings preview. */
@Composable fun StickerSurface(title: String, content: String, style: StickerStyle, pinned: Boolean, modifier: Modifier = Modifier, maxContentLines: Int = 6, trailing: @Composable () -> Unit = {}) {
    val ink = Color(StickerHex.toArgb(style.text))
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    val size = (style.fontSizeSp * style.textScalePercent / 100f).sp
    val family = stickerFontFamily(style.fontFamily, LocalWidgetFonts.typeface(LocalContext.current, style.fontFileId))
    val appContext = LocalContext.current
    val wallpaperFile = WidgetBackgrounds.file(appContext, WidgetBackgrounds.STICKER)
    val wallpaperStamp = if (wallpaperFile.isFile) wallpaperFile.lastModified() else 0L
    val wallpaper = remember(wallpaperStamp) {
        if (wallpaperStamp == 0L) null else runCatching { BitmapFactory.decodeFile(wallpaperFile.absolutePath)?.asImageBitmap() }.getOrNull()
    }
    val wallpaperFade = WidgetBackgrounds.fade(appContext, WidgetBackgrounds.STICKER) / 100f
    val opacity = style.opacityPercent / 100f
    Box(modifier.padding(style.marginDp.dp).shadow(style.elevationDp.dp, shape)) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = style.widgetSize.minHeightDp.dp)
                .clip(shape)
                .background(Color(StickerHex.toArgb(style.background)).copy(alpha = opacity), shape)
                .then(if (wallpaper == null) Modifier else Modifier.drawBehind {
                    val scale = maxOf(this.size.width / wallpaper.width, this.size.height / wallpaper.height)
                    val srcW = (this.size.width / scale).toInt().coerceIn(1, wallpaper.width)
                    val srcH = (this.size.height / scale).toInt().coerceIn(1, wallpaper.height)
                    drawImage(
                        wallpaper, IntOffset((wallpaper.width - srcW) / 2, (wallpaper.height - srcH) / 2), IntSize(srcW, srcH),
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), alpha = opacity
                    )
                    drawRect(Color.Black.copy(alpha = wallpaperFade * opacity))
                })
                .then(if (style.borderWidthDp > 0) Modifier.border(style.borderWidthDp.dp, Color(StickerHex.toArgb(style.borderColor)), shape) else Modifier)
                .padding(style.paddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title.ifBlank { "Untitled" }, color = ink, fontFamily = family, fontSize = (size.value * 1.2f).sp,
                    lineHeight = (size.value * 1.2f * style.lineHeight).sp, fontWeight = FontWeight((style.fontWeight + 200).coerceAtMost(900)),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { trailing() }
            }
            if (content.isNotBlank()) Text(
                content, color = ink.copy(alpha = .88f), fontFamily = family, fontSize = size, lineHeight = (size.value * style.lineHeight).sp,
                fontWeight = FontWeight(style.fontWeight), maxLines = maxContentLines, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable fun StickerPinGlyph(style: StickerStyle, pinned: Boolean) {
    ClarityGlyph(Icons.Outlined.PushPin, tint = Color(StickerHex.toArgb(style.pin)).copy(alpha = if (pinned) 1f else .4f), size = 20.dp)
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

/** HEX entry with paste, copy, visual picker, favourites and recents. Valid values are committed instantly. */
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
                Text(if (isFavorite) "★ Saved" else "☆ Favourite", color = UiTokens.Accent, fontSize = UiTokens.DetailSp.sp)
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

@Composable private fun GroupTitle(text: String, hint: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(text, color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
    }
}

/** Every sticker style control. Used for the global style and, inside the editor, for a per-sticker override. */
@Composable fun StickerStyleControls(style: StickerStyle, book: StickerColorBook, onBook: (StickerColorBook) -> Unit, showFonts: Boolean = true, showLayout: Boolean = true, onChange: (StickerStyle) -> Unit) {
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
        GroupTitle("Sticker colours", "Changes the card background, the text colour and the pin colour.")
        HexColorField("Card background HEX", style.background, book, onBook) { onChange(style.copy(background = it)) }
        HexColorField("Text HEX", style.text, book, onBook) { onChange(style.copy(text = it)) }
        HexColorField("Pin HEX", style.pin, book, onBook) { onChange(style.copy(pin = it)) }

        if (showFonts) {
        GroupTitle("Fonts Customization", "Changes the font, size and weight of the title and note text, in the app and on the home-screen widget.")
        val families = WidgetFontFamily.entries
        families.chunked(2).forEach { row ->
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
        StyleSlider("Line height", "%.1f×".format(style.lineHeight), style.lineHeight, 1f..2f) { onChange(style.copy(lineHeight = (it * 10).toInt() / 10f)) }
        StyleSlider("Text scaling", "${style.textScalePercent}%", style.textScalePercent.toFloat(), 80f..150f) { onChange(style.copy(textScalePercent = it.toInt())) }
        }

        if (showLayout) {
        GroupTitle("Border", "Changes the outline around the sticker card. Set thickness to 0 for no border.")
        StyleSlider("Border thickness", if (style.borderWidthDp == 0) "None" else "${style.borderWidthDp} dp", style.borderWidthDp.toFloat(), 0f..12f) { onChange(style.copy(borderWidthDp = it.toInt())) }
        HexColorField("Border HEX", style.borderColor, book, onBook) { onChange(style.copy(borderColor = it)) }

        GroupTitle("Widget size", "Changes how tall the sticker card is.")
        val sizes = StickerWidgetSize.entries
        ChoiceRow(sizes.map { it.label to (it == style.widgetSize) }) { onChange(style.copy(widgetSize = sizes[it])) }
        if (style.widgetSize == StickerWidgetSize.Auto) Text("Auto sizes each sticker to fit its text.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)

        GroupTitle("Card shape & spacing", "Changes the corner roundness, inner padding, outer margin, transparency and shadow of the card.")
        StyleSlider("Corner radius", "${style.cornerRadiusDp} dp", style.cornerRadiusDp.toFloat(), 0f..32f) { onChange(style.copy(cornerRadiusDp = it.toInt())) }
        StyleSlider("Padding", "${style.paddingDp} dp", style.paddingDp.toFloat(), 4f..32f) { onChange(style.copy(paddingDp = it.toInt())) }
        StyleSlider("Margin", "${style.marginDp} dp", style.marginDp.toFloat(), 0f..24f) { onChange(style.copy(marginDp = it.toInt())) }
        StyleSlider("Opacity", "${style.opacityPercent}%", style.opacityPercent.toFloat(), 30f..100f) { onChange(style.copy(opacityPercent = it.toInt())) }
        StyleSlider("Elevation / shadow", "${style.elevationDp} dp", style.elevationDp.toFloat(), 0f..16f) { onChange(style.copy(elevationDp = it.toInt())) }
        }
    }
}
@Composable fun StickerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(StickerStore.globalStyle(context)) }
    var saveDefault by remember { mutableStateOf(StickerStore.saveAsDefault(context)) }
    var book by remember { mutableStateOf(StickerStore.colorBook(context)) }
    // Debounced while dragging; the latest value is always flushed when the screen closes.
    val latestStyle by androidx.compose.runtime.rememberUpdatedState(style)
    LaunchedEffect(style) { kotlinx.coroutines.delay(150); StickerStore.saveGlobalStyle(context, style) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { StickerStore.saveGlobalStyle(context, latestStyle) } }
    var editingBackground by remember { mutableStateOf(false) }
    var backgroundRevision by remember { mutableIntStateOf(0) }
    if (editingBackground) Dialog(onDismissRequest = { editingBackground = false }) {
        Surface(color = Color(0xFF19191E), shape = RoundedCornerShape(20.dp)) {
            Box(Modifier.padding(16.dp)) {
                com.example.darktodo.WidgetBackgroundEditor(WidgetBackgrounds.STICKER) { editingBackground = false; backgroundRevision++ }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sticker settings", color = UiTokens.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Done", color = UiTokens.Accent) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live preview", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            StickerSurface("Preview sticker", "Changes appear here instantly. Long notes wrap and never clip.", style, pinned = true, maxContentLines = 8) { StickerPinGlyph(style, true) }
            Surface(color = UiTokens.SurfaceRaised, shape = RoundedCornerShape(UiTokens.CardRadius), modifier = Modifier.fillMaxWidth().clickable {
                saveDefault = !saveDefault; StickerStore.setSaveAsDefault(context, saveDefault)
            }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Save As Default Style", color = UiTokens.Ink, fontSize = UiTokens.LabelSp.sp, fontWeight = FontWeight.Medium)
                        Text("Stickers without their own style also use these colours, font, radius and padding.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(saveDefault, { saveDefault = it; StickerStore.setSaveAsDefault(context, it) }, colors = SwitchDefaults.colors(checkedTrackColor = UiTokens.Accent))
                }
            }
            Text("These settings apply to every sticker. A sticker can override them from its editor.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            Surface(color = UiTokens.SurfaceRaised, shape = RoundedCornerShape(UiTokens.CardRadius), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Background image", color = UiTokens.Ink, fontSize = UiTokens.SectionTitleSp.sp, fontWeight = FontWeight.SemiBold)
                    Text("Puts a picture behind every sticker, in the app and on the widget. Raise the image fade to make text easier to read on colourful pictures.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
                    Row {
                        TextButton(onClick = { editingBackground = true }) {
                            Text(if (WidgetBackgrounds.has(context, WidgetBackgrounds.STICKER) || backgroundRevision < 0) "Change image" else "Choose image", color = UiTokens.Accent)
                        }
                        if (WidgetBackgrounds.has(context, WidgetBackgrounds.STICKER)) TextButton(onClick = {
                            WidgetBackgrounds.clear(context, WidgetBackgrounds.STICKER)
                            com.example.darktodo.widget.StickerWidget.updateAll(context)
                            backgroundRevision++
                        }) { Text("Remove image", color = UiTokens.Accent) }
                    }
                }
            }
            Text("Home-screen widget: add \"Clarity Sticker\" from your launcher's widget picker. It uses these styles, updates instantly, and tapping it flips to the next sticker.", color = UiTokens.Muted, fontSize = UiTokens.DetailSp.sp)
            StickerStyleControls(style, book, { book = it; StickerStore.saveColorBook(context, it) }) { style = it }
            TextButton(onClick = { style = StickerStyle.DEFAULT }) { Text("Reset to defaults", color = UiTokens.Accent) }
            Spacer(Modifier.size(24.dp))
        }
    }
}
