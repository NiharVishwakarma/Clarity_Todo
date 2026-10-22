package com.example.darktodo.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuoteBackground
import com.example.darktodo.data.QuoteGradientDirection
import com.example.darktodo.data.QuoteLibrary
import com.example.darktodo.data.QuoteRotation
import com.example.darktodo.data.QuotesWidgetPrefs
import com.example.darktodo.data.QuotesWidgetSettings
import com.example.darktodo.data.StickerColorBook
import com.example.darktodo.data.WidgetFontFamily
import com.example.darktodo.data.WidgetQuoteSelection
import com.example.darktodo.widget.QuotesWidgetRendering
import com.example.darktodo.widget.StickerWidget
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QuotesWidgetSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedJson by rememberSaveable { mutableStateOf(QuotesWidgetPrefs.load(context).toJson()) }
    var draftJson by rememberSaveable { mutableStateOf(savedJson) }
    val settings = remember(draftJson) { QuotesWidgetSettings.fromJson(draftJson) }
    val library = remember { QuoteLibrary.load(context) }
    val fonts = remember { LocalWidgetFonts.list(context) }
    val pool = remember(settings.topics, settings.includeUnverified) { WidgetQuoteSelection.pool(library, settings) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val quote = pool.quotes.firstOrNull { it.id == previewId } ?: pool.quotes.firstOrNull()
    var previewSize by rememberSaveable { mutableStateOf(1) }
    val sizes = listOf(300 to 70, 300 to 160, 220 to 220)
    val (width, height) = sizes[previewSize]
    val fontScale = LocalConfiguration.current.fontScale
    val bitmap = remember(settings, quote, width, height, fontScale) {
        QuotesWidgetRendering.render(context, quote, settings, width, height, pool.usedTopicFallback)
    }
    var book by remember { mutableStateOf(StickerColorBook()) }
    var contentExpanded by rememberSettingsSectionExpanded()
    var backgroundExpanded by rememberSettingsSectionExpanded()
    var cardExpanded by rememberSettingsSectionExpanded()
    var fontsExpanded by rememberSettingsSectionExpanded()
    var layoutExpanded by rememberSettingsSectionExpanded()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun update(value: QuotesWidgetSettings) { draftJson = value.sanitized().toJson() }
    fun action(save: Boolean) {
        val pending = settings
        busy = true
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    if (save) StickerWidget.applyQuoteSettings(context.applicationContext, pending)
                    else StickerWidget.refreshQuotes(context.applicationContext)
                    StickerWidget.quoteIds(context.applicationContext).size
                }
                if (save) savedJson = pending.toJson()
                status = if (save) "Saved. Updated $count quote widget(s)." else "Refreshed $count quote widget(s) using saved settings."
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                Log.e("QuotesWidgetSettings", "Cannot read offline quote data", error)
                status = "Could not read offline quotes. Please try again."
            } catch (error: RuntimeException) {
                Log.e("QuotesWidgetSettings", "Cannot save or refresh quote widgets", error)
                status = "Could not complete save/refresh: ${error.message ?: "Please try again."}"
            } finally {
                busy = false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = UiTokens.Accent) }
            Text("Quotes Widget", color = UiTokens.Ink, fontSize = 22.sp)
        }
        Text("Customize your home-screen quote widget. Add Clarity Quotes from your launcher's widget picker. Existing Random quotes widgets are also updated; note widgets are not changed.", color = UiTokens.Muted)
        Text("LIVE PREVIEW", color = UiTokens.Muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Compact", "Wide", "Square").forEachIndexed { index, label ->
                FilterChip(selected = previewSize == index, onClick = { previewSize = index }, label = { Text(label) },
                    colors = quoteChipColors())
            }
        }
        Surface(color = UiTokens.SurfaceRaised) {
            Image(
                bitmap.asImageBitmap(),
                QuotesWidgetRendering.description(quote, settings, pool.usedTopicFallback),
                Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)
            )
        }
        Text("Same renderer as the launcher widget. Transparency is shown over this preview surface, not your wallpaper.", color = UiTokens.Muted)
        if (pool.usedTopicFallback) Text("No offline matches for these topics. Showing all eligible topics instead.", color = UiTokens.Accent)
        if (pool.quotes.isEmpty()) Text("No eligible quotes. Allow unverified authors or reset the filters.", color = UiTokens.Accent)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                previewId = WidgetQuoteSelection.next(pool.quotes, emptySet(), quote?.id)?.quote?.id
            }) { Text("Next preview", color = UiTokens.Accent) }
            TextButton(onClick = { action(false) }, enabled = !busy) { Text("Refresh widgets", color = UiTokens.Accent) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                update(QuotesWidgetSettings())
                status = "Defaults loaded in preview. Apply Changes to reset all quote widgets."
            }, enabled = !busy) { Text("Reset to defaults", color = UiTokens.Accent) }
        }
        Button(onClick = { action(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Accent)) {
            Text(if (busy) "Working..." else "Apply Changes")
        }
        if (draftJson != savedJson) Text("Unsaved preview changes", color = UiTokens.Accent)
        status?.let { Text(it, color = UiTokens.Muted) }

        CollapsibleSettingsSection("Quote Content", "Topics, attribution, refresh schedule",
            contentExpanded, { contentExpanded = it }, ClarityIcons.Quotes) {
            Text("Select multiple topics. Motivation/Discipline, Philosophy/Stoicism, Productivity/Focus and Life/Wisdom share bundled categories.", color = UiTokens.Muted)
            WidgetQuoteSelection.topics.chunked(2).forEach { row ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { topic ->
                        FilterChip(topic.id in settings.topics,
                            { update(settings.copy(topics = QuoteLibrary.toggleTopic(settings.topics, topic.id))) },
                            label = { Text(topic.label) }, colors = quoteChipColors())
                    }
                }
                }
            }
            QuoteToggle("Include unverified / unknown authors", settings.includeUnverified) { update(settings.copy(includeUnverified = it)) }
            Text("Verified here means a named author with a bundled source citation; not an external verification service.", color = UiTokens.Muted)
            QuoteToggle("Show author", settings.showAuthor) { update(settings.copy(showAuthor = it)) }
            QuoteToggle("Show source when available", settings.showSource) { update(settings.copy(showSource = it)) }
            QuoteToggle("Automatic rotation", settings.rotation.scheduled) {
                update(settings.copy(rotation = if (it) QuoteRotation.Both else QuoteRotation.Manual))
            }
            QuoteChoices("Rotation interval", listOf("15 min", "30 min", "1 hour", "3 hours", "6 hours", "12 hours", "24 hours"),
                listOf(15, 30, 60, 180, 360, 720, 1440).indexOf(settings.intervalMinutes),
                enabled = settings.rotation.scheduled) {
                update(settings.copy(intervalMinutes = listOf(15, 30, 60, 180, 360, 720, 1440)[it]))
            }
            if (settings.rotation.scheduled) {
                QuoteToggle("Also refresh when the widget is tapped", settings.rotation.tapToRefresh) {
                    update(settings.copy(rotation = if (it) QuoteRotation.Both else QuoteRotation.Scheduled))
                }
                Text("Approximate timing, 15 minutes minimum. Android battery restrictions and Doze may delay rotation. Force-stop pauses work until Clarity is reopened.", color = UiTokens.Muted)
            }
            Text("Manual/Both: tap the widget for another quote. Scheduled only: tapping opens Clarity. Refresh widgets always works with the last saved settings.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection("Widget Background", "Outer background, gradient, opacity and insets",
            backgroundExpanded, { backgroundExpanded = it }, ClarityIcons.Background) {
            QuoteChoices("Background type", QuoteBackground.entries.map { it.label }, settings.outerBackgroundStyle.ordinal) {
                update(settings.copy(outerBackgroundStyle = QuoteBackground.entries[it]))
            }
            if (settings.outerBackgroundStyle == QuoteBackground.Solid || settings.outerBackgroundStyle == QuoteBackground.Gradient) {
                HexColorField("Background primary color", settings.outerBackground, book, { book = it }) { update(settings.copy(outerBackground = it)) }
            }
            if (settings.outerBackgroundStyle == QuoteBackground.Gradient) {
                HexColorField("Background secondary color", settings.outerGradientEnd, book, { book = it }) { update(settings.copy(outerGradientEnd = it)) }
            }
            if (settings.outerBackgroundStyle == QuoteBackground.Gradient || settings.outerBackgroundStyle == QuoteBackground.Automatic) {
                QuoteChoices("Background gradient direction", QuoteGradientDirection.entries.map { it.label }, settings.outerGradientDirection.ordinal) {
                    update(settings.copy(outerGradientDirection = QuoteGradientDirection.entries[it]))
                }
            }
            if (settings.outerBackgroundStyle != QuoteBackground.Transparent) {
                QuoteSlider("Background opacity (%)", settings.outerOpacityPercent.toFloat(), 0f..100f) { update(settings.copy(outerOpacityPercent = it.toInt())) }
            }
            QuoteSlider("Background corner radius (dp)", settings.outerCornerRadiusDp, 0f..48f) { update(settings.copy(outerCornerRadiusDp = it)) }
            QuoteSlider("Background internal padding (dp)", settings.outerPaddingDp, 0f..32f) { update(settings.copy(outerPaddingDp = it)) }
            QuoteToggle("Background border", settings.outerBorder) { update(settings.copy(outerBorder = it)) }
            if (settings.outerBorder) {
                HexColorField("Background border color", settings.outerBorderColor, book, { book = it }) { update(settings.copy(outerBorderColor = it)) }
                QuoteSlider("Background border thickness (dp)", settings.outerBorderWidthDp, .5f..6f) { update(settings.copy(outerBorderWidthDp = it)) }
            }
            Text("Increase background padding to reveal the outer layer around the card. Compact widgets limit effective padding to keep text readable.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection("Quote Card", "Independent card colors, corners, padding and border",
            cardExpanded, { cardExpanded = it }, ClarityIcons.Colors) {
            QuoteChoices("Card background type", QuoteBackground.entries.map { it.label }, settings.backgroundStyle.ordinal) {
                update(settings.copy(backgroundStyle = QuoteBackground.entries[it]))
            }
            if (settings.backgroundStyle == QuoteBackground.Solid || settings.backgroundStyle == QuoteBackground.Gradient) {
                HexColorField("Card primary color", settings.background, book, { book = it }) { update(settings.copy(background = it)) }
            }
            if (settings.backgroundStyle == QuoteBackground.Gradient) {
                HexColorField("Card secondary color", settings.gradientEnd, book, { book = it }) { update(settings.copy(gradientEnd = it)) }
            }
            if (settings.backgroundStyle == QuoteBackground.Gradient || settings.backgroundStyle == QuoteBackground.Automatic) {
                QuoteChoices("Card gradient direction", QuoteGradientDirection.entries.map { it.label }, settings.gradientDirection.ordinal) {
                    update(settings.copy(gradientDirection = QuoteGradientDirection.entries[it]))
                }
            }
            if (settings.backgroundStyle != QuoteBackground.Transparent) {
                QuoteSlider("Card opacity (%)", settings.cardOpacityPercent.toFloat(), 0f..100f) { update(settings.copy(cardOpacityPercent = it.toInt())) }
            }
            HexColorField("Quote text color", settings.textColor, book, { book = it }) { update(settings.copy(textColor = it)) }
            HexColorField("Author / source color", settings.authorColor, book, { book = it }) { update(settings.copy(authorColor = it)) }
            QuoteSlider("Card corner radius (dp)", settings.cornerRadiusDp, 0f..48f) { update(settings.copy(cornerRadiusDp = it)) }
            QuoteSlider("Card internal padding (dp)", settings.paddingDp, 8f..40f) { update(settings.copy(paddingDp = it)) }
            QuoteToggle("Card border", settings.border) { update(settings.copy(border = it)) }
            if (settings.border) {
                HexColorField("Border color", settings.borderColor, book, { book = it }) { update(settings.copy(borderColor = it)) }
                QuoteSlider("Border thickness (dp)", settings.borderWidthDp, .5f..6f) { update(settings.copy(borderWidthDp = it)) }
            }
            QuoteToggle("Soft card shadow", settings.shadow) { update(settings.copy(shadow = it)) }
            Text("Opaque cards correct unreadable ink to at least 4.5:1 contrast and may darken extreme gradients. Transparent/translucent cards use your selected ink; contrast depends on your wallpaper. Automatic gradients follow the quote.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection("Typography", "Font family, size and weight",
            fontsExpanded, { fontsExpanded = it }, ClarityIcons.Fonts) {
            QuoteChoices("Font family", WidgetFontFamily.entries.map { it.label },
                if (settings.fontFileId == null) WidgetFontFamily.entries.indexOfFirst { it.key == settings.fontFamily } else -1) {
                update(settings.copy(fontFamily = WidgetFontFamily.entries[it].key, fontFileId = null))
            }
            fonts.forEach { font ->
                TextButton(onClick = { update(settings.copy(fontFileId = font.id)) }) {
                    Text((if (font.containsId(settings.fontFileId)) "Selected: " else "") + font.name, color = UiTokens.Accent)
                }
            }
            Text("Uses system and already imported app fonts. On Android 8, weights map to normal/bold.", color = UiTokens.Muted)
            QuoteSlider("Quote size (sp)", settings.fontSizeSp, 12f..36f) { update(settings.copy(fontSizeSp = it)) }
            Text("Line spacing: ${"%.2f".format(settings.lineSpacing)}x", color = UiTokens.Ink)
            Slider(settings.lineSpacing, { update(settings.copy(lineSpacing = it)) }, valueRange = 1f..1.8f,
                colors = SliderDefaults.colors(thumbColor = UiTokens.Accent, activeTrackColor = UiTokens.Accent))
            QuoteChoices("Font weight", listOf("Light", "Normal", "Medium", "Bold"), listOf(300, 400, 500, 700).indexOf(settings.fontWeight)) {
                update(settings.copy(fontWeight = listOf(300, 400, 500, 700)[it]))
        }
        CollapsibleSettingsSection("Layout and decoration", "Alignment and quotation marks",
            layoutExpanded, { layoutExpanded = it }, ClarityIcons.Layout) {
            QuoteChoices("Text alignment", QuoteAlignment.entries.map { it.name }, settings.alignment.ordinal) {
                update(settings.copy(alignment = QuoteAlignment.entries[it]))
            }
            QuoteToggle("Quotation marks", settings.decoration) { update(settings.copy(decoration = it)) }
            Text("Quotes shrink to fit, then ellipsize rather than overlap the author. Compact cells reduce effective padding and may shorten the author or omit source/fallback labels. Launcher insets and corner masks remain system-controlled.", color = UiTokens.Muted)
        }
    }
}

@Composable
private fun QuoteToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = UiTokens.Ink, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = UiTokens.Accent,
            uncheckedThumbColor = UiTokens.Muted, uncheckedTrackColor = UiTokens.SurfaceRaised,
            uncheckedBorderColor = UiTokens.Subdued
        ))
    }
}

@Composable
private fun QuoteChoices(label: String, choices: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Text(label, color = UiTokens.Ink)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { index, choice ->
            FilterChip(index == selected, { onSelect(index) }, enabled = enabled, label = { Text(choice) },
                colors = quoteChipColors())
        }
    }
}

@Composable
private fun QuoteSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label: ${"%.1f".format(value)}", color = UiTokens.Ink)
    Slider(value, onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = UiTokens.Accent, activeTrackColor = UiTokens.Accent))
}

@Composable
private fun quoteChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = UiTokens.SurfaceRaised,
    labelColor = UiTokens.Ink,
    selectedContainerColor = Color(0xFF3D141C),
    selectedLabelColor = UiTokens.Ink,
    disabledLabelColor = UiTokens.Muted
)
