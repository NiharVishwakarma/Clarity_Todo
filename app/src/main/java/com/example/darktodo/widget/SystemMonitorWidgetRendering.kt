package com.example.darktodo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.SystemMonitorAppearance
import com.example.darktodo.data.WidgetBackgrounds
import kotlin.math.ceil

object SystemMonitorWidgetRendering {
    /**
     * Renders text into a Bitmap using custom font or system typeface.
     */
    fun text(
        context: Context,
        text: String,
        sizeSp: Float,
        colorHex: String,
        appearance: SystemMonitorAppearance,
        weight: Int = 400,
        maxWidthDp: Float = 200f,
        maxLines: Int = 1
    ): Bitmap {
        val style = appearance.sanitized()
        val metrics = context.resources.displayMetrics
        val scale = minOf(metrics.density, 2f)
        val base = LocalWidgetFonts.typeface(context, style.fontFileId)
            ?: Typeface.create(style.fontFamily, Typeface.NORMAL)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
            else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            textSize = sizeSp * metrics.scaledDensity * scale / metrics.density
            color = StickerHex.toArgb(colorHex)
        }
        val padding = ceil(2 * scale).toInt()
        val width = (maxWidthDp.coerceIn(1f, 600f) * scale).toInt().coerceAtLeast(1)
        val contentWidth = (width - padding * 2).coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines.coerceIn(1, 4))
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(contentWidth)
            .build()
        val bitmap = Bitmap.createBitmap(
            width,
            (layout.height + padding * 2).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        bitmap.density = (metrics.densityDpi * scale / metrics.density).toInt()
        Canvas(bitmap).apply {
            translate(padding.toFloat(), padding.toFloat())
            layout.draw(this)
        }
        return bitmap
    }

    /**
     * Renders the outer widget background with wallpaper or custom solid color/alpha, border, and corner radius.
     */
    fun background(context: Context, appearance: SystemMonitorAppearance, widthDp: Int, heightDp: Int): Bitmap {
        val style = appearance.sanitized()
        val scale = minOf(context.resources.displayMetrics.density, 1f)
        val width = (widthDp.coerceIn(1, 800) * scale).toInt().coerceAtLeast(1)
        val height = (heightDp.coerceIn(1, 800) * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = scale
        val bounds = RectF(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        val radius = style.cornerRadiusDp * scale

        val layer = canvas.saveLayerAlpha(bounds, style.backgroundArgb() ushr 24)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = StickerHex.toArgb(style.backgroundColor)
        }
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        if (style.wallpaperEnabled) WidgetBackgrounds.renderPx(
            context, WidgetBackgrounds.SYSTEM_MONITOR, width.toFloat(), height.toFloat(), radius
        )?.let { wallpaper ->
            canvas.drawBitmap(
                wallpaper, null, bounds,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
        canvas.restoreToCount(layer)

        // Draw outline border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = stroke
            color = style.outlineArgb()
        }
        canvas.drawRoundRect(bounds, radius, radius, borderPaint)
        return bitmap
    }

    /**
     * Renders card surface bitmap with customizable corner radius and card background color/opacity.
     */
    fun cardBackground(appearance: SystemMonitorAppearance, widthDp: Int = 120, heightDp: Int = 64): Bitmap {
        val style = appearance.sanitized()
        val width = maxOf(1, widthDp)
        val height = maxOf(1, heightDp)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = style.cardCornerRadiusDp.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.cardBackgroundArgb()
        }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        return bitmap
    }
}
