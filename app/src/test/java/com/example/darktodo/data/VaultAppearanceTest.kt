package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAppearanceTest {
    @Test fun defaultsMatchOriginalShortcut() {
        val style = VaultAppearance.DEFAULT
        assertEquals(16f, style.titleSizeSp)
        assertEquals(12f, style.statusSizeSp)
        assertEquals(700, style.titleWeight)
        assertEquals(400, style.statusWeight)
        assertEquals("#F4F3F5", style.titleColor)
        assertEquals("#AAA8B0", style.statusColor)
        assertEquals("#09090C", style.backgroundColor)
        assertEquals("#F20D24", style.accentColor)
        assertEquals(0x3AF20D24, style.outlineArgb())
        assertEquals(100, style.backgroundOpacityPercent)
        assertEquals(18, style.cornerRadiusDp)
        assertEquals(style, style.sanitized())
    }

    @Test fun corruptedValuesAreBoundedAndFontsCannotEscapePrivateDirectory() {
        val style = VaultAppearance(
            fontFamily = "unknown", fontFileId = "../secret.ttf",
            titleSizeSp = Float.NaN, statusSizeSp = Float.POSITIVE_INFINITY,
            titleWeight = -1, statusWeight = 5000, titleColor = "bad!",
            statusColor = "#abc", backgroundColor = "broken", accentColor = "not-a-color",
            backgroundOpacityPercent = -10, cornerRadiusDp = 1000
        ).sanitized()
        assertEquals(WidgetFontFamily.SANS_SERIF.key, style.fontFamily)
        assertNull(style.fontFileId)
        assertEquals(16f, style.titleSizeSp)
        assertEquals(12f, style.statusSizeSp)
        assertEquals(100, style.titleWeight)
        assertEquals(900, style.statusWeight)
        assertEquals("#AABBCC", style.statusColor)
        assertEquals(VaultAppearance.DEFAULT.titleColor, style.titleColor)
        assertEquals(VaultAppearance.DEFAULT.backgroundColor, style.backgroundColor)
        assertEquals(VaultAppearance.DEFAULT.accentColor, style.accentColor)
        assertEquals(0, style.backgroundOpacityPercent)
        assertEquals(40, style.cornerRadiusDp)
    }

    @Test fun sizeAndOpacityBoundsAreInclusive() {
        val low = VaultAppearance(titleSizeSp = -10f, statusSizeSp = 0f, cornerRadiusDp = -2).sanitized()
        val high = VaultAppearance(titleSizeSp = 500f, statusSizeSp = 500f, backgroundOpacityPercent = 200).sanitized()
        assertEquals(12f, low.titleSizeSp)
        assertEquals(10f, low.statusSizeSp)
        assertEquals(0, low.cornerRadiusDp)
        assertEquals(28f, high.titleSizeSp)
        assertEquals(22f, high.statusSizeSp)
        assertEquals(100, high.backgroundOpacityPercent)
    }

    @Test fun backgroundAlphaDoesNotChangeForegroundColors() {
        listOf(0 to 0, 50 to 128, 100 to 255).forEach { (opacity, alpha) ->
            val style = VaultAppearance(backgroundOpacityPercent = opacity)
            assertEquals(alpha, style.backgroundArgb() ushr 24)
            assertEquals(0x09090C, style.backgroundArgb() and 0xFFFFFF)
            assertEquals(255, StickerHex.toArgb(style.titleColor) ushr 24)
            assertEquals(255, StickerHex.toArgb(style.statusColor) ushr 24)
        }
    }

    @Test fun contrastGuidanceDetectsUnreadableColorsWithoutMutatingThem() {
        val style = VaultAppearance(titleColor = "#09090C")
        assertEquals(1f, style.contrastRatio(style.titleColor), .001f)
        assertTrue(VaultAppearance.DEFAULT.contrastRatio("#FFFFFF") > 4.5f)
        assertEquals("#09090C", style.titleColor)
    }

    @Test fun accentChangesOnlyTheOutlineAndFollowsBackgroundOpacity() {
        val style = VaultAppearance(accentColor = "#abc", backgroundOpacityPercent = 50).sanitized()
        assertEquals("#AABBCC", style.accentColor)
        assertEquals(0x1DAABBCC, style.outlineArgb())
        assertEquals("#09090C", style.backgroundColor)
        assertEquals("#F4F3F5", style.titleColor)
        assertEquals(0, style.copy(backgroundOpacityPercent = 0).outlineArgb() ushr 24)
    }
}
