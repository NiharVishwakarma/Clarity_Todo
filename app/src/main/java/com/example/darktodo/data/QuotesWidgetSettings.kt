package com.example.darktodo.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

enum class QuoteBackground(val label: String) {
    Solid("Solid color"), Gradient("Custom gradient"), Automatic("Automatic gradient")
}

enum class QuoteRotation(val label: String, val scheduled: Boolean, val tapToRefresh: Boolean) {
    Manual("Manual refresh", false, true),
    Scheduled("Scheduled rotation", true, false),
    Both("Manual and scheduled", true, true)
}

enum class QuoteAlignment { Left, Center, Right }

data class QuotesWidgetSettings(
    val topics: Set<String> = setOf(QuoteLibrary.ALL_TOPICS),
    val includeUnverified: Boolean = false,
    val showAuthor: Boolean = true,
    val showSource: Boolean = false,
    val rotation: QuoteRotation = QuoteRotation.Manual,
    val intervalMinutes: Int = 60,
    val backgroundStyle: QuoteBackground = QuoteBackground.Solid,
    val background: String = "#19191E",
    val gradientEnd: String = "#4A1523",
    val textColor: String = "#F5F5F7",
    val authorColor: String = "#C9C9D0",
    val fontFamily: String = "sans-serif",
    val fontFileId: String? = null,
    val fontSizeSp: Float = 20f,
    val fontWeight: Int = 400,
    val alignment: QuoteAlignment = QuoteAlignment.Left,
    val cornerRadiusDp: Float = 24f,
    val paddingDp: Float = 20f,
    val border: Boolean = true,
    val shadow: Boolean = false,
    val decoration: Boolean = true
) {
    fun sanitized(): QuotesWidgetSettings = copy(
        topics = topics.ifEmpty { setOf(QuoteLibrary.ALL_TOPICS) },
        intervalMinutes = intervalMinutes.coerceIn(15, 10080),
        background = StickerHex.normalize(background) ?: "#19191E",
        gradientEnd = StickerHex.normalize(gradientEnd) ?: "#4A1523",
        textColor = StickerHex.normalize(textColor) ?: "#F5F5F7",
        authorColor = StickerHex.normalize(authorColor) ?: "#C9C9D0",
        fontFamily = WidgetFontFamily.fromStoredKey(fontFamily)?.key ?: "sans-serif",
        fontSizeSp = fontSizeSp.takeIf { it.isFinite() }?.coerceIn(12f, 36f) ?: 20f,
        fontWeight = fontWeight.coerceIn(100, 900),
        cornerRadiusDp = cornerRadiusDp.takeIf { it.isFinite() }?.coerceIn(0f, 48f) ?: 24f,
        paddingDp = paddingDp.takeIf { it.isFinite() }?.coerceIn(8f, 40f) ?: 20f
    )

    fun toJson(): String = JSONObject().apply {
        put("topics", JSONArray(topics.sorted()))
        put("includeUnverified", includeUnverified)
        put("showAuthor", showAuthor); put("showSource", showSource)
        put("rotation", rotation.name); put("intervalMinutes", intervalMinutes)
        put("backgroundStyle", backgroundStyle.name)
        put("background", background); put("gradientEnd", gradientEnd)
        put("textColor", textColor); put("authorColor", authorColor)
        put("fontFamily", fontFamily); put("fontFileId", fontFileId ?: JSONObject.NULL)
        put("fontSizeSp", fontSizeSp); put("fontWeight", fontWeight)
        put("alignment", alignment.name); put("cornerRadiusDp", cornerRadiusDp)
        put("paddingDp", paddingDp); put("border", border)
        put("shadow", shadow); put("decoration", decoration)
    }.toString()

    companion object {
        fun fromJson(json: String): QuotesWidgetSettings {
            val o = JSONObject(json)
            val defaults = QuotesWidgetSettings()
            val array = o.optJSONArray("topics")
            return QuotesWidgetSettings(
                topics = array?.let { (0 until it.length()).map { index -> it.getString(index) }.toSet() } ?: defaults.topics,
                includeUnverified = o.optBoolean("includeUnverified", false),
                showAuthor = o.optBoolean("showAuthor", true),
                showSource = o.optBoolean("showSource", false),
                rotation = QuoteRotation.entries.firstOrNull { it.name == o.optString("rotation") } ?: defaults.rotation,
                intervalMinutes = o.optInt("intervalMinutes", 60),
                backgroundStyle = QuoteBackground.entries.firstOrNull { it.name == o.optString("backgroundStyle") } ?: defaults.backgroundStyle,
                background = o.optString("background", defaults.background),
                gradientEnd = o.optString("gradientEnd", defaults.gradientEnd),
                textColor = o.optString("textColor", defaults.textColor),
                authorColor = o.optString("authorColor", defaults.authorColor),
                fontFamily = o.optString("fontFamily", defaults.fontFamily),
                fontFileId = if (o.isNull("fontFileId")) null else o.optString("fontFileId").takeIf { it.isNotBlank() },
                fontSizeSp = o.optDouble("fontSizeSp", 20.0).toFloat(),
                fontWeight = o.optInt("fontWeight", 400),
                alignment = QuoteAlignment.entries.firstOrNull { it.name == o.optString("alignment") } ?: defaults.alignment,
                cornerRadiusDp = o.optDouble("cornerRadiusDp", 24.0).toFloat(),
                paddingDp = o.optDouble("paddingDp", 20.0).toFloat(),
                border = o.optBoolean("border", true),
                shadow = o.optBoolean("shadow", false),
                decoration = o.optBoolean("decoration", true)
            ).sanitized()
        }
    }
}

object QuotesWidgetPrefs {
    const val PREFS = "quotes_widget_settings"

    fun load(context: Context): QuotesWidgetSettings {
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("settings", null) ?: return QuotesWidgetSettings()
        return try {
            QuotesWidgetSettings.fromJson(json)
        } catch (error: JSONException) {
            Log.e("QuotesWidgetPrefs", "Invalid quote widget settings; using defaults", error)
            QuotesWidgetSettings()
        }
    }

    fun save(context: Context, settings: QuotesWidgetSettings) {
        check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("settings", settings.sanitized().toJson()).commit()) {
            "Could not save Quotes Widget settings."
        }
    }
}

data class WidgetQuotePool(val quotes: List<ClarityQuote>, val usedTopicFallback: Boolean)
data class WidgetQuoteChoice(val quote: ClarityQuote, val seenIds: Set<String>)

object WidgetQuoteSelection {
    // Aliases expose familiar individual topics without relabeling the bundled collection.
    private val aliases = mapOf(
        "motivation" to "discipline", "philosophy" to "stoicism",
        "productivity" to "creativity", "life" to "wisdom"
    )
    val topics = listOf(QuoteTopic(QuoteLibrary.ALL_TOPICS, "All Topics"),
        QuoteTopic("motivation", "Motivation"), QuoteTopic("discipline", "Discipline"),
        QuoteTopic("philosophy", "Philosophy"), QuoteTopic("stoicism", "Stoicism"),
        QuoteTopic("success", "Success"), QuoteTopic("productivity", "Productivity"),
        QuoteTopic("life", "Life"), QuoteTopic("happiness", "Happiness")) +
        QuoteLibrary.topics.filterNot { it.id in setOf("discipline", "stoicism", "success", "happiness") }

    fun pool(quotes: List<ClarityQuote>, settings: QuotesWidgetSettings): WidgetQuotePool {
        val allowed = quotes.filter { settings.includeUnverified || it.authorVerified }
        val topics = settings.topics.map { aliases[it] ?: it }.toSet()
        val matching = allowed.filter { topics.isEmpty() || QuoteLibrary.ALL_TOPICS in topics || it.topic in topics }
        return WidgetQuotePool(matching.ifEmpty { allowed }, matching.isEmpty() && allowed.isNotEmpty())
    }

    fun next(pool: List<ClarityQuote>, seen: Set<String>, currentId: String?, random: Random = Random.Default): WidgetQuoteChoice? {
        if (pool.isEmpty()) return null
        val availableIds = pool.map { it.id }.toSet()
        val validSeen = seen.intersect(availableIds)
        val unseen = pool.filterNot { it.id in validSeen }
        val cycle = if (unseen.isEmpty()) emptySet() else validSeen
        val candidates = unseen.ifEmpty { pool }
        val quote = candidates.filterNot { it.id == currentId }.ifEmpty { candidates }.random(random)
        return WidgetQuoteChoice(quote, cycle + quote.id)
    }
}

data class QuotesWidgetPalette(val start: Int, val end: Int, val ink: Int, val authorInk: Int) {
    companion object {
        fun resolve(settings: QuotesWidgetSettings, quoteId: String): QuotesWidgetPalette {
            val automatic = listOf("#19191E" to "#4A1523", "#121C24" to "#243B48", "#201822" to "#48253B")
            val colors = when (settings.backgroundStyle) {
                QuoteBackground.Solid -> settings.background to settings.background
                QuoteBackground.Gradient -> settings.background to settings.gradientEnd
                QuoteBackground.Automatic -> automatic[(quoteId.hashCode() and Int.MAX_VALUE) % automatic.size]
            }
            var start = StickerHex.toArgb(colors.first)
            var end = StickerHex.toArgb(colors.second)
            val white = 0xFFFFFFFF.toInt()
            val black = 0xFF000000.toInt()
            fun luminance(color: Int) = NoteContrast.relativeLuminance(
                (color shr 16) and 255, (color shr 8) and 255, color and 255
            ).toDouble()
            fun bounds(): Pair<Double, Double> {
                // Channel minima bound every point; convex sRGB luminance peaks at an endpoint.
                val lower = NoteContrast.relativeLuminance(
                    minOf((start shr 16) and 255, (end shr 16) and 255),
                    minOf((start shr 8) and 255, (end shr 8) and 255),
                    minOf(start and 255, end and 255)
                ).toDouble()
                return lower to maxOf(luminance(start), luminance(end))
            }
            fun contrast(color: Int): Double {
                val (low, high) = bounds()
                val ink = luminance(color)
                return when {
                    ink < low -> (low + .05) / (ink + .05)
                    ink > high -> (ink + .05) / (high + .05)
                    else -> 1.0
                }
            }
            if (maxOf(contrast(white), contrast(black)) < 4.5) {
                // Some gradients have no readable single ink; darken without replacing their hue.
                fun scrim(color: Int): Int = black or
                    ((((color shr 16) and 255) * .28).toInt() shl 16) or
                    ((((color shr 8) and 255) * .28).toInt() shl 8) or
                    ((color and 255) * .28).toInt()
                start = scrim(start); end = scrim(end)
            }
            fun accessible(hex: String): Int {
                val requested = StickerHex.toArgb(hex)
                if (contrast(requested) >= 4.5) return requested
                return if (contrast(white) >= contrast(black)) white else black
            }
            return QuotesWidgetPalette(start, end, accessible(settings.textColor), accessible(settings.authorColor))
        }
    }
}
