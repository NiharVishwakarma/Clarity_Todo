package com.example.darktodo.data

import android.content.Context
import com.example.darktodo.widget.VaultWidget

/** Global shortcut styling only; no vault contents or authentication preferences live here. */
data class VaultAppearance(
    val fontFamily: String = WidgetFontFamily.SANS_SERIF.key,
    val fontFileId: String? = null,
    val titleSizeSp: Float = 16f,
    val statusSizeSp: Float = 12f,
    val titleWeight: Int = 700,
    val statusWeight: Int = 400,
    val titleColor: String = "#F4F3F5",
    val statusColor: String = "#AAA8B0",
    val backgroundColor: String = "#09090C",
    val accentColor: String = "#F20D24",
    val backgroundOpacityPercent: Int = 100,
    val cornerRadiusDp: Int = 18
) {
    fun sanitized() = copy(
        fontFamily = WidgetFontFamily.fromStoredKey(fontFamily)?.key ?: DEFAULT.fontFamily,
        fontFileId = fontFileId?.takeIf { it.matches(Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")) },
        titleSizeSp = if (titleSizeSp.isFinite()) titleSizeSp.coerceIn(12f, 28f) else DEFAULT.titleSizeSp,
        statusSizeSp = if (statusSizeSp.isFinite()) statusSizeSp.coerceIn(10f, 22f) else DEFAULT.statusSizeSp,
        titleWeight = titleWeight.coerceIn(100, 900),
        statusWeight = statusWeight.coerceIn(100, 900),
        titleColor = StickerHex.normalize(titleColor) ?: DEFAULT.titleColor,
        statusColor = StickerHex.normalize(statusColor) ?: DEFAULT.statusColor,
        backgroundColor = StickerHex.normalize(backgroundColor) ?: DEFAULT.backgroundColor,
        accentColor = StickerHex.normalize(accentColor) ?: DEFAULT.accentColor,
        backgroundOpacityPercent = backgroundOpacityPercent.coerceIn(0, 100),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 40)
    )

    /** Alpha is used for the background layer only, never for artwork or lettering. */
    fun backgroundArgb(): Int =
        ((backgroundOpacityPercent.coerceIn(0, 100) * 255 + 50) / 100 shl 24) or
            (StickerHex.toArgb(backgroundColor) and 0xFFFFFF)

    fun outlineArgb(): Int =
        ((58 * backgroundOpacityPercent.coerceIn(0, 100) + 50) / 100 shl 24) or
            (StickerHex.toArgb(accentColor) and 0xFFFFFF)

    fun contrastRatio(text: String): Float {
        val ink = NoteContrast.luminance(text)
        val fill = NoteContrast.luminance(backgroundColor)
        return (maxOf(ink, fill) + .05f) / (minOf(ink, fill) + .05f)
    }

    companion object {
        val DEFAULT = VaultAppearance()
    }
}

object VaultAppearancePrefs {
    const val FILE = "vault_widget_appearance"

    fun load(context: Context): VaultAppearance {
        val values = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).all
        val defaults = VaultAppearance.DEFAULT
        fun number(key: String, default: Number) = values[key] as? Number ?: default
        fun text(key: String, default: String) = values[key] as? String ?: default
        val selectedFont = (values["font_file_id"] as? String)?.takeIf { id ->
            LocalWidgetFonts.list(context).any { it.id == id }
        }
        return VaultAppearance(
            fontFamily = text("font_family", defaults.fontFamily),
            fontFileId = selectedFont,
            titleSizeSp = number("title_size", defaults.titleSizeSp).toFloat(),
            statusSizeSp = number("status_size", defaults.statusSizeSp).toFloat(),
            titleWeight = number("title_weight", defaults.titleWeight).toInt(),
            statusWeight = number("status_weight", defaults.statusWeight).toInt(),
            titleColor = text("title_color", defaults.titleColor),
            statusColor = text("status_color", defaults.statusColor),
            backgroundColor = text("background_color", defaults.backgroundColor),
            accentColor = text("accent_color", defaults.accentColor),
            backgroundOpacityPercent = number("background_opacity", defaults.backgroundOpacityPercent).toInt(),
            cornerRadiusDp = number("corner_radius", defaults.cornerRadiusDp).toInt()
        ).sanitized()
    }

    fun save(context: Context, appearance: VaultAppearance) {
        val style = appearance.sanitized()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("font_family", style.fontFamily).putString("font_file_id", style.fontFileId)
            .putFloat("title_size", style.titleSizeSp).putFloat("status_size", style.statusSizeSp)
            .putInt("title_weight", style.titleWeight).putInt("status_weight", style.statusWeight)
            .putString("title_color", style.titleColor).putString("status_color", style.statusColor)
            .putString("background_color", style.backgroundColor)
            .putString("accent_color", style.accentColor)
            .putInt("background_opacity", style.backgroundOpacityPercent)
            .putInt("corner_radius", style.cornerRadiusDp).apply()
        VaultWidget.updateAll(context)
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        VaultWidget.updateAll(context)
    }
}
