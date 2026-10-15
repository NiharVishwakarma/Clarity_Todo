package com.example.darktodo.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ImportedWidgetFont(val id: String, val name: String)

/** Private copies of user-picked font files, shared by both home-screen widgets. */
object LocalWidgetFonts {
    private const val PREFS = "widget_fonts"
    private const val KEY_FONTS = "imported_fonts"
    private const val MAX_FONT_BYTES = 20L * 1024L * 1024L
    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun list(context: Context): List<ImportedWidgetFont> {
        val root = root(context)
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FONTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.matches(Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")) && name.isNotBlank() && File(root, id).isFile) {
                        add(ImportedWidgetFont(id, name))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Copy, validate, then register a font selected through Android's document picker. */
    fun import(context: Context, uri: Uri): ImportedWidgetFont {
        val app = context.applicationContext
        val displayName = displayName(app, uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("ttf", "otf", "ttc")) { "Choose a .ttf, .otf, or .ttc font file." }

        val directory = root(app)
        check(directory.exists() || directory.mkdirs()) { "Could not create the app's font folder." }
        val id = "${UUID.randomUUID()}.$extension"
        val temporary = File(directory, "$id.tmp")
        val destination = File(directory, id)
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: error("Android could not open that font file.")
            input.use { source ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_FONT_BYTES) { "Font files must be 20 MB or smaller." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0L) { "The selected font file is empty." }
                }
            }
            val parsed = Typeface.Builder(temporary).build()
            check(parsed != null) { "Android couldn't read this font file." }
            check(temporary.renameTo(destination)) { "Could not save the font in app storage." }
            synchronized(typefaceCache) { typefaceCache[id] = parsed }

            val entries = list(app).toMutableList().apply { add(0, ImportedWidgetFont(id, displayName)) }
            val array = JSONArray()
            entries.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_FONTS, array.toString()).apply()
            return ImportedWidgetFont(id, displayName)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    /** TypefaceSpan is parcelable, allowing the selected Typeface to travel with RemoteViews text. */
    fun styledText(context: Context, text: String, fontId: String?): CharSequence {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || fontId == null || text.isEmpty()) return text
        val typeface = synchronized(typefaceCache) {
            typefaceCache[fontId] ?: runCatching { Typeface.createFromFile(File(root(context), fontId)) }
                .getOrNull()?.also { typefaceCache[fontId] = it }
        } ?: return text
        return SpannableString(text).apply {
            setSpan(TypefaceSpan(typeface), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return (fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Imported font")
            .replace(Regex("[\\r\\n]"), " ").take(120)
    }

    private fun root(context: Context) = File(context.applicationContext.filesDir, "widget-fonts")
}
