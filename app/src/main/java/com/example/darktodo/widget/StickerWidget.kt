package com.example.darktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.Sticker
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.StickerOps
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.StickerStyle
import com.example.darktodo.data.StickerWidgetSize
import kotlin.math.max
import kotlin.math.min

/** Home-screen sticker. Tapping cycles through stickers; the choice is remembered per widget. */
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == StickerWidget.ACTION_NEXT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                StickerWidget.cycle(context, id)
                StickerWidget.update(context, AppWidgetManager.getInstance(context), id)
            }
            return
        }
        super.onReceive(context, intent)
    }
}

object StickerWidget {
    const val ACTION_NEXT = "com.example.darktodo.STICKER_WIDGET_NEXT"
    private const val PREFS = "sticker_widget_selection"
    private const val MAX_PIXELS = 600_000

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
        val sticker = selected(context, id)
        val global = StickerStore.globalStyle(context)
        val style = sticker?.let { StickerStyle.resolve(it, global) } ?: global
        val bitmap = render(
            title = sticker?.title ?: "No stickers yet",
            content = sticker?.content ?: "Open Clarity and add a sticker to see it here.",
            style = style,
            pinned = sticker?.pinned == true,
            widthPx = (wDp * density).toInt(),
            heightPx = (hDp * density).toInt(),
            density = density
        )
        val views = RemoteViews(context.packageName, R.layout.sticker_widget)
        views.setImageViewBitmap(R.id.sticker_widget_image, bitmap)
        views.setContentDescription(R.id.sticker_widget_image, sticker?.let { "${it.title}. ${it.content}. Tap for next sticker" } ?: "No stickers yet")
        val next = Intent(context, StickerWidgetReceiver::class.java)
            .setAction(ACTION_NEXT)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(android.net.Uri.parse("clarity://sticker/next/$id"))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.sticker_widget_root, if (sticker == null) {
            PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), flags)
        } else {
            PendingIntent.getBroadcast(context, id, next, flags)
        })
        manager.updateAppWidget(id, views)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ordered(context: Context) = StickerOps.display(StickerStore.load(context))

    private fun selected(context: Context, id: Int): Sticker? {
        val list = ordered(context)
        val saved = prefs(context).getString("w_$id", null)
        return list.firstOrNull { it.id == saved } ?: list.firstOrNull()
    }

    fun cycle(context: Context, id: Int) {
        val list = ordered(context)
        if (list.isEmpty()) return
        val current = selected(context, id)
        val nextIndex = (list.indexOfFirst { it.id == current?.id } + 1) % list.size
        prefs(context).edit().putString("w_$id", list[nextIndex].id).apply()
    }

    fun forget(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove("w_$it") } }.apply()
    }

    private fun resolveTypeface(style: StickerStyle, weight: Int): Typeface {
        val base = Typeface.create(style.fontFamily, Typeface.NORMAL)
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

    /** Draws the whole sticker (card, shadow, text) so every style option renders identically to the app. */
    internal fun render(title: String, content: String, style: StickerStyle, pinned: Boolean, widthPx: Int, heightPx: Int, density: Float): Bitmap {
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
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = StickerHex.toArgb(style.background)
            alpha = (255 * style.opacityPercent / 100f).toInt()
            if (shadow > 0f) setShadowLayer(shadow, 0f, shadow / 3f, 0x66000000)
        }
        canvas.drawRoundRect(card, radius, radius, fill)

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

        fun paints(s: Float): Pair<TextPaint, TextPaint> {
            val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = baseSize * s; typeface = resolveTypeface(style, style.fontWeight) }
            val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = baseSize * s * 1.15f; typeface = resolveTypeface(style, style.fontWeight + 200) }
            return head to body
        }

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

        if (pinned) {
            val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = StickerHex.toArgb(style.pin) }
            canvas.drawCircle(card.right - pad / 2f - 4f * d, card.top + pad / 2f + 4f * d, 4f * d, pin)
        }
        return bitmap
    }
}
