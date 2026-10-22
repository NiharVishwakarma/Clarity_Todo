package com.example.darktodo.widget

import android.content.Context
import android.graphics.Bitmap
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
import androidx.core.graphics.ColorUtils
import com.example.darktodo.data.ClarityQuote
import com.example.darktodo.data.LocalWidgetFonts
import com.example.darktodo.data.QuoteAlignment
import com.example.darktodo.data.QuotesWidgetPalette
import com.example.darktodo.data.QuotesWidgetSettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Shared bitmap renderer: RemoteViews cannot load arbitrary font files or custom gradients. */
object QuotesWidgetRendering {
    fun description(quote: ClarityQuote?, settings: QuotesWidgetSettings, fallback: Boolean): String =
        listOfNotNull(
            quote?.text ?: "No eligible offline quotes. Allow unverified authors in Settings > Quotes Widget.",
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
        val card = RectF(inset, inset, w - inset, h - inset)
        val radius = min(s.cornerRadiusDp * d, min(card.width(), card.height()) / 2)
        val (start, end, ink, authorInk) = QuotesWidgetPalette.resolve(s, quote?.id.orEmpty())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = start
            if (start != end) shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), start, end, Shader.TileMode.CLAMP)
            if (s.shadow) setShadowLayer(4f * d, 0f, 2f * d, 0x66000000)
        }
        canvas.drawRoundRect(card, radius, radius, fill)
        if (s.border) canvas.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = d
            color = ColorUtils.setAlphaComponent(authorInk, 65)
        })
        val pad = max(s.paddingDp * d, radius * .32f).coerceAtMost(min(card.width(), card.height()) / 4f)
        // Existing Notes widgets can shrink to 56dp high; reserve space for the author there.
        val verticalPad = if (heightDp <= 90) min(pad, 7f * d) else pad
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
                .setAlignment(alignment).setIncludePad(false).setLineSpacing(0f, 1.12f)
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
        val gap = 6f * d
        fun footers() = listOfNotNull(
            author?.let { layout(it, max(9f * d, size * .8f), authorInk, 2) },
            source?.let { layout(it, max(8f * d, size * .64f), authorInk, 2) },
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
