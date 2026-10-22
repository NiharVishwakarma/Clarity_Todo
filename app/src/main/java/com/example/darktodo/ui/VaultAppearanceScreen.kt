package com.example.darktodo.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.R
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.VaultAppearance
import com.example.darktodo.data.VaultAppearancePrefs
import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.widget.VaultWidgetRendering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultAppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(VaultAppearancePrefs.load(context)) }
    var fonts by remember { mutableStateOf(LocalWidgetFonts.list(context)) }
    var book by remember { mutableStateOf(StickerColorBook()) }
    var importing by remember { mutableStateOf(false) }
    var fontsExpanded by rememberSettingsSectionExpanded()
    var colorsExpanded by rememberSettingsSectionExpanded()
    var backgroundExpanded by rememberSettingsSectionExpanded()
    val importScope = rememberCoroutineScope()
    fun update(value: VaultAppearance) {
        style = value.sanitized()
        VaultAppearancePrefs.save(context, style)
    }
    fun preview(value: VaultAppearance) {
        style = value.sanitized()
    }
    fun commitPreview() {
        VaultAppearancePrefs.save(context, style)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importScope.launch {
            importing = true
            val readPermission = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, readPermission) }
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val font = LocalWidgetFonts.import(context.applicationContext, uri)
                        font to LocalWidgetFonts.list(context.applicationContext)
                    }
                }
                result.onSuccess { (font, availableFonts) ->
                    fonts = availableFonts
                    update(style.copy(fontFileId = font.id))
                }.onFailure {
                    Toast.makeText(context, it.message ?: "Could not import font", Toast.LENGTH_LONG).show()
                }
            } finally {
                runCatching { context.contentResolver.releasePersistableUriPermission(uri, readPermission) }
                importing = false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = UiTokens.Accent) }
            Text("Vault Appearance", color = UiTokens.Ink, fontSize = 20.sp)
        }
        Text("Live preview · all Vault widgets", color = UiTokens.Muted)
        VaultAppearancePreview(style)
        Text("Sliders preview while dragging and save on release. Other changes save immediately. This shortcut never displays passwords. Appearance is local to this device.", color = UiTokens.Muted)
        CollapsibleSettingsSection(
            title = "Fonts",
            summary = "Font family · Font size and weight (title/status)",
            expanded = fontsExpanded,
            onExpandedChange = { fontsExpanded = it },
            icon = ClarityIcons.Fonts
        ) {
            Text("Font family", color = UiTokens.Ink)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                WidgetFontFamily.entries.forEach { family ->
                    TextButton(onClick = { update(style.copy(fontFamily = family.key, fontFileId = null)) }) {
                        Text(
                            (if (style.fontFileId == null && style.fontFamily == family.key) "✓ " else "") + family.label,
                            color = UiTokens.Accent
                        )
                    }
                }
            }
            fonts.forEach { font ->
                TextButton(onClick = { update(style.copy(fontFileId = font.id)) }) {
                    Text((if (font.containsId(style.fontFileId)) "✓ " else "") + font.name, color = UiTokens.Accent)
                }
            }
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = !importing) {
                Text(if (importing) "Importing font…" else "Import font (.ttf, .otf, .ttc)", color = UiTokens.Accent)
            }
            VaultSlider("Font size (title)", style.titleSizeSp, 12f..28f, "sp", onFinished = ::commitPreview) { preview(style.copy(titleSizeSp = it)) }
            VaultSlider("Font size (status)", style.statusSizeSp, 10f..22f, "sp", onFinished = ::commitPreview) { preview(style.copy(statusSizeSp = it)) }
            VaultSlider("Font weight (title)", style.titleWeight.toFloat(), 100f..900f, "", onFinished = ::commitPreview) { preview(style.copy(titleWeight = it.toInt())) }
            VaultSlider("Font weight (status)", style.statusWeight.toFloat(), 100f..900f, "", onFinished = ::commitPreview) { preview(style.copy(statusWeight = it.toInt())) }
            Text("On Android 8, weights use normal/bold equivalents.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection(
            title = "Colors",
            summary = "Title · Status · Accent/outline",
            expanded = colorsExpanded,
            onExpandedChange = { colorsExpanded = it },
            icon = ClarityIcons.Colors
        ) {
            HexColorField("Title color", style.titleColor, book, { book = it }) { update(style.copy(titleColor = it)) }
            HexColorField("Status color", style.statusColor, book, { book = it }) { update(style.copy(statusColor = it)) }
            HexColorField("Accent / outline color", style.accentColor, book, { book = it }) { update(style.copy(accentColor = it)) }
            Text("Accent colors the card outline; the Clarity artwork is never tinted.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection(
            title = "Background",
            summary = "Background color · Background opacity",
            expanded = backgroundExpanded,
            onExpandedChange = { backgroundExpanded = it },
            icon = ClarityIcons.Background
        ) {
            HexColorField("Background color", style.backgroundColor, book, { book = it }) { update(style.copy(backgroundColor = it)) }
            VaultSlider("Background opacity", style.backgroundOpacityPercent.toFloat(), 0f..100f, "%", onFinished = ::commitPreview) {
                preview(style.copy(backgroundOpacityPercent = it.toInt()))
            }
        }
        VaultSlider("Rounded corners", style.cornerRadiusDp.toFloat(), 0f..40f, "dp", onFinished = ::commitPreview) { preview(style.copy(cornerRadiusDp = it.toInt())) }
        val lowContrast = minOf(style.contrastRatio(style.titleColor), style.contrastRatio(style.statusColor)) < 4.5f
        Text(
            if (style.backgroundOpacityPercent < 100) "Transparent backgrounds depend on your wallpaper. Check readability on light and dark wallpapers; text and artwork remain fully opaque."
            else if (lowContrast) "Low contrast: choose lighter text on dark backgrounds, or darker text on light backgrounds. Aim for at least 4.5:1."
            else "Text contrast is at least 4.5:1 against the opaque background.",
            color = if (lowContrast) UiTokens.Accent else UiTokens.Muted
        )
        TextButton(onClick = {
            VaultAppearancePrefs.reset(context)
            style = VaultAppearancePrefs.load(context)
        }) { Text("Reset to original appearance", color = UiTokens.Accent) }
    }
}

@Composable
private fun VaultSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onFinished: () -> Unit, onChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${value.toInt()}$unit", color = UiTokens.Ink)
        Slider(value, onChange, valueRange = range, onValueChangeFinished = onFinished)
    }
}

@Composable
private fun VaultAppearancePreview(style: VaultAppearance) {
    val context = LocalContext.current
    val title = context.getString(R.string.vault_widget_label)
    val status = context.getString(R.string.vault_widget_locked_status)
    val titleBitmap = remember(style, title) { VaultWidgetRendering.text(context, title, style, true, 220f).asImageBitmap() }
    val statusBitmap = remember(style, status) { VaultWidgetRendering.text(context, status, style, false, 220f).asImageBitmap() }
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 78.dp)
            .background(Color(style.backgroundArgb()), shape)
            .border(1.dp, Color(style.outlineArgb()), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = remember(context) {
            val size = (62 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            requireNotNull(context.getDrawable(R.drawable.clarity_suite_icon)).toBitmap(size, size).asImageBitmap()
        }
        Image(
            icon,
            null,
            Modifier.size(62.dp).padding(2.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Image(titleBitmap, title, alignment = Alignment.CenterStart)
            Image(statusBitmap, status, alignment = Alignment.CenterStart)
        }
    }
}
