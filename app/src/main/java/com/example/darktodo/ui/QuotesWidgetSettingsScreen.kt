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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.UiTokens
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuoteBackground
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
        Text("Home-screen quotes only. Global settings for every Notes widget configured as Random quotes.", color = UiTokens.Muted)
        Text("Add the Clarity Notes widget on your home screen, then choose Random quotes. Notes do not need to be open.", color = UiTokens.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Compact", "Wide", "Square").forEachIndexed { index, label ->
                FilterChip(selected = previewSize == index, onClick = { previewSize = index }, label = { Text(label) })
            }
        }
        Image(
            bitmap.asImageBitmap(),
            QuotesWidgetRendering.description(quote, settings, pool.usedTopicFallback),
            Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height)
        )
        Text("Live preview uses the actual widget renderer. Resize the home-screen widget for more text.", color = UiTokens.Muted)
        if (pool.usedTopicFallback) Text("No offline matches for these topics. Showing all eligible topics instead.", color = UiTokens.Accent)
        if (pool.quotes.isEmpty()) Text("No eligible quotes. Allow unverified authors or reset the filters.", color = UiTokens.Accent)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = {
                previewId = WidgetQuoteSelection.next(pool.quotes, emptySet(), quote?.id)?.quote?.id
            }) { Text("Next preview", color = UiTokens.Accent) }
            TextButton(onClick = { action(false) }, enabled = !busy) { Text("Refresh widgets", color = UiTokens.Accent) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { action(true) }, enabled = !busy) {
                Text(if (busy) "Working..." else "Apply / Save", color = UiTokens.Accent)
            }
            TextButton(onClick = {
                update(QuotesWidgetSettings())
                status = "Defaults loaded in preview. Apply / Save to reset all quote widgets."
            }, enabled = !busy) { Text("Reset to defaults", color = UiTokens.Accent) }
        }
        if (draftJson != savedJson) Text("Unsaved preview changes", color = UiTokens.Accent)
        status?.let { Text(it, color = UiTokens.Muted) }

        CollapsibleSettingsSection("Quote content and rotation", "Topics, attribution, refresh schedule",
            contentExpanded, { contentExpanded = it }, ClarityIcons.Personalization) {
            Text("Select multiple topics. Motivation/Discipline, Philosophy/Stoicism, Productivity/Focus and Life/Wisdom share bundled categories.", color = UiTokens.Muted)
            WidgetQuoteSelection.topics.chunked(2).forEach { row ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { topic ->
                        FilterChip(topic.id in settings.topics,
                            { update(settings.copy(topics = QuoteLibrary.toggleTopic(settings.topics, topic.id))) },
                            label = { Text(topic.label) })
                    }
                }
            }
            QuoteToggle("Include unverified / unknown authors", settings.includeUnverified) { update(settings.copy(includeUnverified = it)) }
            Text("Verified here means a named author with a bundled source citation; not an external verification service.", color = UiTokens.Muted)
            QuoteToggle("Show author", settings.showAuthor) { update(settings.copy(showAuthor = it)) }
            QuoteToggle("Show source when available", settings.showSource) { update(settings.copy(showSource = it)) }
            QuoteChoices("Rotation mode", QuoteRotation.entries.map { it.label }, settings.rotation.ordinal) {
                update(settings.copy(rotation = QuoteRotation.entries[it]))
            }
            if (settings.rotation.scheduled) {
                QuoteChoices("Rotation interval", listOf("15 min", "30 min", "1 hour", "3 hours", "6 hours", "12 hours", "24 hours"),
                    listOf(15, 30, 60, 180, 360, 720, 1440).indexOf(settings.intervalMinutes)) {
                    update(settings.copy(intervalMinutes = listOf(15, 30, 60, 180, 360, 720, 1440)[it]))
                }
                Text("Approximate timing, 15 minutes minimum. Android battery restrictions and Doze may delay rotation. Force-stop pauses work until Clarity is reopened.", color = UiTokens.Muted)
            }
            Text("Manual/Both: tap the widget for another quote. Scheduled only: tapping opens Clarity. Refresh widgets always works with the last saved settings.", color = UiTokens.Muted)
        }
        CollapsibleSettingsSection("Background and colors", "Solid, gradient, text and author",
            backgroundExpanded, { backgroundExpanded = it }, ClarityIcons.Colors) {
            QuoteChoices("Background style", QuoteBackground.entries.map { it.label }, settings.backgroundStyle.ordinal) {
                update(settings.copy(backgroundStyle = QuoteBackground.entries[it]))
            }
            if (settings.backgroundStyle != QuoteBackground.Automatic) {
                HexColorField("Background / gradient start", settings.background, book, { book = it }) { update(settings.copy(background = it)) }
            }
            if (settings.backgroundStyle == QuoteBackground.Gradient) {
                HexColorField("Gradient end", settings.gradientEnd, book, { book = it }) { update(settings.copy(gradientEnd = it)) }
            }
            HexColorField("Quote text color", settings.textColor, book, { book = it }) { update(settings.copy(textColor = it)) }
            HexColorField("Author / source color", settings.authorColor, book, { book = it }) { update(settings.copy(authorColor = it)) }
            Text("Unreadable text colors are replaced with light/dark ink for at least 4.5:1 contrast. Extreme gradients receive a subtle dark scrim. Automatic gradients follow the quote.", color = UiTokens.Muted)
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
            QuoteChoices("Font weight", listOf("Light", "Normal", "Medium", "Bold"), listOf(300, 400, 500, 700).indexOf(settings.fontWeight)) {
                update(settings.copy(fontWeight = listOf(300, 400, 500, 700)[it]))
            }
        }
        CollapsibleSettingsSection("Layout and decoration", "Alignment, corners, padding, border and shadow",
            layoutExpanded, { layoutExpanded = it }, ClarityIcons.Personalization) {
            QuoteChoices("Text alignment", QuoteAlignment.entries.map { it.name }, settings.alignment.ordinal) {
                update(settings.copy(alignment = QuoteAlignment.entries[it]))
            }
            QuoteSlider("Corner radius (dp)", settings.cornerRadiusDp, 0f..48f) { update(settings.copy(cornerRadiusDp = it)) }
            QuoteSlider("Internal padding (dp)", settings.paddingDp, 8f..40f) { update(settings.copy(paddingDp = it)) }
            QuoteToggle("Subtle border", settings.border) { update(settings.copy(border = it)) }
            QuoteToggle("Soft shadow", settings.shadow) { update(settings.copy(shadow = it)) }
            QuoteToggle("Quotation marks", settings.decoration) { update(settings.copy(decoration = it)) }
            Text("Quotes shrink to fit, then ellipsize rather than overlap the author. Tiny cells may omit source/fallback labels. Launcher insets and corner masks remain system-controlled.", color = UiTokens.Muted)
        }
    }
}

@Composable
private fun QuoteToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = UiTokens.Ink, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun QuoteChoices(label: String, choices: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = UiTokens.Ink)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { index, choice ->
            FilterChip(index == selected, { onSelect(index) }, label = { Text(choice) })
        }
    }
}

@Composable
private fun QuoteSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label: ${value.toInt()}", color = UiTokens.Ink)
    Slider(value, onChange, valueRange = range)
}
