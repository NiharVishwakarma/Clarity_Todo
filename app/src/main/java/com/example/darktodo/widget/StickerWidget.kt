package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.NoteAppearance
import com.example.darktodo.data.Sticker
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerOps
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.StickerWidgetSize
import com.example.darktodo.data.WidgetBackgrounds
import kotlin.math.max
import kotlin.math.min

/** Home-screen note. Tapping it opens the note in Clarity for editing; the note is chosen when the widget is placed. */
class StickerWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { StickerWidget.update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        StickerWidget.update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        StickerWidget.forget(context, ids)
    }
}

object StickerWidget {
    private const val PREFS = "sticker_widget_selection"
    private const val TAG = "StickerWidget"
    private const val MAX_PIXELS = 120_000

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StickerWidgetReceiver::class.java))
        ids.forEach { update(context, manager, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val density = context.resources.displayMetrics.density
        val options = manager.getAppWidgetOptions(id)
        val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(40)
        val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(40)
        val note = selected(context, id)
        val style = if (note != null) NoteAppearance.effectiveStyle(context, StickerStore.styleFor(context, note), note.id) else StickerStore.globalStyle(context)
        val customTypeface = LocalWidgetFonts.typeface(context, style.fontFileId)
        Log.d(TAG, "update id=$id note=${note?.id} family=${style.fontFamily} fontFile=${style.fontFileId} loaded=${customTypeface != null} size=${style.fontSizeSp} weight=${style.fontWeight}")
        val bitmap = render(
            title = note?.title ?: "No notes yet \uD83D\uDCDD",
            content = note?.content ?: "Open Clarity and create a note to see it here.",
            style = style,
            widthPx = (wDp * density).toInt(),
            heightPx = (hDp * density).toInt(),
            density = density,
            custom = customTypeface,
            context = context,
            imageKey = note?.let { WidgetBackgrounds.noteKey(it.id) }
        )
        val views = RemoteViews(context.packageName, R.layout.sticker_widget)
        views.setImageViewBitmap(R.id.sticker_widget_image, bitmap)
        views.setContentDescription(R.id.sticker_widget_image, note?.let { "${it.title}. ${it.content}. Opens the note for editing" } ?: "No notes yet")
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (note != null) {
            open.setAction(MainActivity.ACTION_OPEN_NOTE)
                .putExtra(MainActivity.EXTRA_NOTE_ID, note.id)
                .setData(android.net.Uri.parse("clarity://note/open/${note.id}"))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.sticker_widget_root, PendingIntent.getActivity(context, id, open, flags))
        manager.updateAppWidget(id, views)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun selected(context: Context, id: Int): Sticker? {
        val list = StickerOps.display(StickerStore.load(context))
        val saved = prefs(context).getString("w_$id", null)
        return list.firstOrNull { it.id == saved } ?: list.firstOrNull()
    }

    /** Remembers which note a widget instance shows. */
    fun setNote(context: Context, widgetId: Int, noteId: String) {
        prefs(context).edit().putString("w_$widgetId", noteId).apply()
    }

    fun forget(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove("w_$it") } }.apply()
    }

    private fun resolveTypeface(style: StickerStyle, weight: Int, custom: Typeface?): Typeface {
        val base = custom ?: Typeface.create(style.fontFamily, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight.coerceIn(100, 900), false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layoutFor(text: String, paint: TextPaint, width: Int, lineHeight: Float, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, lineHeight)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    /** Draws the whole note (card, image, border, text) so every style option renders like the in-app preview. */
    internal fun render(
        title: String, content: String, style: StickerStyle, widthPx: Int, heightPx: Int, density: Float,
        custom: Typeface? = null, context: Context? = null, imageKey: String? = null
    ): Bitmap {
        val scale = min(1f, kotlin.math.sqrt(MAX_PIXELS.toFloat() / (max(1, widthPx).toFloat() * max(1, heightPx))))
        val w = max(40, (widthPx * scale).toInt())
        val h = max(40, (heightPx * scale).toInt())
        val d = density * scale
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val shadow = style.elevationDp * d
        val inset = style.marginDp * d + shadow + 2f * d
        val card = RectF(inset, inset, w - inset, h - inset - shadow / 2f)
        val radius = style.cornerRadiusDp * d
        val opacity = (255 * style.opacityPercent / 100f).toInt()
        val background = StickerHex.toArgb(style.background)

        // Fill, image and fade are composed in one layer and drawn through a single rounded rectangle,
        // so every layer is clipped by exactly the same corners and no fill colour can show at the edge.
        val layerW = max(1, card.width().toInt() + 1)
        val layerH = max(1, card.height().toInt() + 1)
        val layer = Bitmap.createBitmap(layerW, layerH, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layer)
        layerCanvas.drawColor(background)
        if (context != null && imageKey != null) {
            WidgetBackgrounds.drawCover(layerCanvas, context, imageKey, RectF(0f, 0f, layerW.toFloat(), layerH.toFloat()))
        }
        if (shadow > 0f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = background
                alpha = opacity
                setShadowLayer(shadow, 0f, shadow / 3f, 0x66000000)
            }
            canvas.drawRoundRect(card, radius, radius, shadowPaint)
        }
        val shader = BitmapShader(layer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setTranslate(card.left, card.top) })
        }
        canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader; alpha = opacity })
        layer.recycle()

        if (style.borderWidthDp > 0) {
            val stroke = style.borderWidthDp * d
            val half = stroke / 2f
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = stroke
                color = StickerHex.toArgb(style.borderColor)
            }
            val inner = max(0f, radius - half)
            canvas.drawRoundRect(RectF(card.left + half, card.top + half, card.right - half, card.bottom - half), inner, inner, line)
        }

        val pad = style.paddingDp * d
        val textWidth = (card.width() - pad * 2).toInt().coerceAtLeast(10)
        val availHeight = (card.height() - pad * 2).coerceAtLeast(10f)
        val ink = StickerHex.toArgb(style.text)
        val baseSize = style.fontSizeSp * style.textScalePercent / 100f * d

        val startScale = when (style.widgetSize) {
            StickerWidgetSize.Small -> 0.85f
            StickerWidgetSize.Medium -> 1f
            StickerWidgetSize.Large -> 1.25f
            StickerWidgetSize.Auto -> 1.6f
        }
        val minScale = if (style.widgetSize == StickerWidgetSize.Auto) 0.5f else 0.6f

        fun textPaint(size: Float, weight: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = size
            typeface = resolveTypeface(style, weight, custom)
            // Glow wins when both effects are on; a paint carries a single shadow layer.
            if (style.glow) setShadowLayer(8f * d, 0f, 0f, (ink and 0x00FFFFFF) or 0xCC000000.toInt())
            else if (style.textShadow) setShadowLayer(3f * d, 1f * d, 1f * d, 0x99000000.toInt())
        }

        fun paints(s: Float): Pair<TextPaint, TextPaint> =
            textPaint(baseSize * s * 1.15f, style.fontWeight + 200) to textPaint(baseSize * s, style.fontWeight)

        var s = startScale
        var head: StaticLayout? = null
        var body: StaticLayout? = null
        var gap = 0f
        while (true) {
            val (hp, bp) = paints(s)
            head = if (title.isBlank()) null else layoutFor(title, hp, textWidth, style.lineHeight, 3)
            body = if (content.isBlank()) null else layoutFor(content, bp, textWidth, style.lineHeight)
            gap = if (head != null && body != null) 4f * d else 0f
            val total = (head?.height ?: 0) + gap + (body?.height ?: 0)
            if (total <= availHeight || s <= minScale) break
            s = max(minScale, s - 0.05f)
        }

        // Last resort at the smallest scale: ellipsize instead of clipping mid-line.
        val headHeight = head?.height ?: 0
        if (body != null && headHeight + gap + body.height > availHeight) {
            val room = availHeight - headHeight - gap
            val (_, bp) = paints(s)
            var lines = body.getLineForVertical(room.toInt().coerceAtLeast(0))
            if (body.getLineBottom(lines) > room) lines -= 1
            body = layoutFor(content, bp, textWidth, style.lineHeight, max(1, lines + 1))
        }

        canvas.save()
        canvas.clipRect(card)
        canvas.translate(card.left + pad, card.top + pad)
        head?.draw(canvas)
        if (body != null) {
            canvas.translate(0f, headHeight + gap)
            body.draw(canvas)
        }
        canvas.restore()
        return bitmap
    }
}
