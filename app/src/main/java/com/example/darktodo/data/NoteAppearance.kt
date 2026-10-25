package com.example.darktodo.data

import android.content.Context

/** What a note really looks like on screen: its stored style plus automatic text contrast. Used by the app and the widget alike. */
object NoteAppearance {
    /** Images use conservative black/white bounds after fade. Translucent widgets have an unknown launcher backdrop. */
    fun backgroundRange(context: Context, style: StickerStyle, noteId: String): ClosedFloatingPointRange<Float> {
        val key = WidgetBackgrounds.noteKey(noteId)
        val base = if (WidgetBackgrounds.has(context, key)) {
            val channel = ((1f - WidgetBackgrounds.fade(context, key) / 100f) * 255).toInt()
            0f..NoteContrast.relativeLuminance(channel, channel, channel)
        } else NoteContrast.luminance(style.background).let { it..it }
        if (style.opacityPercent == 100) return base
        // Unknown wallpaper can dominate partially transparent backgrounds; do not promise a ratio.
        return 0f..1f
    }

    fun effectiveStyle(context: Context, style: StickerStyle, noteId: String): StickerStyle {
        val range = backgroundRange(context, style, noteId)
        return effectiveStyle(style, range)
    }

    fun effectiveStyle(style: StickerStyle, backgrounds: ClosedFloatingPointRange<Float>): StickerStyle {
        val recommended = NoteContrast.recommended(backgrounds)
        fun resolve(text: NoteTextStyle) = if (text.automatic) text.copy(color = recommended) else text
        return style.copy(
            text = if (style.autoContrast) recommended else style.text,
            titleStyle = resolve(style.titleText()), bodyStyle = resolve(style.bodyText())
        )
    }
}
