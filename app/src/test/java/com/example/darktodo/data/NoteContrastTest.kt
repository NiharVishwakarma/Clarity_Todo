package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContrastTest {
    @Test fun wcagRatioAndMidtoneSelectionMeetNormalTextTarget() {
        assertEquals(21f, NoteContrast.ratio(0f, 1f), .0001f)
        assertEquals(1f, NoteContrast.ratio(.5f, .5f), .0001f)
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val luminance = NoteContrast.relativeLuminance(red, green, blue)
            val foreground = NoteContrast.inkFor(luminance)
            assertTrue("$red,$green,$blue $foreground",
                NoteContrast.ratio(NoteContrast.luminance(foreground), luminance) >= 4.5f)
        }
    }

    @Test fun rangeContrastDoesNotClaimThatAnImageAverageGuaranteesReadability() {
        assertEquals(1f, NoteContrast.worstRatio("#777777", 0f..1f), .0001f)
        assertTrue(NoteContrast.worstRatio("#FFFFFF", 0f..0.1f) >= 4.5f)
        assertTrue(NoteContrast.worstRatio("#FFFFFF", 0f..1f) < 4.5f)
    }

    @Test fun independentAutomaticModesNeverReplaceStoredCustomColors() {
        val original = StickerStyle(
            titleStyle = NoteTextStyle(color = "#FF0000", automatic = false),
            bodyStyle = NoteTextStyle(color = "#123456", automatic = true)
        )
        val dark = NoteAppearance.effectiveStyle(original, 0f..0f)
        val light = NoteAppearance.effectiveStyle(original, 1f..1f)
        assertEquals("#FF0000", dark.titleText().color)
        assertEquals("#FF0000", light.titleText().color)
        assertEquals("#FFFFFF", dark.bodyText().color)
        assertEquals("#000000", light.bodyText().color)
        assertEquals("#123456", original.bodyText().color)
        val inverted = original.copy(titleStyle = original.titleText().copy(automatic = true),
            bodyStyle = original.bodyText().copy(automatic = false))
        assertEquals("#123456", NoteAppearance.effectiveStyle(inverted, 1f..1f).bodyText().color)
        assertEquals("#000000", NoteAppearance.effectiveStyle(inverted, 1f..1f).titleText().color)
    }

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
