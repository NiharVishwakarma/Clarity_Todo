package com.example.darktodo.data

import org.json.JSONArray
import org.json.JSONObject

enum class StickerWidgetSize(val label: String, val minHeightDp: Int) {
    Small("Small", 96),
    Medium("Medium", 140),
    Large("Large", 200),
    Auto("Auto", 0);

    companion object {
        fun fromName(name: String?): StickerWidgetSize = entries.firstOrNull { it.name == name } ?: Auto
    }
}

data class NoteTextStyle(
    val color: String = "#FFFFFF",
    val automatic: Boolean = true,
    val fontFamily: String = WidgetFontFamily.SANS_SERIF.key,
    val fontFileId: String? = null,
    val sizeSp: Float = 13f,
    val weight: Int = 400,
    val italic: Boolean = false,
    val alignment: String = "start",
    val lineHeight: Float = 1.3f
) {
    fun sanitized() = copy(
        color = StickerHex.normalize(color) ?: "#FFFFFF",
        fontFamily = WidgetFontFamily.fromKey(fontFamily)?.key ?: WidgetFontFamily.SANS_SERIF.key,
        fontFileId = fontFileId?.takeIf { it.matches(Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")) },
        sizeSp = sizeSp.takeIf { it.isFinite() }?.coerceIn(10f, 34f) ?: 13f,
        weight = weight.coerceIn(300, 800),
        alignment = alignment.takeIf { it in listOf("start", "center", "end") } ?: "start",
        lineHeight = lineHeight.takeIf { it.isFinite() }?.coerceIn(1f, 2f) ?: 1.3f
    )

    fun toJson(): JSONObject = JSONObject().put("color", color).put("automatic", automatic)
        .put("fontFamily", fontFamily).put("fontFileId", fontFileId ?: JSONObject.NULL)
        .put("sizeSp", sizeSp.toDouble()).put("weight", weight).put("italic", italic)
        .put("alignment", alignment).put("lineHeight", lineHeight.toDouble())

    companion object {
        fun fromJson(json: JSONObject?): NoteTextStyle? = json?.let {
            NoteTextStyle(
                color = it.optString("color", "#FFFFFF"), automatic = it.optBoolean("automatic", true),
                fontFamily = it.optString("fontFamily", WidgetFontFamily.SANS_SERIF.key),
                fontFileId = if (it.isNull("fontFileId")) null else it.optString("fontFileId").ifBlank { null },
                sizeSp = it.optDouble("sizeSp", 13.0).toFloat(), weight = it.optInt("weight", 400),
                italic = it.optBoolean("italic", false), alignment = it.optString("alignment", "start"),
                lineHeight = it.optDouble("lineHeight", 1.3).toFloat()
            ).sanitized()
        }
    }
}

/** Full look of a note. Colours are `#RRGGBB` strings so users can type, paste and copy them. */
data class StickerStyle(
    val background: String = "#FFE680",
    val text: String = "#3B3200",
    val fontFamily: String = WidgetFontFamily.SANS_SERIF.key,
    val fontSizeSp: Float = 13f,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.3f,
    val textScalePercent: Int = 100,
    val cornerRadiusDp: Int = 16,
    val paddingDp: Int = 16,
    val marginDp: Int = 0,
    val opacityPercent: Int = 100,
    val elevationDp: Int = 0,
    val widgetSize: StickerWidgetSize = StickerWidgetSize.Auto,
    val fontFileId: String? = null,
    val borderWidthDp: Int = 0,
    val borderColor: String = "#000000",
    val autoContrast: Boolean = true,
    val textShadow: Boolean = false,
    val glow: Boolean = false,
    val titleStyle: NoteTextStyle? = null,
    val bodyStyle: NoteTextStyle? = null
) {
    fun titleText(): NoteTextStyle = titleStyle ?: legacyText().copy(
        sizeSp = fontSizeSp * 1.2f, weight = (fontWeight + 200).coerceAtMost(900))
    fun bodyText(): NoteTextStyle = bodyStyle ?: legacyText()
    private fun legacyText() = NoteTextStyle(text, autoContrast, fontFamily, fontFileId, fontSizeSp,
        fontWeight, lineHeight = lineHeight)

    /** Stored values are clamped so a hand-edited or old file can never produce an unreadable sticker. */
    fun sanitized() = copy(
        background = StickerHex.normalize(background) ?: DEFAULT.background,
        text = StickerHex.normalize(text) ?: DEFAULT.text,
        fontFamily = WidgetFontFamily.fromKey(fontFamily)?.key ?: DEFAULT.fontFamily,
        fontSizeSp = fontSizeSp.takeIf { it.isFinite() }?.coerceIn(10f, 28f) ?: DEFAULT.fontSizeSp,
        fontWeight = fontWeight.coerceIn(300, 800),
        lineHeight = lineHeight.takeIf { it.isFinite() }?.coerceIn(1f, 2f) ?: DEFAULT.lineHeight,
        textScalePercent = textScalePercent.coerceIn(80, 150),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 32),
        paddingDp = paddingDp.coerceIn(4, 32),
        marginDp = marginDp.coerceIn(0, 24),
        opacityPercent = opacityPercent.coerceIn(30, 100),
        elevationDp = elevationDp.coerceIn(0, 16),
        borderWidthDp = borderWidthDp.coerceIn(0, 12),
        borderColor = StickerHex.normalize(borderColor) ?: DEFAULT.borderColor,
        titleStyle = titleStyle?.sanitized(),
        bodyStyle = bodyStyle?.sanitized()
    )

    fun toJson(): JSONObject = JSONObject().put("background", background).put("text", text)
        .put("fontFamily", fontFamily).put("fontSizeSp", fontSizeSp.toDouble()).put("fontWeight", fontWeight)
        .put("lineHeight", lineHeight.toDouble()).put("textScale", textScalePercent).put("radius", cornerRadiusDp)
        .put("padding", paddingDp).put("margin", marginDp).put("opacity", opacityPercent)
        .put("elevation", elevationDp).put("widgetSize", widgetSize.name)
        .put("fontFileId", fontFileId ?: JSONObject.NULL).put("borderWidth", borderWidthDp).put("borderColor", borderColor)
        .put("autoContrast", autoContrast).put("textShadow", textShadow).put("glow", glow)
        .put("titleStyle", titleStyle?.toJson() ?: JSONObject.NULL)
        .put("bodyStyle", bodyStyle?.toJson() ?: JSONObject.NULL)

    companion object {
        val DEFAULT = StickerStyle()

        fun fromJson(o: JSONObject?): StickerStyle? {
            if (o == null) return null
            return StickerStyle(
                background = o.optString("background", DEFAULT.background),
                text = o.optString("text", DEFAULT.text),
                fontFamily = o.optString("fontFamily", DEFAULT.fontFamily),
                fontSizeSp = o.optDouble("fontSizeSp", DEFAULT.fontSizeSp.toDouble()).toFloat(),
                fontWeight = o.optInt("fontWeight", DEFAULT.fontWeight),
                lineHeight = o.optDouble("lineHeight", DEFAULT.lineHeight.toDouble()).toFloat(),
                textScalePercent = o.optInt("textScale", DEFAULT.textScalePercent),
                cornerRadiusDp = o.optInt("radius", DEFAULT.cornerRadiusDp),
                paddingDp = o.optInt("padding", DEFAULT.paddingDp),
                marginDp = o.optInt("margin", DEFAULT.marginDp),
                opacityPercent = o.optInt("opacity", DEFAULT.opacityPercent),
                elevationDp = o.optInt("elevation", DEFAULT.elevationDp),
                widgetSize = StickerWidgetSize.fromName(o.optString("widgetSize")),
                fontFileId = if (o.isNull("fontFileId")) null else o.optString("fontFileId").ifBlank { null },
                borderWidthDp = o.optInt("borderWidth", DEFAULT.borderWidthDp),
                borderColor = o.optString("borderColor", DEFAULT.borderColor),
                // Notes saved before these options existed keep their exact look.
                autoContrast = o.optBoolean("autoContrast", false),
                textShadow = o.optBoolean("textShadow", false),
                glow = o.optBoolean("glow", false),
                titleStyle = NoteTextStyle.fromJson(o.optJSONObject("titleStyle")),
                bodyStyle = NoteTextStyle.fromJson(o.optJSONObject("bodyStyle"))
            ).sanitized()
        }

        /** Style of a note that has none of its own yet (older notes): the former global style plus its preset colours. */
        fun resolve(sticker: Sticker, global: StickerStyle): StickerStyle {
            sticker.style?.let { return it }
            val globalCustomised = global.background != DEFAULT.background || global.text != DEFAULT.text
            return if (globalCustomised) global
            else global.copy(background = hex(sticker.color.background), text = hex(sticker.color.ink))
        }

        private fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)
    }
}

/** Picks readable text for a background from its relative luminance (0 = black, 1 = white). */
object NoteContrast {
    const val DARK_INK = "#000000"
    const val LIGHT_INK = "#FFFFFF"

    private fun linear(channel: Int): Float {
        val c = channel / 255f
        return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

    fun relativeLuminance(r: Int, g: Int, b: Int): Float = 0.2126f * linear(r) + 0.7152f * linear(g) + 0.0722f * linear(b)

    fun luminance(hex: String): Float {
        val argb = StickerHex.toArgb(hex)
        return relativeLuminance((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }

    /** A black overlay of [fadePercent] darkens every channel, which scales linear luminance by roughly (1 - fade)^2.2. */
    fun withFade(luminance: Float, fadePercent: Int): Float =
        luminance * Math.pow((1f - fadePercent.coerceIn(0, 100) / 100f).toDouble(), 2.2).toFloat()

    fun ratio(first: Float, second: Float): Float =
        (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)

    fun worstRatio(ink: String, backgrounds: ClosedFloatingPointRange<Float>): Float {
        val foreground = luminance(ink)
        return ratio(foreground, foreground.coerceIn(backgrounds.start, backgrounds.endInclusive))
    }

    fun inkFor(luminance: Float): String = recommended(luminance..luminance)

    fun recommended(backgrounds: ClosedFloatingPointRange<Float>): String =
        if (worstRatio(DARK_INK, backgrounds) > worstRatio(LIGHT_INK, backgrounds)) DARK_INK else LIGHT_INK
}

object StickerHex {
    private val pattern = Regex("^#?([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})$")

    /** Accepts pasted values such as `ffd700`, `#FFD`, or ` #c4b5fd `; returns `#RRGGBB` or null. */
    fun normalize(input: String?): String? {
        val match = pattern.matchEntire(input?.trim().orEmpty()) ?: return null
        var digits = match.groupValues[1].uppercase()
        if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")
        return "#$digits"
    }

    fun toArgb(hex: String): Int = 0xFF000000.toInt() or (normalize(hex) ?: "#000000").substring(1).toInt(16)
}

/** Recent and favourite colours shared by every sticker colour field. */
data class StickerColorBook(val recents: List<String> = emptyList(), val favorites: List<String> = emptyList()) {
    fun withRecent(hex: String): StickerColorBook {
        val value = StickerHex.normalize(hex) ?: return this
        return copy(recents = (listOf(value) + recents.filterNot { it == value }).take(MAX_RECENTS))
    }

    fun toggleFavorite(hex: String): StickerColorBook {
        val value = StickerHex.normalize(hex) ?: return this
        return copy(favorites = if (value in favorites) favorites - value else (favorites + value).takeLast(MAX_FAVORITES))
    }

    fun encode(): String = JSONObject().put("recents", JSONArray(recents)).put("favorites", JSONArray(favorites)).toString()

    companion object {
        const val MAX_RECENTS = 8
        const val MAX_FAVORITES = 16

        fun decode(json: String?): StickerColorBook = runCatching {
            val o = JSONObject(json ?: return StickerColorBook())
            fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { StickerHex.normalize(a.optString(it)) } }.orEmpty()
            StickerColorBook(list("recents").take(MAX_RECENTS), list("favorites").take(MAX_FAVORITES))
        }.getOrDefault(StickerColorBook())
    }
}
