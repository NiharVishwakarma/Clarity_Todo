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

/** Private copies of user-picked font files, shared by app typography and all font pickers/widgets. */
object LocalWidgetFonts {
    private const val PREFS = "widget_fonts"
    private const val KEY_FONTS = "imported_fonts"
    private const val MAX_FONT_BYTES = 20L * 1024L * 1024L
    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val catalog = ImportedFontCatalog { file, error ->
        android.util.Log.w("LocalWidgetFonts", "Could not inspect imported font ${file.name}", error)
    }
    // Parsed registry keyed by its stored JSON; the file-exists check still runs on every call.
    private var listRaw: String? = null
    private var listEntries: List<ImportedWidgetFont> = emptyList()

    @Synchronized
    fun list(context: Context): List<ImportedWidgetFont> = catalog.list(root(context), entries(context))

    private fun entries(context: Context): List<ImportedWidgetFont> {
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FONTS, "[]") ?: "[]"
        val entries = synchronized(this) {
            if (json != listRaw) {
                listEntries = runCatching {
                    val array = JSONArray(json)
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            val id = item.optString("id")
                            val name = item.optString("name")
                            if (id.matches(Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")) && name.isNotBlank()) add(ImportedWidgetFont(id, name))
                        }
                    }
                }.onFailure { android.util.Log.e("LocalWidgetFonts", "Could not read the font registry", it) }
                    .getOrDefault(emptyList())
                listRaw = json
            }
            listEntries
        }
        return entries
    }

    /** Copy, validate, then register a font selected through Android's document picker. */
    @Synchronized
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
            val hash = catalog.hash(temporary)
            val entries = entries(app)
            catalog.find(directory, entries, hash)?.let { return it }
            check(temporary.renameTo(destination)) { "Could not save the font in app storage." }
            synchronized(typefaceCache) { typefaceCache[id] = parsed }

            // Append to the original JSON, not the deduplicated picker view: retain legacy metadata/IDs.
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val array = JSONArray(prefs.getString(KEY_FONTS, "[]") ?: "[]")
            array.put(JSONObject().put("id", id).put("name", displayName))
            check(prefs.edit().putString(KEY_FONTS, array.toString()).commit()) { "Could not save the font registry." }
            listRaw = null
            return list(app).first { it.containsId(id) }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                android.util.Log.w("LocalWidgetFonts", "Could not remove temporary font ${temporary.name}")
            }
        }
    }

    /** Resolve an imported font in-process for Compose previews and app typography. */
    fun typeface(context: Context, fontId: String?): Typeface? {
        if (fontId == null) return null
        return synchronized(typefaceCache) {
            typefaceCache[fontId] ?: runCatching { Typeface.createFromFile(File(root(context), fontId)) }
                .onFailure { android.util.Log.w("LocalWidgetFonts", "Could not load imported font $fontId", it) }
                .getOrNull()?.also { typefaceCache[fontId] = it }
        }
    }

    private val textBitmaps = android.util.LruCache<String, Bitmap>(128)

    /** Render imported-font text to a bitmap in this process; Typeface itself cannot cross into a launcher process. */
    fun renderText(
        context: Context,
        text: String,
        fontId: String?,
        sizeSp: Float,
        color: Int,
        maxWidthDp: Float = 600f,
        align: Paint.Align = Paint.Align.LEFT,
        maxLines: Int = 1
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || fontId == null || text.isEmpty()) return null
        val metrics = context.resources.displayMetrics
        // Every input that affects the pixels is part of the key; callers never modify the bitmap.
        val key = "$fontId|$sizeSp|$color|$maxWidthDp|${align.name}|$maxLines|${metrics.density}|${metrics.scaledDensity}|$text"
        textBitmaps.get(key)?.let { return it }
        return renderTextUncached(context, text, fontId, sizeSp, color, maxWidthDp, align, maxLines)?.also { textBitmaps.put(key, it) }
    }

    private fun renderTextUncached(
        context: Context,
        text: String,
        fontId: String?,
        sizeSp: Float,
        color: Int,
        maxWidthDp: Float = 600f,
        align: Paint.Align = Paint.Align.LEFT,
        maxLines: Int = 1
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || fontId == null || text.isEmpty()) return null
        val typeface = typeface(context, fontId) ?: return null
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
        if (maxLines > 1) {
            // Word-wrapped layout; only the last permitted line is ellipsized.
            val layout = android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, android.text.TextPaint(paint), contentMaxWidthPx)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            val widest = (0 until layout.lineCount).maxOf { ceil(layout.getLineWidth(it)).toInt() }
            val wrappedWidth = (widest + padding * 2).coerceIn(1, maxWidthPx)
            val wrappedBitmap = Bitmap.createBitmap(wrappedWidth, layout.height + padding * 2, Bitmap.Config.ARGB_8888)
            wrappedBitmap.density = (context.resources.displayMetrics.densityDpi * scale / density).toInt()
            Canvas(wrappedBitmap).apply {
                translate(padding.toFloat(), padding.toFloat())
                layout.draw(this)
            }
            return wrappedBitmap
        }
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
