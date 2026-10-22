package com.example.darktodo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class WallpaperStyle(val label: String) {
    SOLID("Solid"), LINEAR("Linear"), RADIAL("Radial"), MESH("Mesh"),
    ABSTRACT("Abstract"), WAVES("Waves"), GEOMETRIC("Geometric"),
    CONCENTRIC("Concentric"), NOISE("Noise"), AURORA("Aurora"),
    MINIMAL("Minimal"), PATTERN("Dots & lines");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: ABSTRACT
    }
}

data class WallpaperConfig(
    val colors: List<Int> = listOf(Color.RED, Color.rgb(98, 35, 184), Color.rgb(21, 21, 26)),
    val style: WallpaperStyle = WallpaperStyle.ABSTRACT,
    val seed: Long = 1L,
    val complexity: Int = 1,
    val brightness: Float = 1f,
    val texture: Float = 0.18f,
    val patternScale: Float = 0.5f,
    val gradientDirection: Int = 0
) {
    fun sanitized() = copy(
        colors = colors.take(3).ifEmpty { listOf(Color.DKGRAY) },
        complexity = complexity.coerceIn(0, 2),
        brightness = brightness.takeIf(Float::isFinite)?.coerceIn(0.35f, 1.5f) ?: 1f,
        texture = texture.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
        patternScale = patternScale.takeIf(Float::isFinite)?.coerceIn(0.1f, 1f) ?: 0.5f,
        gradientDirection = gradientDirection.coerceIn(0, 3)
    )
}

data class WallpaperPalette(val name: String, val colors: List<Int>)

object WallpaperPalettes {
    val all = listOf(
        WallpaperPalette("Midnight", listOf(Color.rgb(6, 12, 28), Color.rgb(42, 35, 89), Color.rgb(11, 16, 36))),
        WallpaperPalette("Crimson", listOf(Color.rgb(10, 4, 9), Color.rgb(242, 13, 36), Color.rgb(95, 4, 30))),
        WallpaperPalette("Cyberpunk", listOf(Color.rgb(11, 4, 30), Color.rgb(164, 23, 204), Color.rgb(0, 221, 220))),
        WallpaperPalette("Ocean", listOf(Color.rgb(4, 31, 56), Color.rgb(0, 128, 166), Color.rgb(50, 215, 190))),
        WallpaperPalette("Forest", listOf(Color.rgb(7, 28, 22), Color.rgb(38, 112, 75), Color.rgb(163, 186, 97))),
        WallpaperPalette("Sunset", listOf(Color.rgb(58, 18, 53), Color.rgb(244, 86, 95), Color.rgb(255, 180, 80))),
        WallpaperPalette("Purple Haze", listOf(Color.rgb(29, 9, 56), Color.rgb(114, 38, 169), Color.rgb(236, 89, 162))),
        WallpaperPalette("Monochrome", listOf(Color.rgb(12, 12, 16), Color.rgb(106, 106, 117), Color.rgb(230, 230, 236))),
        WallpaperPalette("Pastel", listOf(Color.rgb(242, 181, 205), Color.rgb(168, 195, 245), Color.rgb(255, 227, 184)))
    )
}

data class SavedWallpaper(val id: String, val style: WallpaperStyle, val seed: Long, val createdAt: Long, val config: WallpaperConfig)

object WallpaperStudioStore {
    private const val TAG = "WallpaperStudioStore"
    private const val PREFS = "wallpaper_studio"
    private const val CONFIG = "current_config"
    private const val SAVED = "saved_wallpapers"
    private const val TARGET = "apply_target"
    private const val MAX_SAVED = 20
    const val TARGET_HOME = 1
    const val TARGET_LOCK = 2
    const val TARGET_BOTH = 3

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun directory(context: Context) = File(context.applicationContext.filesDir, "wallpapers").apply { mkdirs() }

    fun loadConfig(context: Context): WallpaperConfig {
        val raw = prefs(context).getString(CONFIG, null) ?: return WallpaperConfig()
        return runCatching {
            val json = JSONObject(raw)
            val colors = json.optJSONArray("colors") ?: JSONArray()
            WallpaperConfig(
                colors = (0 until colors.length()).mapNotNull { colors.optInt(it, Int.MIN_VALUE).takeIf { value -> value != Int.MIN_VALUE } },
                style = WallpaperStyle.from(json.optString("style")),
                seed = json.optLong("seed", 1L),
                complexity = json.optInt("complexity", 1),
                brightness = json.optDouble("brightness", 1.0).toFloat(),
                texture = json.optDouble("texture", 0.18).toFloat(),
                patternScale = json.optDouble("patternScale", 0.5).toFloat(),
                gradientDirection = json.optInt("gradientDirection", 0)
            ).sanitized()
        }.getOrElse {
            Log.e(TAG, "Could not read saved wallpaper configuration; using defaults.", it)
            WallpaperConfig()
        }
    }

    fun saveConfig(context: Context, config: WallpaperConfig) {
        val safe = config.sanitized()
        val json = JSONObject().put("colors", JSONArray(safe.colors)).put("style", safe.style.name)
            .put("seed", safe.seed).put("complexity", safe.complexity).put("brightness", safe.brightness)
            .put("texture", safe.texture).put("patternScale", safe.patternScale).put("gradientDirection", safe.gradientDirection)
        prefs(context).edit().putString(CONFIG, json.toString()).apply()
    }

    fun target(context: Context): Int = prefs(context).getInt(TARGET, TARGET_HOME).takeIf { it in TARGET_HOME..TARGET_BOTH } ?: TARGET_HOME
    fun saveTarget(context: Context, target: Int) { prefs(context).edit().putInt(TARGET, target.coerceIn(TARGET_HOME, TARGET_BOTH)).apply() }

    fun saveWidgetPreview(context: Context, bitmap: Bitmap) {
        val preview = Bitmap.createScaledBitmap(bitmap, 560, 320, true)
        try {
            File(directory(context), "widget-preview.png").outputStream().use {
                check(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not save widget preview." }
            }
        } finally { if (preview !== bitmap) preview.recycle() }
    }

    fun widgetPreview(context: Context): Bitmap? {
        val file = File(directory(context), "widget-preview.png")
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath).also {
            if (it == null) Log.e(TAG, "Could not decode the saved widget wallpaper preview.")
        }
    }

    @Synchronized
    fun save(context: Context, bitmap: Bitmap, config: WallpaperConfig): SavedWallpaper {
        val id = UUID.randomUUID().toString()
        val file = File(directory(context), "$id.png")
        check(file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }) { "Could not write wallpaper image." }
        val entry = SavedWallpaper(id, config.style, config.seed, System.currentTimeMillis(), config.sanitized())
        val items = list(context).toMutableList()
        items.add(0, entry)
        val retained = items.take(MAX_SAVED)
        writeIndex(context, retained)
        items.drop(MAX_SAVED).forEach { File(directory(context), "${it.id}.png").delete() }
        return entry
    }

    @Synchronized
    fun list(context: Context): List<SavedWallpaper> {
        val json = runCatching { JSONArray(prefs(context).getString(SAVED, "[]")) }.getOrElse {
            Log.e(TAG, "Could not read saved wallpaper index.", it)
            JSONArray()
        }
        return (0 until json.length()).mapNotNull { index ->
            val entry = runCatching {
                val item = json.getJSONObject(index)
                val colorsJson = item.getJSONArray("colors")
                val colors = (0 until colorsJson.length()).map { colorsJson.getInt(it) }
                SavedWallpaper(
                    item.getString("id"), WallpaperStyle.from(item.optString("style")), item.optLong("seed"),
                    item.optLong("createdAt"), WallpaperConfig(
                        colors, WallpaperStyle.from(item.optString("style")), item.optLong("seed"),
                        item.optInt("complexity", 1), item.optDouble("brightness", 1.0).toFloat(),
                        item.optDouble("texture", 0.18).toFloat(), item.optDouble("patternScale", 0.5).toFloat(),
                        item.optInt("gradientDirection", 0)
                    ).sanitized()
                )
            }.onFailure { Log.e(TAG, "Ignoring invalid saved wallpaper record at index $index.", it) }.getOrNull()
            entry?.takeIf { File(directory(context), "${it.id}.png").isFile }
        }
    }

    fun image(context: Context, id: String): Bitmap? {
        val file = File(directory(context), "$id.png")
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath).also {
            if (it == null) Log.e(TAG, "Could not decode saved wallpaper $id.")
        }
    }

    fun preview(context: Context, id: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        val path = File(directory(context), "$id.png").absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "Saved wallpaper $id has invalid image dimensions.")
            return null
        }
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > max(maxWidth, maxHeight)) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }).also {
            if (it == null) Log.e(TAG, "Could not decode saved wallpaper preview $id.")
        }
    }

    fun copySavedTo(context: Context, id: String, destination: OutputStream) {
        val file = File(directory(context), "$id.png")
        check(file.isFile) { "Saved wallpaper is no longer available." }
        file.inputStream().use { it.copyTo(destination) }
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val file = File(directory(context), "$id.png")
        check(!file.exists() || file.delete()) { "Could not remove saved wallpaper." }
        writeIndex(context, list(context).filterNot { it.id == id })
    }

    private fun writeIndex(context: Context, entries: List<SavedWallpaper>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val c = entry.config
            array.put(JSONObject().put("id", entry.id).put("style", c.style.name).put("seed", c.seed).put("createdAt", entry.createdAt)
                .put("colors", JSONArray(c.colors)).put("complexity", c.complexity).put("brightness", c.brightness)
                .put("texture", c.texture).put("patternScale", c.patternScale).put("gradientDirection", c.gradientDirection))
        }
        prefs(context).edit().putString(SAVED, array.toString()).apply()
    }
}

/** Canvas-based, deterministic procedural rendering shared by the studio and launcher widget. */
object WallpaperEngine {
    fun render(config: WallpaperConfig, requestedWidth: Int, requestedHeight: Int): Bitmap {
        require(requestedWidth > 0 && requestedHeight > 0) { "Wallpaper dimensions must be positive." }
        val boundedWidth = requestedWidth.coerceAtMost(4096)
        val boundedHeight = requestedHeight.coerceAtMost(4096)
        val scale = min(1.0, kotlin.math.sqrt(12_000_000.0 / (boundedWidth.toDouble() * boundedHeight)))
        val width = max(1, (boundedWidth * scale).toInt())
        val height = max(1, (boundedHeight * scale).toInt())
        val safe = config.sanitized()
        val colors = safe.colors
        val rng = Random(safe.seed)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()
        fun color(index: Int) = colors[index.mod(colors.size)]
        fun fill(color: Int) { paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; canvas.drawRect(rect, paint) }
        fun gradient(x0: Float, y0: Float, x1: Float, y1: Float) =
            LinearGradient(x0, y0, x1, y1, intArrayOf(color(0), color(1), color(2)), null, Shader.TileMode.CLAMP)
        fun layeredPath(path: Path, first: Int, second: Int, x: Float, y: Float) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, y, w, y + h * .18f, intArrayOf(first, second), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)
            paint.shader = null
        }

        when (safe.style) {
            WallpaperStyle.SOLID -> fill(color(0))
            WallpaperStyle.LINEAR -> {
                val (x0, y0, x1, y1) = when (safe.gradientDirection) {
                    1 -> floatArrayOf(w, 0f, 0f, h)
                    2 -> floatArrayOf(0f, 0f, w, 0f)
                    3 -> floatArrayOf(0f, h, 0f, 0f)
                    else -> floatArrayOf(0f, 0f, w, h)
                }
                val colorOffset = rng.nextInt(colors.size)
                paint.shader = LinearGradient(
                    x0, y0, x1, y1,
                    intArrayOf(color(colorOffset), color(colorOffset + 1), color(colorOffset + 2)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(rect, paint)
                paint.shader = null
            }
            WallpaperStyle.RADIAL -> {
                fill(color(0))
                val positions = arrayOf(.32f, .62f, .94f)
                positions.forEachIndexed { i, radius ->
                    val cx = w * (.35f + i * .15f + (rng.nextFloat() - .5f) * .08f)
                    val cy = h * (.35f + i * .12f + (rng.nextFloat() - .5f) * .08f)
                    val variedRadius = radius * (.94f + rng.nextFloat() * .12f)
                    paint.shader = RadialGradient(cx, cy, max(w, h) * variedRadius, intArrayOf(color(i), color((i + 1) % colors.size), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                    canvas.drawRect(rect, paint)
                }
                paint.shader = null
            }
            WallpaperStyle.MESH -> {
                fill(color(0))
                val points = listOf(.2f to .22f, .78f to .2f, .45f to .55f, .18f to .82f, .82f to .78f)
                points.forEachIndexed { i, point ->
                    paint.shader = RadialGradient(w * point.first, h * point.second, max(w, h) * (.4f + rng.nextFloat() * .25f),
                        intArrayOf(color(i + 1), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                    canvas.drawRect(rect, paint)
                }
                paint.shader = null
            }
            WallpaperStyle.ABSTRACT -> {
                fill(Color.BLACK)
                val layers = 3 + safe.complexity * 2
                repeat(layers) { i ->
                    val offset = (rng.nextFloat() - .5f) * w * .5f
                    val path = Path().apply {
                        moveTo(w * (.25f + i * .06f) + offset, -h * .08f)
                        cubicTo(w * (.05f + rng.nextFloat() * .2f) + offset, h * .22f, w * (.85f + rng.nextFloat() * .1f), h * .38f, w * (.5f + offset / w), h * .55f)
                        cubicTo(w * (.12f + rng.nextFloat() * .2f), h * .72f, w * (.8f + rng.nextFloat() * .15f), h * .86f, w * (.48f + offset / w), h * 1.08f)
                        lineTo(w * (.75f + offset / w), h * 1.08f)
                        cubicTo(w * .92f, h * .78f, w * .2f, h * .68f, w * .56f, h * .52f)
                        cubicTo(w * .9f, h * .35f, w * .15f, h * .18f, w * .7f, -h * .08f)
                        close()
                    }
                    layeredPath(path, color(i), color(i + 1), 0f, h * (i * .1f))
                }
            }
            WallpaperStyle.WAVES -> {
                fill(color(0))
                val layers = 3 + safe.complexity * 2
                repeat(layers) { i ->
                    val y = h * (.3f + i * .14f) + (rng.nextFloat() - .5f) * h * .06f
                    val firstControl = y - h * (.12f + rng.nextFloat() * .2f)
                    val secondControl = y + h * (.08f + rng.nextFloat() * .2f)
                    val path = Path().apply {
                        moveTo(0f, y)
                        cubicTo(w * (.18f + rng.nextFloat() * .18f), firstControl, w * (.45f + rng.nextFloat() * .2f), secondControl, w, y + (rng.nextFloat() - .5f) * h * .12f)
                        lineTo(w, h); lineTo(0f, h); close()
                    }
                    layeredPath(path, color(i), color(i + 1), 0f, y)
                }
            }
            WallpaperStyle.GEOMETRIC -> {
                fill(color(0))
                val count = 5 + safe.complexity * 6
                repeat(count) { i ->
                    val cx = rng.nextFloat() * w
                    val cy = rng.nextFloat() * h
                    val radius = min(w, h) * (.035f + rng.nextFloat() * .18f)
                    paint.style = if (i % 3 == 0) Paint.Style.STROKE else Paint.Style.FILL
                    paint.strokeWidth = max(1f, min(w, h) * .004f)
                    paint.color = color(i + 1)
                    when (i % 3) {
                        0 -> canvas.drawCircle(cx, cy, radius, paint)
                        1 -> canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint)
                        else -> canvas.drawPath(Path().apply { moveTo(cx, cy - radius); lineTo(cx + radius, cy + radius); lineTo(cx - radius, cy + radius); close() }, paint)
                    }
                }
                paint.style = Paint.Style.FILL
            }
            WallpaperStyle.CONCENTRIC -> {
                fill(color(0))
                val cx = w * (.5f + (rng.nextFloat() - .5f) * .24f)
                val cy = h * (.5f + (rng.nextFloat() - .5f) * .24f)
                val maxRadius = max(w, h) * .78f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1f, min(w, h) * .006f)
                repeat(22 + safe.complexity * 22) { i ->
                    paint.color = color(i).let { Color.argb(180, Color.red(it), Color.green(it), Color.blue(it)) }
                    canvas.drawCircle(cx, cy, maxRadius * (i + 1) / (22f + safe.complexity * 22), paint)
                }
                paint.style = Paint.Style.FILL
            }
            WallpaperStyle.NOISE -> {
                paint.shader = gradient(0f, 0f, w, h)
                canvas.drawRect(rect, paint)
                paint.shader = null
                repeat((w * h / 900f * (0.15f + safe.texture * 1.8f) * (1 + safe.complexity * .25f)).toInt().coerceAtMost(15_000)) {
                    paint.color = if (rng.nextBoolean()) Color.argb(8 + rng.nextInt((10 + safe.texture * 50).toInt().coerceAtLeast(1)), 255, 255, 255) else Color.argb(8 + rng.nextInt((10 + safe.texture * 40).toInt().coerceAtLeast(1)), 0, 0, 0)
                    val r = .35f + rng.nextFloat() * 1.3f
                    canvas.drawCircle(rng.nextFloat() * w, rng.nextFloat() * h, r, paint)
                }
            }
            WallpaperStyle.AURORA -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(color(0), color(1), color(2)), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                paint.shader = null
                repeat(3 + safe.complexity * 2) { i ->
                    val center = w * (.18f + rng.nextFloat() * .64f)
                    val path = Path().apply {
                        moveTo(-w * .1f, h * (.18f + i * .12f))
                        cubicTo(center - w * .18f, h * (.1f + rng.nextFloat() * .25f), center + w * .2f, h * (.58f + rng.nextFloat() * .18f), w * 1.1f, h * (.35f + i * .1f))
                    }
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = h * (.035f + rng.nextFloat() * .04f)
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.shader = LinearGradient(0f, h * .2f, w, h * .8f, intArrayOf(Color.TRANSPARENT, color(i + 1), color(i), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                    canvas.drawPath(path, paint)
                }
                paint.shader = null
                paint.style = Paint.Style.FILL
            }
            WallpaperStyle.MINIMAL -> {
                fill(color(0))
                val radius = min(w, h) * (.16f + safe.complexity * .08f + rng.nextFloat() * .035f)
                val centerX = w * (.68f + rng.nextFloat() * .2f)
                val centerY = h * (.15f + rng.nextFloat() * .2f)
                paint.shader = RadialGradient(centerX, centerY, radius * 2.2f, intArrayOf(color(1), color(2), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                paint.shader = null
                paint.color = color(2)
                paint.alpha = 125
                canvas.drawCircle(w * (.12f + rng.nextFloat() * .2f), h * (.72f + rng.nextFloat() * .18f), radius * .55f, paint)
                paint.alpha = 255
            }
            WallpaperStyle.PATTERN -> {
                fill(color(0))
                val spacing = (min(w, h) * (.025f + safe.patternScale * .065f) / (1f + safe.complexity * .16f)).coerceAtLeast(8f)
                val dotRadius = spacing * (.035f + safe.patternScale * .035f)
                paint.color = color(1)
                paint.alpha = 185
                var row = 0
                var y = rng.nextFloat() * spacing
                while (y < h) {
                    var x = (if (row % 2 == 0) 0f else spacing / 2) + rng.nextFloat() * spacing * .35f
                    while (x < w) {
                        if (row % 4 == 0) canvas.drawLine(x, y, x + spacing * .35f, y, paint)
                        else canvas.drawCircle(x, y, dotRadius, paint)
                        x += spacing
                    }
                    y += spacing
                    row++
                }
                paint.alpha = 255
            }
        }

        if (safe.style !in setOf(WallpaperStyle.SOLID, WallpaperStyle.NOISE, WallpaperStyle.PATTERN) && safe.texture > 0f) {
            paint.color = Color.WHITE
            repeat((safe.texture * (width * height / 1800f)).toInt().coerceAtMost(12_000)) {
                paint.alpha = rng.nextInt(8, 22)
                canvas.drawCircle(rng.nextFloat() * w, rng.nextFloat() * h, .4f + rng.nextFloat() * 1.2f, paint)
            }
        }
        if (safe.brightness != 1f) {
            val overlay = if (safe.brightness < 1f) Color.BLACK else Color.WHITE
            paint.color = overlay
            paint.alpha = (kotlin.math.abs(safe.brightness - 1f) * 110).toInt().coerceIn(0, 80)
            canvas.drawRect(rect, paint)
        }
        return bitmap
    }
}
