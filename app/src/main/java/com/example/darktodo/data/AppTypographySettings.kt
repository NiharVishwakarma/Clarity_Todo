package com.example.darktodo.data

import android.content.Context

data class AppFontSettings(
    val family: WidgetFontFamily = WidgetFontFamily.SANS_SERIF,
    val fontFileId: String? = null
)

/** App UI typography is stored separately from both widget preference stores. */
object AppTypographySettings {
    private const val FILE = "app_typography"
    private const val FAMILY = "font_family"
    private const val FONT_FILE_ID = "font_file_id"

    fun load(context: Context): AppFontSettings {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val family = WidgetFontFamily.fromStoredKey(prefs.getString(FAMILY, null)) ?: WidgetFontFamily.SANS_SERIF
        val savedFontId = prefs.getString(FONT_FILE_ID, null)
        val fontFileId = savedFontId?.takeIf { id -> LocalWidgetFonts.list(context).any { it.id == id } }
        return AppFontSettings(family = family, fontFileId = fontFileId)
    }

    fun saveFamily(context: Context, family: WidgetFontFamily) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(FAMILY, family.key)
            .remove(FONT_FILE_ID)
            .apply()
    }

    fun saveImportedFont(context: Context, fontId: String) {
        if (LocalWidgetFonts.list(context).none { it.id == fontId }) return
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(FONT_FILE_ID, fontId)
            .apply()
    }
}
