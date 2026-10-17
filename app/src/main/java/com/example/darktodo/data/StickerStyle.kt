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

/** Full look of a sticker. Colours are `#RRGGBB` strings so users can type, paste and copy them. */
data class StickerStyle(
    val background: String = "#FFE680",
    val text: String = "#3B3200",
    val pin: String = "#3B3200",
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
    val widgetSize: StickerWidgetSize = StickerWidgetSize.Auto
) {
    /** Stored values are clamped so a hand-edited or old file can never produce an unreadable sticker. */
    fun sanitized() = copy(
        background = StickerHex.normalize(background) ?: DEFAULT.background,
        text = StickerHex.normalize(text) ?: DEFAULT.text,
        pin = StickerHex.normalize(pin) ?: DEFAULT.pin,
        fontFamily = WidgetFontFamily.fromKey(fontFamily)?.key ?: DEFAULT.fontFamily,
        fontSizeSp = fontSizeSp.coerceIn(10f, 28f),
        fontWeight = fontWeight.coerceIn(300, 800),
        lineHeight = lineHeight.coerceIn(1f, 2f),
        textScalePercent = textScalePercent.coerceIn(80, 150),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 32),
        paddingDp = paddingDp.coerceIn(4, 32),
        marginDp = marginDp.coerceIn(0, 24),
        opacityPercent = opacityPercent.coerceIn(30, 100),
        elevationDp = elevationDp.coerceIn(0, 16)
    )

    fun toJson(): JSONObject = JSONObject().put("background", background).put("text", text).put("pin", pin)
        .put("fontFamily", fontFamily).put("fontSizeSp", fontSizeSp.toDouble()).put("fontWeight", fontWeight)
        .put("lineHeight", lineHeight.toDouble()).put("textScale", textScalePercent).put("radius", cornerRadiusDp)
        .put("padding", paddingDp).put("margin", marginDp).put("opacity", opacityPercent)
        .put("elevation", elevationDp).put("widgetSize", widgetSize.name)

    companion object {
        val DEFAULT = StickerStyle()

        fun fromJson(o: JSONObject?): StickerStyle? {
            if (o == null) return null
            return StickerStyle(
                background = o.optString("background", DEFAULT.background),
                text = o.optString("text", DEFAULT.text),
                pin = o.optString("pin", DEFAULT.pin),
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
                widgetSize = StickerWidgetSize.fromName(o.optString("widgetSize"))
            ).sanitized()
        }

        /** Style used for a sticker that has no override: global typography/layout plus its own preset colours. */
        fun resolve(sticker: Sticker, global: StickerStyle): StickerStyle =
            sticker.style ?: global.copy(background = hex(sticker.color.background), text = hex(sticker.color.ink), pin = hex(sticker.color.ink))

        private fun hex(argb: Long) = "#%06X".format(argb and 0xFFFFFF)
    }
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
