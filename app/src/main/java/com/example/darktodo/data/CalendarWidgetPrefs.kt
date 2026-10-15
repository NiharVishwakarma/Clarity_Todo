package com.example.darktodo.data

import android.content.Context
import android.graphics.Color

data class CalendarWidgetPrefs(
    val background: String = "#000000", val date: String = "#FFFFFF", val weekday: String = "#8A8A8A",
    val heading: String = "#FFFFFF", val selectedBackground: String = "#F20D24", val selectedText: String = "#FFFFFF",
    val completeDot: String = "#35D07F", val mixedDot: String = "#FF9F1C", val openDot: String = "#F20D24",
    val navigationArrows: String = "#F20D24",
    val cornerRadius: Int = 8, val dateSize: Int = 17, val weekdaySize: Int = 11, val headingSize: Int = 17,
    val rowSpacing: Int = 2, val showDots: Boolean = true,
    val fontFamily: String = "googlesans", val fontStyle: String = "regular"
) {
    /** Compatibility view for callers that used the previous binary setting. */
    @Deprecated("Use fontStyle to distinguish Regular, Bold, and Italic")
    val fontItalic: Boolean get() = fontStyle == "italic"
}

object CalendarWidgetSettings {
    private const val FILE = "calendar_widget_settings"
    private val defaults = CalendarWidgetPrefs()
    fun load(context: Context): CalendarWidgetPrefs {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        fun color(key: String, value: String) = p.getString(key, value) ?: value
        return CalendarWidgetPrefs(
            color("background", defaults.background), color("date", defaults.date), color("weekday", defaults.weekday),
            color("heading", defaults.heading), color("selected_background", defaults.selectedBackground), color("selected_text", defaults.selectedText),
            color("complete_dot", defaults.completeDot), color("mixed_dot", defaults.mixedDot), color("open_dot", defaults.openDot),
            color("navigation_arrows", defaults.navigationArrows),
            p.getInt("corner_radius", defaults.cornerRadius).let { value -> listOf(8, 16, 22, 32).minBy { kotlin.math.abs(it - value) } }, p.getInt("date_size", defaults.dateSize).coerceIn(12, 20),
            p.getInt("weekday_size", defaults.weekdaySize).coerceIn(8, 14), p.getInt("heading_size", defaults.headingSize).coerceIn(14, 24),
            p.getInt("row_spacing", defaults.rowSpacing).coerceIn(0, 8), p.getBoolean("show_dots", defaults.showDots),
            p.getString("font_family", defaults.fontFamily).orEmpty().takeIf { it in FONT_FAMILIES } ?: defaults.fontFamily,
            p.getString("font_style", null).orEmpty().takeIf { it in FONT_STYLES }
                ?: if (p.getBoolean("font_italic", false)) "italic" else defaults.fontStyle
        )
    }
    fun saveColor(context: Context, key: String, value: String): Boolean {
        val normalized = value.trim().let { if (it.startsWith("#")) it.uppercase() else "#${it.uppercase()}" }
        if (!normalized.matches(Regex("^#[0-9A-F]{6}$"))) return false
        return try { Color.parseColor(normalized); context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(key, normalized).apply(); true } catch (_: IllegalArgumentException) { false }
    }
    fun saveInt(context: Context, key: String, value: Int) {
        if (key == "corner_radius") {
            val snapped = listOf(8, 16, 22, 32).minBy { kotlin.math.abs(it - value) }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(key, snapped).apply()
        } else {
            val range = when (key) { "date_size" -> 12..20; "weekday_size" -> 8..14; "heading_size" -> 14..24; else -> 0..8 }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(key, value.coerceIn(range)).apply()
        }
    }
    fun saveDots(context: Context, enabled: Boolean) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("show_dots", enabled).apply() }
    fun saveFontFamily(context: Context, family: String) {
        if (family in FONT_FAMILIES) context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("font_family", family).apply()
    }
    fun saveFontStyle(context: Context, style: String) {
        if (style !in FONT_STYLES) return
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("font_style", style).remove("font_italic").apply()
    }
    fun reset(context: Context) {
        // Remove only this settings file's customization keys; calendar navigation state and
        // every To-Do preference/task remain untouched.
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

private val FONT_FAMILIES = setOf("opensans", "googlesans", "montserrat", "notosans", "ubuntu", "nunito")
private val FONT_STYLES = setOf("regular", "bold", "italic")
