package com.example.darktodo.data

import android.content.Context

/** What a note really looks like on screen: its stored style plus automatic text contrast. Used by the app and the widget alike. */
object NoteAppearance {
    /** Text colour is chosen from the background (colour, or image after its fade) when auto contrast is on. */
    fun effectiveStyle(context: Context, style: StickerStyle, noteId: String): StickerStyle {
        if (!style.autoContrast) return style
        val key = WidgetBackgrounds.noteKey(noteId)
        val imageLuminance = WidgetBackgrounds.averageLuminance(context, key)
        val luminance = if (imageLuminance != null) NoteContrast.withFade(imageLuminance, WidgetBackgrounds.fade(context, key))
        else NoteContrast.luminance(style.background)
        return style.copy(text = NoteContrast.inkFor(luminance))
    }
}
