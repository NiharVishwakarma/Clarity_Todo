package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContrastTest {
    @Test fun whiteAndBlackLuminance() {
        assertEquals(1f, NoteContrast.luminance("#FFFFFF"), 0.01f)
        assertEquals(0f, NoteContrast.luminance("#000000"), 0.01f)
    }

    @Test fun darkBackgroundsGetLightTextAndLightOnesGetDarkText() {
        assertEquals(NoteContrast.LIGHT_INK, NoteContrast.inkFor(NoteContrast.luminance("#101010")))
        assertEquals(NoteContrast.LIGHT_INK, NoteContrast.inkFor(NoteContrast.luminance("#0D47A1")))
        assertEquals(NoteContrast.DARK_INK, NoteContrast.inkFor(NoteContrast.luminance("#FFE680")))
        assertEquals(NoteContrast.DARK_INK, NoteContrast.inkFor(NoteContrast.luminance("#FFFFFF")))
    }

    @Test fun fadeDarkensAndCanFlipABrightImageToLightText() {
        assertEquals(0.8f, NoteContrast.withFade(0.8f, 0), 0.0001f)
        assertEquals(0f, NoteContrast.withFade(0.8f, 100), 0.0001f)
        assertTrue(NoteContrast.withFade(0.5f, 50) < 0.5f)
        assertEquals(NoteContrast.DARK_INK, NoteContrast.inkFor(NoteContrast.withFade(1f, 0)))
        assertEquals(NoteContrast.LIGHT_INK, NoteContrast.inkFor(NoteContrast.withFade(1f, 90)))
    }
}
