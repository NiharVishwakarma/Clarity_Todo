package com.example.darktodo.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.NoteAppearance
import com.example.darktodo.data.NoteContrast
import com.example.darktodo.data.NoteTextStyle
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.WidgetFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The same per-element controls are used by the transactional editor and live-persisted note settings. */
@Composable
internal fun NoteTextAppearance(
    title: Boolean, noteId: String, style: StickerStyle,
    book: StickerColorBook, onBook: (StickerColorBook) -> Unit, onChange: (StickerStyle) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = if (title) style.titleText() else style.bodyText()
    val currentText by rememberUpdatedState(text)
    val update by rememberUpdatedState<(NoteTextStyle) -> Unit> { next ->
        onChange(if (title) style.copy(titleStyle = next) else style.copy(bodyStyle = next))
    }
    var fonts by remember { mutableStateOf(LocalWidgetFonts.list(context)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalWidgetFonts.import(context, uri) }
                    .onFailure { if (it is CancellationException) throw it }
            }
            result.fold(
                onSuccess = { font -> fonts = LocalWidgetFonts.list(context); update(currentText.copy(fontFileId = font.id)) },
                onFailure = { Toast.makeText(context, it.message ?: "Could not import font.", Toast.LENGTH_LONG).show() }
            )
        }
    }
    val backgrounds = NoteAppearance.backgroundRange(context, style, noteId)
    val recommended = NoteContrast.recommended(backgrounds)
    val actualColor = if (text.automatic) recommended else text.color
    val ratio = NoteContrast.worstRatio(actualColor, backgrounds)
    var colorExpanded by remember(title) { mutableStateOf(true) }
    var typographyExpanded by remember(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val effective = NoteAppearance.effectiveStyle(context, style, noteId)
        NoteSurface(if (title) "Title preview" else "Title", if (title) "Note body" else "Note body preview",
            effective, com.example.darktodo.data.WidgetBackgrounds.noteKey(noteId), maxContentLines = 2)
        CollapsibleSettingsSection("Color", "Automatic or custom foreground", colorExpanded,
            { colorExpanded = it }, icon = ClarityIcons.Colors) {
            ToggleRow("Automatic contrast", "Independent for ${if (title) "title" else "note body"}. Custom colors are never replaced.",
                text.automatic) { update(text.copy(automatic = it)) }
            if (ratio < 4.5f) {
                Text(if (backgrounds.start != backgrounds.endInclusive)
                    "Contrast warning: readability cannot be guaranteed over this image or translucent background. Increase background opacity/image fade."
                    else "Contrast warning: %.2f:1, below the 4.5:1 normal-text target.".format(ratio),
                    color = UiTokens.Accent, fontSize = 13.sp)
                TextButton(onClick = { update(text.copy(color = recommended, automatic = true)) }) {
                    Text("Use recommended color", color = UiTokens.Ink)
                }
            }
            val palettes = listOf(
                "Neutral" to listOf("#FFFFFF", "#D1D5DB", "#000000"),
                "Warm" to listOf("#FCA5A5", "#FDBA74", "#FDE68A"),
                "Cool" to listOf("#A7F3D0", "#93C5FD", "#C4B5FD")
            )
            palettes.forEach { (name, colors) ->
                Text(name, color = UiTokens.Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        val chosen = !text.automatic && text.color == color
                        Surface(onClick = { update(text.copy(color = color, automatic = false)) },
                            color = Color(StickerHex.toArgb(color)), shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp).semantics { selected = chosen }) {
                            Text(if (chosen) "$color Selected" else color,
                                color = Color(StickerHex.toArgb(NoteContrast.inkFor(NoteContrast.luminance(color)))),
                                fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
            HexColorField("Custom text color", text.color, book, onBook) { update(text.copy(color = it, automatic = false)) }
        }
        CollapsibleSettingsSection("Typography", "Font, size, weight, alignment and spacing", typographyExpanded,
            { typographyExpanded = it }, icon = ClarityIcons.Fonts) {
            WidgetFontFamily.entries.chunked(2).forEach { row ->
                ChoiceRow(row.map { it.label to (text.fontFileId == null && text.fontFamily == it.key) }) {
                    update(text.copy(fontFamily = row[it].key, fontFileId = null))
                }
            }
            fonts.chunked(2).forEach { row ->
                ChoiceRow(row.map { it.name to it.containsId(text.fontFileId) }) { update(text.copy(fontFileId = row[it].id)) }
            }
            TextButton(onClick = {
                try { picker.launch(arrayOf("*/*")) }
                catch (error: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No font file picker is available.", Toast.LENGTH_LONG).show()
                }
            }) { Text("Import font (.ttf, .otf, .ttc)", color = UiTokens.Ink) }
            StyleSlider("Font size", "${text.sizeSp.toInt()} sp", text.sizeSp, 10f..34f) { update(text.copy(sizeSp = it)) }
            val weights = listOf("Regular" to 400, "Medium" to 500, "Semibold" to 600, "Bold" to 700)
            weights.chunked(2).forEach { row ->
                ChoiceRow(row.map { it.first to (text.weight == it.second) }) { update(text.copy(weight = row[it].second)) }
            }
            ToggleRow("Italic", "Applies only to this text element.", text.italic) { update(text.copy(italic = it)) }
            val alignments = listOf("start", "center", "end")
            ChoiceRow(listOf("Start", "Center", "End").mapIndexed { index, label -> label to (text.alignment == alignments[index]) }) {
                update(text.copy(alignment = alignments[it]))
            }
            StyleSlider("Line spacing", "%.1fx".format(text.lineHeight), text.lineHeight, 1f..2f) { update(text.copy(lineHeight = it)) }
        }
        TextButton(onClick = { update(NoteTextStyle(sizeSp = if (title) 15.6f else 13f, weight = if (title) 600 else 400)) }) {
            Text("Reset ${if (title) "title" else "note"} appearance", color = UiTokens.Ink)
        }
    }
}
