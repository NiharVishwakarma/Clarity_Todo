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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.SystemMonitorAppearance
import com.example.darktodo.data.SystemMonitorAppearancePrefs
import com.example.darktodo.data.WidgetBackgrounds
import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.widget.SystemMonitorWidgetRendering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemMonitorAppearanceScreen(onBack: () -> Unit, onOpenWallpaper: (String) -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(SystemMonitorAppearancePrefs.load(context)) }
    var fonts by remember { mutableStateOf(LocalWidgetFonts.list(context)) }
    var book by remember { mutableStateOf(StickerColorBook()) }
    var importing by remember { mutableStateOf(false) }
    var fontsExpanded by rememberSettingsSectionExpanded()
    var colorsExpanded by rememberSettingsSectionExpanded()
    var backgroundExpanded by rememberSettingsSectionExpanded()
    val importScope = rememberCoroutineScope()

    fun update(value: SystemMonitorAppearance) {
        style = value.sanitized()
        SystemMonitorAppearancePrefs.save(context, style)
    }

    fun preview(value: SystemMonitorAppearance) {
        style = value.sanitized()
    }

    fun commitPreview() {
        SystemMonitorAppearancePrefs.save(context, style)
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
            Text("System Monitor Appearance", color = UiTokens.Ink, fontSize = 20.sp)
        }
        Text("Live preview · System Monitor widget", color = UiTokens.Muted)
        SystemMonitorAppearancePreview(style)
        Text("Sliders preview while dragging and save on release. Other changes save immediately. Appearance is local to this device.", color = UiTokens.Muted)

        CollapsibleSettingsSection(
            title = "Fonts",
            summary = "Font family · Size and weight",
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
            MonitorSlider("Font size (title)", style.titleSizeSp, 12f..22f, "sp", onFinished = ::commitPreview) { preview(style.copy(titleSizeSp = it)) }
            MonitorSlider("Font size (values)", style.valueSizeSp, 11f..22f, "sp", onFinished = ::commitPreview) { preview(style.copy(valueSizeSp = it)) }
            MonitorSlider("Font size (labels)", style.labelSizeSp, 9f..16f, "sp", onFinished = ::commitPreview) { preview(style.copy(labelSizeSp = it)) }
            MonitorSlider("Font size (secondary)", style.detailSizeSp, 8f..14f, "sp", onFinished = ::commitPreview) { preview(style.copy(detailSizeSp = it)) }
            MonitorSlider("Font weight (title)", style.titleWeight.toFloat(), 100f..900f, "", onFinished = ::commitPreview) { preview(style.copy(titleWeight = it.toInt())) }
            MonitorSlider("Font weight (values)", style.valueWeight.toFloat(), 100f..900f, "", onFinished = ::commitPreview) { preview(style.copy(valueWeight = it.toInt())) }
            Text("On Android 8, weights use normal/bold equivalents.", color = UiTokens.Muted)
        }

        CollapsibleSettingsSection(
            title = "Colors",
            summary = "Title · Values · Labels · Cards · Accent",
            expanded = colorsExpanded,
            onExpandedChange = { colorsExpanded = it },
            icon = ClarityIcons.Colors
        ) {
            HexColorField("Title color", style.titleColor, book, { book = it }) { update(style.copy(titleColor = it)) }
            HexColorField("Metric value color", style.valueColor, book, { book = it }) { update(style.copy(valueColor = it)) }
            HexColorField("Label color", style.labelColor, book, { book = it }) { update(style.copy(labelColor = it)) }
            HexColorField("Secondary text color", style.secondaryColor, book, { book = it }) { update(style.copy(secondaryColor = it)) }
            HexColorField("Accent / action color", style.accentColor, book, { book = it }) { update(style.copy(accentColor = it)) }
            HexColorField("Card background color", style.cardBackgroundColor, book, { book = it }) { update(style.copy(cardBackgroundColor = it)) }
            MonitorSlider("Card opacity", style.cardOpacityPercent.toFloat(), 0f..100f, "%", onFinished = ::commitPreview) {
                preview(style.copy(cardOpacityPercent = it.toInt()))
            }
        }

        CollapsibleSettingsSection(
            title = "Background & Wallpaper",
            summary = "Background color · Opacity · Wallpaper image",
            expanded = backgroundExpanded,
            onExpandedChange = { backgroundExpanded = it },
            icon = ClarityIcons.Background
        ) {
            HexColorField("Widget background color", style.backgroundColor, book, { book = it }) { update(style.copy(backgroundColor = it)) }
            MonitorSlider("Background opacity", style.backgroundOpacityPercent.toFloat(), 0f..100f, "%", onFinished = ::commitPreview) {
                preview(style.copy(backgroundOpacityPercent = it.toInt()))
            }
            val wpStatus = if (WidgetBackgrounds.has(context, WidgetBackgrounds.SYSTEM_MONITOR))
                "${WidgetBackgrounds.fade(context, WidgetBackgrounds.SYSTEM_MONITOR)}% fade" else "Not set"
            SettingsNavRow(ClarityIcons.Wallpapers, "Widget wallpaper image", "", wpStatus) {
                onOpenWallpaper(WidgetBackgrounds.SYSTEM_MONITOR)
            }
        }

        MonitorSlider("Widget corner radius", style.cornerRadiusDp.toFloat(), 0f..36f, "dp", onFinished = ::commitPreview) { preview(style.copy(cornerRadiusDp = it.toInt())) }
        MonitorSlider("Card corner radius", style.cardCornerRadiusDp.toFloat(), 0f..24f, "dp", onFinished = ::commitPreview) { preview(style.copy(cardCornerRadiusDp = it.toInt())) }

        TextButton(onClick = {
            SystemMonitorAppearancePrefs.reset(context)
            style = SystemMonitorAppearancePrefs.load(context)
        }) { Text("Reset to default appearance", color = UiTokens.Accent) }
    }
}

@Composable
private fun MonitorSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onFinished: () -> Unit, onChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${value.toInt()}$unit", color = UiTokens.Ink)
        Slider(value, onChange, valueRange = range, onValueChangeFinished = onFinished)
    }
}

@Composable
private fun SystemMonitorAppearancePreview(style: SystemMonitorAppearance) {
    val context = LocalContext.current
    val titleBitmap = remember(style) {
        SystemMonitorWidgetRendering.text(context, "System Monitor", style.titleSizeSp, style.titleColor, style, style.titleWeight, 180f).asImageBitmap()
    }
    val ramValBitmap = remember(style) {
        SystemMonitorWidgetRendering.text(context, "5.29 GiB", style.valueSizeSp, style.valueColor, style, style.valueWeight, 120f).asImageBitmap()
    }
    val ramLblBitmap = remember(style) {
        SystemMonitorWidgetRendering.text(context, "Available RAM", style.labelSizeSp, style.labelColor, style, 400, 120f).asImageBitmap()
    }
    val shape = RoundedCornerShape(style.cornerRadiusDp.dp)
    val cardShape = RoundedCornerShape(style.cardCornerRadiusDp.dp)

    Box(
        Modifier.fillMaxWidth().heightIn(min = 140.dp)
            .background(Color(style.backgroundArgb()), shape)
            .border(1.dp, Color(style.outlineArgb()), shape)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Image(titleBitmap, "System Monitor")
                Text("Refresh", color = Color(android.graphics.Color.parseColor(style.accentColor)), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).background(Color(style.cardBackgroundArgb()), cardShape).padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Image(ramLblBitmap, "Available RAM")
                        Image(ramValBitmap, "5.29 GiB")
                    }
                }
                Box(
                    Modifier.weight(1f).background(Color(style.cardBackgroundArgb()), cardShape).padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("App cache", color = Color(android.graphics.Color.parseColor(style.labelColor)), fontSize = style.labelSizeSp.sp)
                        Text("128 MB", color = Color(android.graphics.Color.parseColor(style.valueColor)), fontSize = style.valueSizeSp.sp)
                    }
                }
            }
        }
    }
}
