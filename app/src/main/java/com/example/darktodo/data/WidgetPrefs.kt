package com.example.darktodo.data

import android.content.Context
import android.graphics.Color as AndroidColor
import kotlin.math.roundToInt

data class WidgetPrefs(
    val incomplete: String = "#FF0000",
    val complete: String = "#00C853",
    val countPanel: String = "#FF0000",
    val widgetBackground: String = "#171717",
    val taskCard: String = "#171717",
    val taskTitle: String = "#F5F5F5",
    val fadePercent: Int = 45,
    val fontFamily: WidgetFontFamily = WidgetFontFamily.SANS_SERIF,
    val fontFileId: String? = null,
    val taskTitleSizeSp: Float = 16f,
    val paddingDp: Int = 8,
    val rowSpacingDp: Int = 2,
    val taskCardTransparencyPercent: Int = 0,
    val backgroundDepth: Boolean = false
) {
    val taskCardAlpha: Int
        get() = (255 * (100 - taskCardTransparencyPercent.coerceIn(0, 100)) / 100f).roundToInt()
}
object WidgetSettings {
    private const val PREFS = "todo_store"
    private const val PREFIX = "widget_setting_"
    private val defaults = WidgetPrefs()

    fun load(context: Context): WidgetPrefs {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedFamily = WidgetFontFamily.fromStoredKey(p.getString(PREFIX + "font_family", null)) ?: defaults.fontFamily
        val storedFontId = p.getString(PREFIX + "font_file_id", null)
        val fontFileId = storedFontId?.takeIf { id -> LocalWidgetFonts.list(context).any { it.id == id } }
        val family = storedFamily
        if (p.contains(PREFIX + "font_style") || p.contains(PREFIX + "font_italic")) {
            p.edit().remove(PREFIX + "font_style").remove(PREFIX + "font_italic").apply()
        }
        return WidgetPrefs(
            incomplete = p.getString(PREFIX + "incomplete", defaults.incomplete)!!,
            complete = p.getString(PREFIX + "complete", defaults.complete)!!,
            countPanel = p.getString(PREFIX + "count_panel", defaults.countPanel)!!,
            widgetBackground = p.getString(PREFIX + "background", defaults.widgetBackground)!!,
            taskCard = p.getString(PREFIX + "task_card", defaults.taskCard)!!,
            taskCardTransparencyPercent = p.getInt(PREFIX + "task_card_transparency", defaults.taskCardTransparencyPercent).coerceIn(0, 100),
            backgroundDepth = p.getBoolean(PREFIX + "background_depth", false),
            taskTitle = p.getString(PREFIX + "task_title", defaults.taskTitle)!!,
            fadePercent = p.getInt(PREFIX + "fade", defaults.fadePercent).coerceIn(0, 100),
            fontFamily = family,
            fontFileId = fontFileId,
            taskTitleSizeSp = p.getFloat(PREFIX + "task_title_size_sp", defaults.taskTitleSizeSp).coerceIn(12f, 24f),
            paddingDp = p.getInt(PREFIX + "padding_dp", defaults.paddingDp).coerceIn(0, 20),
            rowSpacingDp = p.getInt(PREFIX + "row_spacing_dp", defaults.rowSpacingDp).coerceIn(0, 12)
        )
    }

    fun saveColor(context: Context, key: String, value: String): Boolean {
        val normalized = value.trim().let { if (it.startsWith("#")) it else "#$it" }.uppercase()
        if (!normalized.matches(Regex("^#[0-9A-F]{6}$"))) return false
        try { AndroidColor.parseColor(normalized) } catch (_: IllegalArgumentException) { return false }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + key, normalized).apply()
        return true
    }

    fun saveFade(context: Context, value: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PREFIX + "fade", value.coerceIn(0, 100)).apply()
    }

    fun saveTaskCardTransparency(context: Context, value: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(PREFIX + "task_card_transparency", value.coerceIn(0, 100)).apply()
    }

    fun saveBackgroundDepth(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PREFIX + "background_depth", enabled).apply()
    }

    fun saveFontFamily(context: Context, family: WidgetFontFamily) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(PREFIX + "font_family", family.key)
            remove(PREFIX + "font_file_id")
        }.apply()
    }

    fun saveImportedFont(context: Context, fontId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + "font_file_id", fontId).apply()
    }

    fun saveTaskTitleSize(context: Context, value: Float) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(PREFIX + "task_title_size_sp", value.coerceIn(12f, 24f)).apply()
    }

    fun saveLayout(context: Context, paddingDp: Int, rowSpacingDp: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(PREFIX + "padding_dp", paddingDp.coerceIn(0, 20))
            .putInt(PREFIX + "row_spacing_dp", rowSpacingDp.coerceIn(0, 12)).apply()
    }

    fun saveAsDefault(context: Context) = StyleSnapshot.save(context, StyleSnapshot.TODO, PREFS, PREFIX)
    fun hasSavedDefault(context: Context) = StyleSnapshot.has(context, StyleSnapshot.TODO)
    fun restoreDefault(context: Context) = StyleSnapshot.restore(context, StyleSnapshot.TODO, PREFS, PREFIX)

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            listOf("incomplete", "complete", "count_panel", "background", "background_depth", "task_card", "task_card_transparency", "task_title", "fade", "font_weight", "font_family", "font_file_id", "font_italic", "font_style", "task_title_size_sp", "padding_dp", "row_spacing_dp").forEach { remove(PREFIX + it) }
        }.apply()
    }
}
