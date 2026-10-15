package com.example.darktodo.data

import android.content.Context
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.TextUtils
import java.io.File
import java.util.UUID
import kotlin.math.ceil
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

    /** Render imported-font text to a bitmap in this process; Typeface itself cannot cross into a launcher process. */
    fun renderText(
        context: Context,
        text: String,
        fontId: String?,
        sizeSp: Float,
        color: Int,
        maxWidthDp: Float = 600f,
        align: Paint.Align = Paint.Align.LEFT
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || fontId == null || text.isEmpty()) return null
        val typeface = synchronized(typefaceCache) {
            typefaceCache[fontId] ?: runCatching { Typeface.createFromFile(File(root(context), fontId)) }
                .getOrNull()?.also { typefaceCache[fontId] = it }
        } ?: return null
        val density = context.resources.displayMetrics.density
        val scale = minOf(density, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = sizeSp * context.resources.displayMetrics.scaledDensity * (scale / density)
            this.color = color
            textAlign = align
        }
        val maxWidthPx = (maxWidthDp * scale).toInt().coerceAtLeast(1)
        val padding = (2f * scale).toInt().coerceAtLeast(1)
        val contentMaxWidthPx = (maxWidthPx - padding * 2).coerceAtLeast(1)
        val measured = TextUtils.ellipsize(text, android.text.TextPaint(paint), contentMaxWidthPx.toFloat(), TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetrics
        val width = (ceil(paint.measureText(measured)).toInt() + padding * 2).coerceAtLeast(1).coerceAtMost(maxWidthPx)
        val height = ceil(metrics.descent - metrics.ascent).toInt().coerceAtLeast(1) + padding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.density = (context.resources.displayMetrics.densityDpi * scale / density).toInt()
        val x = when (align) {
            Paint.Align.LEFT -> padding.toFloat()
            Paint.Align.CENTER -> width / 2f
            Paint.Align.RIGHT -> width - padding.toFloat()
        }
        Canvas(bitmap).drawText(measured, x, padding - metrics.ascent, paint)
        return bitmap
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
