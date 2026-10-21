package com.example.darktodo.data

import android.content.Context
import com.example.darktodo.widget.SystemMonitorWidget

/**
 * Customization preferences for Clarity System Monitor widget.
 * Follows the same design framework as Vault, Calendar, and To-Do widgets.
 */
data class SystemMonitorAppearance(
    val fontFamily: String = WidgetFontFamily.SANS_SERIF.key,
    val fontFileId: String? = null,
    val titleSizeSp: Float = 15f,
    val labelSizeSp: Float = 11f,
    val valueSizeSp: Float = 14f,
    val detailSizeSp: Float = 10f,
    val titleWeight: Int = 700,
    val valueWeight: Int = 700,
    val titleColor: String = "#FFFFFF",
    val labelColor: String = "#B9BBC8",
    val valueColor: String = "#FFFFFF",
    val secondaryColor: String = "#DDDEE5",
    val accentColor: String = "#FF8A96",
    val backgroundColor: String = "#1A1A20",
    val cardBackgroundColor: String = "#25252D",
    val backgroundOpacityPercent: Int = 95,
    val cardOpacityPercent: Int = 90,
    val cornerRadiusDp: Int = 20,
    val cardCornerRadiusDp: Int = 12
) {
    fun sanitized() = copy(
        fontFamily = WidgetFontFamily.fromStoredKey(fontFamily)?.key ?: DEFAULT.fontFamily,
        fontFileId = fontFileId?.takeIf { it.matches(Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")) },
        titleSizeSp = if (titleSizeSp.isFinite()) titleSizeSp.coerceIn(12f, 22f) else DEFAULT.titleSizeSp,
        labelSizeSp = if (labelSizeSp.isFinite()) labelSizeSp.coerceIn(9f, 16f) else DEFAULT.labelSizeSp,
        valueSizeSp = if (valueSizeSp.isFinite()) valueSizeSp.coerceIn(11f, 22f) else DEFAULT.valueSizeSp,
        detailSizeSp = if (detailSizeSp.isFinite()) detailSizeSp.coerceIn(8f, 14f) else DEFAULT.detailSizeSp,
        titleWeight = titleWeight.coerceIn(100, 900),
        valueWeight = valueWeight.coerceIn(100, 900),
        titleColor = StickerHex.normalize(titleColor) ?: DEFAULT.titleColor,
        labelColor = StickerHex.normalize(labelColor) ?: DEFAULT.labelColor,
        valueColor = StickerHex.normalize(valueColor) ?: DEFAULT.valueColor,
        secondaryColor = StickerHex.normalize(secondaryColor) ?: DEFAULT.secondaryColor,
        accentColor = StickerHex.normalize(accentColor) ?: DEFAULT.accentColor,
        backgroundColor = StickerHex.normalize(backgroundColor) ?: DEFAULT.backgroundColor,
        cardBackgroundColor = StickerHex.normalize(cardBackgroundColor) ?: DEFAULT.cardBackgroundColor,
        backgroundOpacityPercent = backgroundOpacityPercent.coerceIn(0, 100),
        cardOpacityPercent = cardOpacityPercent.coerceIn(0, 100),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 36),
        cardCornerRadiusDp = cardCornerRadiusDp.coerceIn(0, 24)
    )

    fun backgroundArgb(): Int =
        ((backgroundOpacityPercent.coerceIn(0, 100) * 255 + 50) / 100 shl 24) or
            (StickerHex.toArgb(backgroundColor) and 0xFFFFFF)

    fun outlineArgb(): Int =
        ((40 * backgroundOpacityPercent.coerceIn(0, 100) + 50) / 100 shl 24) or
            (StickerHex.toArgb("#454550") and 0xFFFFFF)

    fun cardBackgroundArgb(): Int =
        ((cardOpacityPercent.coerceIn(0, 100) * 255 + 50) / 100 shl 24) or
            (StickerHex.toArgb(cardBackgroundColor) and 0xFFFFFF)

    companion object {
        val DEFAULT = SystemMonitorAppearance()
    }
}

object SystemMonitorAppearancePrefs {
    const val FILE = "system_monitor_widget_appearance"

    fun load(context: Context): SystemMonitorAppearance {
        val values = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).all
        val defaults = SystemMonitorAppearance.DEFAULT
        fun number(key: String, default: Number) = values[key] as? Number ?: default
        fun text(key: String, default: String) = values[key] as? String ?: default
        val selectedFont = (values["font_file_id"] as? String)?.takeIf { id ->
            LocalWidgetFonts.list(context).any { it.containsId(id) }
        }
        return SystemMonitorAppearance(
            fontFamily = text("font_family", defaults.fontFamily),
            fontFileId = selectedFont,
            titleSizeSp = number("title_size", defaults.titleSizeSp).toFloat(),
            labelSizeSp = number("label_size", defaults.labelSizeSp).toFloat(),
            valueSizeSp = number("value_size", defaults.valueSizeSp).toFloat(),
            detailSizeSp = number("detail_size", defaults.detailSizeSp).toFloat(),
            titleWeight = number("title_weight", defaults.titleWeight).toInt(),
            valueWeight = number("value_weight", defaults.valueWeight).toInt(),
            titleColor = text("title_color", defaults.titleColor),
            labelColor = text("label_color", defaults.labelColor),
            valueColor = text("value_color", defaults.valueColor),
            secondaryColor = text("secondary_color", defaults.secondaryColor),
            accentColor = text("accent_color", defaults.accentColor),
            backgroundColor = text("background_color", defaults.backgroundColor),
            cardBackgroundColor = text("card_background_color", defaults.cardBackgroundColor),
            backgroundOpacityPercent = number("background_opacity", defaults.backgroundOpacityPercent).toInt(),
            cardOpacityPercent = number("card_opacity", defaults.cardOpacityPercent).toInt(),
            cornerRadiusDp = number("corner_radius", defaults.cornerRadiusDp).toInt(),
            cardCornerRadiusDp = number("card_corner_radius", defaults.cardCornerRadiusDp).toInt()
        ).sanitized()
    }

    fun save(context: Context, appearance: SystemMonitorAppearance) {
        val style = appearance.sanitized()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("font_family", style.fontFamily).putString("font_file_id", style.fontFileId)
            .putFloat("title_size", style.titleSizeSp).putFloat("label_size", style.labelSizeSp)
            .putFloat("value_size", style.valueSizeSp).putFloat("detail_size", style.detailSizeSp)
            .putInt("title_weight", style.titleWeight).putInt("value_weight", style.valueWeight)
            .putString("title_color", style.titleColor).putString("label_color", style.labelColor)
            .putString("value_color", style.valueColor).putString("secondary_color", style.secondaryColor)
            .putString("accent_color", style.accentColor).putString("background_color", style.backgroundColor)
            .putString("card_background_color", style.cardBackgroundColor)
            .putInt("background_opacity", style.backgroundOpacityPercent)
            .putInt("card_opacity", style.cardOpacityPercent)
            .putInt("corner_radius", style.cornerRadiusDp)
            .putInt("card_corner_radius", style.cardCornerRadiusDp).apply()
        SystemMonitorWidget.renderAll(context)
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        SystemMonitorWidget.renderAll(context)
    }
}
