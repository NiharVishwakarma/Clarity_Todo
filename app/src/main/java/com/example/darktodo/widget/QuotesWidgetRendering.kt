package com.example.darktodo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.darktodo.data.ClarityQuote
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuoteBackground
import com.example.darktodo.data.QuoteGradientDirection
import com.example.darktodo.data.QuotesWidgetPalette
import com.example.darktodo.data.QuotesWidgetSettings
import com.example.darktodo.data.StickerHex
import com.example.darktodo.data.WidgetBackgrounds
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Shared bitmap renderer: RemoteViews cannot load arbitrary font files or custom gradients. */
object QuotesWidgetRendering {
    fun description(quote: ClarityQuote?, settings: QuotesWidgetSettings, fallback: Boolean): String =
        listOfNotNull(
            quote?.text ?: "No eligible offline quotes. Allow unverified authors in Notes > Quotes settings.",
            quote?.author?.takeIf { settings.showAuthor && it.isNotBlank() },
            quote?.source?.takeIf { settings.showSource && it.isNotBlank() },
            "No matches for selected topics; showing all eligible topics.".takeIf { fallback }
        ).joinToString("\n")

    fun render(context: Context, quote: ClarityQuote?, settings: QuotesWidgetSettings,
               widthDp: Int, heightDp: Int, fallback: Boolean = false): Bitmap {
        val s = settings.sanitized()
        val density = context.resources.displayMetrics.density
        val rawWidth = widthDp.coerceIn(40, 1200) * density
        val rawHeight = heightDp.coerceIn(40, 1200) * density
        val scale = min(1f, sqrt(120_000f / (rawWidth * rawHeight)))
        val d = density * scale
        val w = max(40, (rawWidth * scale).toInt())
        val h = max(40, (rawHeight * scale).toInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = if (s.shadow) 5f * d else 1f * d
        val outer = RectF(inset, inset, w - inset, h - inset)
        val outerRadius = min(s.outerCornerRadiusDp * d, min(outer.width(), outer.height()) / 2)
        fun fill(bounds: RectF, radius: Float, start: Int, end: Int,
                 direction: QuoteGradientDirection, opacity: Int, shadow: Boolean = false) {
            if (opacity == 0) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = start
                if (start != end) shader = LinearGradient(bounds.left, bounds.top,
                    bounds.left + bounds.width() * direction.x, bounds.top + bounds.height() * direction.y,
                    start, end, Shader.TileMode.CLAMP)
                alpha = (255 * opacity / 100f).toInt()
                if (shadow) setShadowLayer(4f * d, 0f, 2f * d, 0x66000000)
            }
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
        fun border(bounds: RectF, radius: Float, color: String, stroke: Float) {
            val lineBounds = RectF(bounds).apply { inset(stroke / 2, stroke / 2) }
            canvas.drawRoundRect(lineBounds, max(0f, radius - stroke / 2), max(0f, radius - stroke / 2),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = stroke
                    this.color = StickerHex.toArgb(color)
                })
        }
        val (outerStart, outerEnd) = QuotesWidgetPalette.backgroundColors(
            s.outerBackgroundStyle, s.outerBackground, s.outerGradientEnd, quote?.id.orEmpty())
        fill(outer, outerRadius, outerStart, outerEnd, s.outerGradientDirection,
            if (s.outerBackgroundStyle == QuoteBackground.Transparent) 0 else s.outerOpacityPercent)
        val outerStroke = if (s.outerBorder) s.outerBorderWidthDp * d else 0f
        val outerPad = min(s.outerPaddingDp * d + outerStroke,
            min(outer.width() / 6f, max(0f, (outer.height() - 44f * d) / 2)))
        val card = RectF(outer.left + outerPad, outer.top + outerPad, outer.right - outerPad, outer.bottom - outerPad)
        val radius = min(s.cornerRadiusDp * d, min(card.width(), card.height()) / 2)
        val palette = QuotesWidgetPalette.resolve(s, quote?.id.orEmpty())
        val (start, end) = palette
        val hasImage = WidgetBackgrounds.has(context, WidgetBackgrounds.QUOTES)
        val ink = if (hasImage) StickerHex.toArgb(s.textColor) else palette.ink
        val authorInk = if (hasImage) StickerHex.toArgb(s.authorColor) else palette.authorInk
        fill(card, radius, start, end, s.gradientDirection,
            if (s.backgroundStyle == QuoteBackground.Transparent) 0 else s.cardOpacityPercent, s.shadow)
        if (hasImage) {
            val image = WidgetBackgrounds.renderPx(context, WidgetBackgrounds.QUOTES,
                card.width(), card.height(), radius)
            if (image != null) {
                val shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(android.graphics.Matrix().apply { setTranslate(card.left, card.top) })
                }
                canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                    alpha = (255 * s.cardOpacityPercent / 100f).toInt()
                })
                image.recycle()
            }
        }
        val stroke = s.borderWidthDp * d
        if (s.border) border(card, radius, s.borderColor, stroke)
        if (s.outerBorder) border(outer, outerRadius, s.outerBorderColor, outerStroke)
        val pad = max(s.paddingDp * d + if (s.border) stroke else 0f, radius * .32f)
            .coerceAtMost(min(card.width(), card.height()) / 4f)
        // Both providers can shrink to 56dp high. Keep one body/author line before decoration.
        val verticalPad = min(pad, max(0f, (card.height() - 44f * d) / 2))
        val content = RectF(card.left + pad, card.top + verticalPad, card.right - pad, card.bottom - verticalPad)
        val width = content.width().toInt().coerceAtLeast(1)
        val custom = LocalWidgetFonts.typeface(context, s.fontFileId)
        val base = custom ?: Typeface.create(s.fontFamily, Typeface.NORMAL)
        val face = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, s.fontWeight, false)
        else Typeface.create(base, if (s.fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL)
        val alignment = when (s.alignment) {
            QuoteAlignment.Left -> Layout.Alignment.ALIGN_NORMAL
            QuoteAlignment.Center -> Layout.Alignment.ALIGN_CENTER
            QuoteAlignment.Right -> Layout.Alignment.ALIGN_OPPOSITE
        }
        fun paint(size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face
        }
        fun layout(text: String, size: Float, color: Int, lines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint(size, color), width)
                .setAlignment(alignment).setIncludePad(false).setLineSpacing(0f, s.lineSpacing)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
                .build()

        val text = quote?.text?.let { if (s.decoration) "\u201c$it\u201d" else it }
            ?: "No eligible offline quotes. Allow unverified authors in Quotes Widget settings."
        val author = quote?.author?.takeIf { s.showAuthor && it.isNotBlank() }?.let { "\u2014 $it" }
        val source = quote?.source?.takeIf { s.showSource && it.isNotBlank() }
        val notice = if (fallback) "No topic matches. Showing all topics." else null
        val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 2f)
        var size = s.fontSizeSp * d * fontScale
        val minimum = min(size, 10f * d)
        val gap = if (content.height() < 64f * d) 3f * d else 6f * d
        val footerLines = if (content.height() < 64f * d) 1 else 2
        fun footers() = listOfNotNull(
            author?.let { layout(it, max(9f * d, size * .8f), authorInk, footerLines) },
            source?.let { layout(it, max(8f * d, size * .64f), authorInk, footerLines) },
            notice?.let { layout(it, 8f * d, authorInk, 2) }
        )
        var footer = footers()
        var body = layout(text, size, ink)
        fun totalHeight() = body.height + footer.sumOf { it.height } + gap * footer.size
        while (totalHeight() > content.height() && size > minimum) {
            size = max(minimum, size - d)
            footer = footers()
            body = layout(text, size, ink)
        }
        // Very small launcher cells prioritize quote + author, then omit optional metadata.
        while (footer.isNotEmpty() &&
            footer.sumOf { it.height } + gap * footer.size + body.getLineBottom(0) > content.height()) {
            footer = footer.dropLast(1)
        }
        val bodyRoom = (content.height() - footer.sumOf { it.height } - gap * footer.size).coerceAtLeast(1f)
        if (body.height > bodyRoom) {
            val lines = (0 until body.lineCount).count { body.getLineBottom(it) <= bodyRoom }.coerceAtLeast(1)
            body = layout(text, size, ink, lines)
        }
        canvas.save()
        canvas.clipRect(content)
        val used = totalHeight()
        canvas.translate(content.left, content.top + max(0f, (content.height() - used) / 2))
        (listOf(body) + footer).forEach {
            it.draw(canvas)
            canvas.translate(0f, it.height.toFloat() + gap)
        }
        canvas.restore()
        return bitmap
    }
}
