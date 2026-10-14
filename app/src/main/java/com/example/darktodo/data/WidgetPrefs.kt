package com.example.darktodo.data

import android.content.Context
import android.graphics.Color as AndroidColor

data class WidgetPrefs(
    val incomplete: String = "#FF0000",
    val complete: String = "#00C853",
    val countPanel: String = "#FF0000",
    val widgetBackground: String = "#171717",
    val taskCard: String = "#171717",
    val taskTitle: String = "#F5F5F5",
    val fadePercent: Int = 45,
    val fontFamily: String = "googlesans",
    val fontItalic: Boolean = false,
    val taskTitleSizeSp: Float = 16f
)

object WidgetSettings {
    private const val PREFS = "todo_store"
    private const val PREFIX = "widget_setting_"
    private val defaults = WidgetPrefs()

    fun load(context: Context): WidgetPrefs {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetPrefs(
            incomplete = p.getString(PREFIX + "incomplete", defaults.incomplete)!!,
            complete = p.getString(PREFIX + "complete", defaults.complete)!!,
            countPanel = p.getString(PREFIX + "count_panel", defaults.countPanel)!!,
            widgetBackground = p.getString(PREFIX + "background", defaults.widgetBackground)!!,
            taskCard = p.getString(PREFIX + "task_card", defaults.taskCard)!!,
            taskTitle = p.getString(PREFIX + "task_title", defaults.taskTitle)!!,
            fadePercent = p.getInt(PREFIX + "fade", defaults.fadePercent).coerceIn(0, 100),
            fontFamily = p.getString(PREFIX + "font_family", defaults.fontFamily).orEmpty().takeIf { it in FONT_FAMILIES } ?: defaults.fontFamily,
            fontItalic = p.getBoolean(PREFIX + "font_italic", defaults.fontItalic),
            taskTitleSizeSp = p.getFloat(PREFIX + "task_title_size_sp", defaults.taskTitleSizeSp).coerceIn(12f, 24f)
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

    fun saveFontFamily(context: Context, value: String) {
        if (value !in FONT_FAMILIES) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREFIX + "font_family", value).apply()
    }

    fun saveFontItalic(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREFIX + "font_italic", value).apply()
    }

    fun saveTaskTitleSize(context: Context, value: Float) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(PREFIX + "task_title_size_sp", value.coerceIn(12f, 24f)).apply()
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            listOf("incomplete", "complete", "count_panel", "background", "task_card", "task_title", "fade", "font_weight", "font_family", "font_italic", "task_title_size_sp").forEach { remove(PREFIX + it) }
        }.apply()
    }
}

private val FONT_FAMILIES = setOf("opensans", "googlesans", "montserrat", "notosans", "ubuntu", "nunito")
