package com.example.darktodo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File
import kotlin.math.max

/**
 * Optional per-widget wallpapers. The edited image is stored as a private JPEG; the dark fade
 * overlay is applied when the widget bitmap is rendered so intensity can change without re-editing.
 */
object WidgetBackgrounds {
    const val TODO = "todo"
    const val CALENDAR = "calendar"
    const val SYSTEM_MONITOR = "system_monitor"
    const val STICKER = "sticker"
    const val QUOTES = "quotes"
    const val NOTE_PREFIX = "note_"

    /** Image key for one note; the id is made filename-safe. */
    fun noteKey(noteId: String): String = NOTE_PREFIX + noteId.replace(Regex("[^A-Za-z0-9-]"), "_")
    const val DEFAULT_FADE = 25
    const val MAX_SIDE_PX = 560
    val presets = listOf("Default" to DEFAULT_FADE, "Slightly faded" to 40, "Medium fade" to 60, "Strong fade" to 80)
    val bundledNames = listOf("Crimson dusk", "Midnight", "Carbon", "Ember")
    private const val PREFS = "widget_backgrounds"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    internal fun file(context: Context, key: String) = File(context.applicationContext.filesDir, "widget_background_$key.jpg")

    fun has(context: Context, key: String): Boolean = file(context, key).isFile

    private class CachedSource(val modified: Long, val length: Long, val bitmap: Bitmap) { var luminance: Float? = null }
    // A handful of decoded images is enough: widgets and the open Notes screen reuse the same few notes.
    private val sources = object : LinkedHashMap<String, CachedSource>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSource>?): Boolean = size > 8
    }

    private fun cachedSource(context: Context, key: String): CachedSource? {
        val file = file(context, key)
        if (!file.isFile) return null
        val modified = file.lastModified()
        val length = file.length()
        synchronized(sources) {
            sources[key]?.takeIf { it.modified == modified && it.length == length }?.let { return it }
            val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
            return CachedSource(modified, length, decoded).also { sources[key] = it }
        }
    }

    /** Decoded source image, reused until the saved file changes. Never recycle the returned bitmap. */
    private fun source(context: Context, key: String): Bitmap? = cachedSource(context, key)?.bitmap

    /** Same cached image for UI code (Compose previews). Never recycle it. */
    fun image(context: Context, key: String): Bitmap? = source(context, key)

    internal fun invalidateAfterRestore() {
        synchronized(sources) { sources.clear() }
    }

    /** Average relative luminance (0..1) of the saved image before any fade, or null when there is none. */
    fun averageLuminance(context: Context, key: String): Float? {
        val cached = cachedSource(context, key) ?: return null
        cached.luminance?.let { return it }
        val small = Bitmap.createScaledBitmap(cached.bitmap, 16, 16, true)
        val pixels = IntArray(16 * 16)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        if (small !== cached.bitmap) small.recycle()
        val value = pixels.map { NoteContrast.relativeLuminance(Color.red(it), Color.green(it), Color.blue(it)) }.average().toFloat()
        cached.luminance = value
        return value
    }

    /** Draws the image to cover [area] and then its dark fade, without any rounding; callers clip with their own shape. */
    fun drawCover(canvas: Canvas, context: Context, key: String, area: RectF): Boolean {
        val image = source(context, key) ?: return false
        val scale = max(area.width() / image.width, area.height() / image.height)
        canvas.save()
        canvas.clipRect(area)
        canvas.translate(area.centerX() - image.width * scale / 2f, area.centerY() - image.height * scale / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(image, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawRect(area, Paint().apply { color = Color.argb(fade(context, key) * 255 / 100, 0, 0, 0) })
        return true
    }

    /** Copies an image and its fade to another key (used when duplicating a note). */
    fun copy(context: Context, fromKey: String, toKey: String): Boolean {
        val from = file(context, fromKey)
        if (!from.isFile) return false
        val copied = runCatching { from.copyTo(file(context, toKey), overwrite = true) }.isSuccess
        if (copied) {
            synchronized(sources) { sources.remove(toKey) }
            saveFade(context, toKey, fade(context, fromKey))
            com.example.darktodo.backup.BackupScheduler.requestBackup(context)
        }
        return copied
    }
    fun fade(context: Context, key: String): Int = prefs(context).getInt("${key}_fade", DEFAULT_FADE).coerceIn(0, 100)
    fun saveFade(context: Context, key: String, percent: Int) { prefs(context).edit().putInt("${key}_fade", percent.coerceIn(0, 100)).apply() }

    fun save(context: Context, key: String, bitmap: Bitmap, fadePercent: Int): Boolean {
        val saved = runCatching { file(context, key).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }.getOrDefault(false)
        if (saved) { saveFade(context, key, fadePercent); com.example.darktodo.backup.BackupScheduler.requestBackup(context) }
        return saved
    }

    fun clear(context: Context, key: String) {
        file(context, key).delete()
        synchronized(sources) { sources.remove(key) }
        prefs(context).edit().remove("${key}_fade").apply()
        com.example.darktodo.backup.BackupScheduler.requestBackup(context)
    }

    /** Builds the rounded, faded bitmap for an ImageView background, or null when no wallpaper is set. */
    fun render(context: Context, key: String, widthDp: Int, heightDp: Int, radiusDp: Float): Bitmap? {
        val density = context.resources.displayMetrics.density
        return renderPx(context, key, widthDp * density, heightDp * density, radiusDp * density)
    }

    /** Same as [render] but sized in pixels; the result is downscaled when larger than [MAX_SIDE_PX]. */
    fun renderPx(context: Context, key: String, widthPx: Float, heightPx: Float, radiusPx: Float): Bitmap? {
        if (!has(context, key)) return null
        val source = source(context, key) ?: return null
        var width = widthPx
        var height = heightPx
        val shrink = max(width, height) / MAX_SIDE_PX
        if (shrink > 1f) { width /= shrink; height /= shrink }
        val w = width.toInt().coerceAtLeast(1)
        val h = height.toInt().coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        canvas.drawColor(Color.BLACK)
        val scale = max(w.toFloat() / source.width, h.toFloat() / source.height)
        canvas.save()
        canvas.translate((w - source.width * scale) / 2f, (h - source.height * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        canvas.drawColor(Color.argb(fade(context, key) * 255 / 100, 0, 0, 0))
        val rounded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val radius = radiusPx * (w / width)
        Canvas(rounded).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
        cropped.recycle()
        return rounded
    }

    /** Generated red-and-black backgrounds, so no image assets need to ship with the app. */
    fun bundled(index: Int, width: Int = 800, height: Int = 800): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val (from, to) = when (index) {
            0 -> 0xFF3A0008.toInt() to 0xFF050506.toInt()
            1 -> 0xFF0B0B16.toInt() to 0xFF000000.toInt()
            2 -> 0xFF26262B.toInt() to 0xFF050506.toInt()
            else -> 0xFF5A0A0F.toInt() to 0xFF0A0A0C.toInt()
        }
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        if (index == 0 || index == 3) {
            paint.shader = RadialGradient(width * 0.8f, height * 0.2f, max(width, height) * 0.6f, 0x66F20D24, 0x00F20D24, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        return bitmap
    }
}
