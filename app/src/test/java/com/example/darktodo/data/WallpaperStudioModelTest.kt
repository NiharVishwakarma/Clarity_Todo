package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperStudioModelTest {
    @Test fun allProceduralStylesHaveDistinctIdentities() {
        assertEquals(12, WallpaperStyle.entries.size)
        assertEquals(WallpaperStyle.entries.size, WallpaperStyle.entries.map { it.name }.toSet().size)
        assertEquals(WallpaperStyle.ABSTRACT, WallpaperStyle.from("unknown"))
        assertEquals(WallpaperStyle.AURORA, WallpaperStyle.from("AURORA"))
    }

    @Test fun configSanitizationBoundsUserControlsAndColorCount() {
        val colors = listOf(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 0xFFABCDEF.toInt())
        val safe = WallpaperConfig(
            colors = colors,
            style = WallpaperStyle.PATTERN,
            seed = 42L,
            complexity = 9,
            brightness = Float.NaN,
            texture = -1f,
            patternScale = 2f,
            gradientDirection = -3
        ).sanitized()

        assertEquals(colors.take(3), safe.colors)
        assertEquals(2, safe.complexity)
        assertEquals(1f, safe.brightness, 0f)
        assertEquals(0f, safe.texture, 0f)
        assertEquals(1f, safe.patternScale, 0f)
        assertEquals(0, safe.gradientDirection)
        assertEquals(42L, safe.seed)
    }

    @Test fun emptyPaletteAndNonFiniteControlsAreMadeSafe() {
        val safe = WallpaperConfig(
            colors = emptyList(),
            style = WallpaperStyle.SOLID,
            seed = 3L,
            complexity = -1,
            brightness = Float.POSITIVE_INFINITY,
            texture = Float.NaN,
            patternScale = Float.NEGATIVE_INFINITY,
            gradientDirection = 8
        ).sanitized()

        assertEquals(1, safe.colors.size)
        assertTrue(safe.complexity >= 0)
        assertEquals(1f, safe.brightness, 0f)
        assertEquals(0f, safe.texture, 0f)
        assertEquals(.5f, safe.patternScale, 0f)
        assertEquals(3, safe.gradientDirection)
    }

    @Test fun everyStyleHasAThreeColorResetPalette() {
        WallpaperStyle.entries.forEach { style ->
            val colors = WallpaperPalettes.defaultsFor(style)
            assertEquals(3, colors.size)
            assertTrue(WallpaperPalettes.all.any { it.colors == colors })
        }
        assertEquals(WallpaperPalettes.defaultsFor(WallpaperStyle.WAVES), WallpaperPalettes.defaultsFor(WallpaperStyle.MESH))
    }
}
