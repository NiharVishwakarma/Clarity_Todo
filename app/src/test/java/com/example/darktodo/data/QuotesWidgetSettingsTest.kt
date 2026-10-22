package com.example.darktodo.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuotesWidgetSettingsTest {
    @Test fun everySettingSurvivesPersistentJsonRoundTrip() {
        val settings = QuotesWidgetSettings(
            topics = setOf("discipline", "happiness"), includeUnverified = true,
            showAuthor = false, showSource = true, rotation = QuoteRotation.Both,
            intervalMinutes = 180, backgroundStyle = QuoteBackground.Gradient,
            background = "#123456", gradientEnd = "#654321", textColor = "#FFFFFF",
            authorColor = "#CCCCCC", fontFamily = "serif", fontFileId = "test.ttf",
            fontSizeSp = 28f, fontWeight = 700, alignment = QuoteAlignment.Right,
            cornerRadiusDp = 32f, paddingDp = 16f, border = false,
            shadow = true, decoration = false,
            outerBackgroundStyle = QuoteBackground.Gradient, outerBackground = "#334455",
            outerGradientEnd = "#556677", outerGradientDirection = QuoteGradientDirection.Vertical,
            outerOpacityPercent = 45, outerCornerRadiusDp = 36f, outerPaddingDp = 12f,
            gradientDirection = QuoteGradientDirection.Horizontal, cardOpacityPercent = 80,
            borderColor = "#CC4455", borderWidthDp = 2.5f, lineSpacing = 1.5f,
            outerBorder = true, outerBorderColor = "#BB2233", outerBorderWidthDp = 3f
        )
        assertEquals(settings, QuotesWidgetSettings.fromJson(settings.toJson()))
        assertEquals(QuotesWidgetSettings(), QuotesWidgetSettings.fromJson(QuotesWidgetSettings().toJson()))
        assertNull(QuotesWidgetSettings.fromJson("{}").fontFileId)
    }

    @Test fun defaultsAndBoundsAreStable() {
        val defaults = QuotesWidgetSettings()
        assertEquals(setOf("all"), defaults.topics)
        assertFalse(defaults.includeUnverified)
        assertTrue(defaults.showAuthor)
        assertFalse(defaults.showSource)
        assertEquals(QuoteRotation.Manual, defaults.rotation)
        assertEquals(60, defaults.intervalMinutes)
        assertEquals("#19191E", defaults.background)
        assertEquals(20f, defaults.fontSizeSp)
        assertEquals(400, defaults.fontWeight)
        assertEquals(24f, defaults.cornerRadiusDp)
        assertEquals(20f, defaults.paddingDp)
        assertEquals(QuoteAlignment.Left, defaults.alignment)
        assertTrue(defaults.border && defaults.decoration)
        assertFalse(defaults.shadow)
        val invalid = defaults.copy(topics = emptySet(), intervalMinutes = 1, fontWeight = 1000,
            fontSizeSp = Float.NaN, cornerRadiusDp = -12f, paddingDp = 999f, background = "bad-color").sanitized()
        assertEquals(15, invalid.intervalMinutes)
        assertEquals(900, invalid.fontWeight)
        assertEquals(20f, invalid.fontSizeSp)
        assertEquals(0f, invalid.cornerRadiusDp)
        assertEquals(40f, invalid.paddingDp)
        assertEquals(defaults.background, invalid.background)
        assertEquals(setOf("all"), invalid.topics)
    }

    @Test fun exactAndMultipleTopicsNeverLeakOtherTopicsWhenMatchesExist() {
        val library = listOf(quote("one", "discipline"), quote("two", "happiness"), quote("three", "wisdom"))
        val pool = WidgetQuoteSelection.pool(library, QuotesWidgetSettings(topics = setOf("discipline", "happiness")))
        assertFalse(pool.usedTopicFallback)
        assertEquals(setOf("one", "two"), pool.quotes.map { it.id }.toSet())
        assertEquals(listOf("three"), WidgetQuoteSelection.pool(library,
            QuotesWidgetSettings(topics = setOf("life"))).quotes.map { it.id })
        assertEquals(listOf("one"), WidgetQuoteSelection.pool(library,
            QuotesWidgetSettings(topics = setOf("motivation"))).quotes.map { it.id })
    }

    @Test fun missingTopicsFallbackWithoutBypassingAuthorFilter() {
        val library = listOf(quote("one", "discipline"), quote("unknown", "anime").copy(authorVerified = false))
        val pool = WidgetQuoteSelection.pool(library, QuotesWidgetSettings(topics = setOf("anime")))
        assertTrue(pool.usedTopicFallback)
        assertEquals(listOf("one"), pool.quotes.map { it.id })
        assertTrue(WidgetQuoteSelection.pool(library, QuotesWidgetSettings(
            topics = setOf("anime"), includeUnverified = true)).quotes.single().id == "unknown")
        val empty = WidgetQuoteSelection.pool(listOf(library.last()), QuotesWidgetSettings())
        assertTrue(empty.quotes.isEmpty())
        assertFalse(empty.usedTopicFallback)
        assertNull(WidgetQuoteSelection.next(empty.quotes, emptySet(), null))
    }

    @Test fun fullPoolIsExhaustedBeforeRepeatingAndCycleBoundaryAvoidsCurrentQuote() {
        val pool = (1..40).map { quote("$it", "success") }
        var seen = emptySet<String>()
        var current: String? = null
        val displayed = mutableSetOf<String>()
        repeat(40) { index ->
            val next = requireNotNull(WidgetQuoteSelection.next(pool, seen, current, Random(index)))
            assertTrue(displayed.add(next.quote.id))
            seen = next.seenIds
            current = next.quote.id
        }
        assertEquals(40, seen.size)
        val next = requireNotNull(WidgetQuoteSelection.next(pool, seen, current, Random(41)))
        assertNotEquals(current, next.quote.id)
        assertEquals(setOf(next.quote.id), next.seenIds)
        assertNotNull(WidgetQuoteSelection.next(listOf(pool.first()), setOf("1"), "1"))
    }

    @Test fun changedFiltersDiscardUnrelatedHistoryAndStillAvoidImmediateRepeats() {
        val pool = listOf(quote("one", "success"), quote("two", "success"))
        val choice = requireNotNull(WidgetQuoteSelection.next(pool, setOf("old", "one"), "one"))
        assertEquals("two", choice.quote.id)
        assertEquals(setOf("one", "two"), choice.seenIds)
    }

    @Test fun rotationModesSeparateTapFromPeriodicUpdates() {
        assertTrue(QuoteRotation.Manual.tapToRefresh)
        assertFalse(QuoteRotation.Manual.scheduled)
        assertFalse(QuoteRotation.Scheduled.tapToRefresh)
        assertTrue(QuoteRotation.Scheduled.scheduled)
        assertTrue(QuoteRotation.Both.tapToRefresh && QuoteRotation.Both.scheduled)
    }

    @Test fun unverifiedAttributionIsExplicitlyExcludedByDefault() {
        assertFalse(quote("unknown", "wisdom").copy(author = "Unknown", authorVerified = false).authorVerified)
        assertFalse(ClarityQuote("unknown", "Sample", "Anonymous", "wisdom", "Source", "https://example.org").authorVerified)
        assertFalse(ClarityQuote("unsourced", "Sample", "Named", "wisdom", "", "").authorVerified)
        assertTrue(quote("known", "wisdom").authorVerified)
    }

    @Test fun paletteMeetsContrastThresholdAcrossSolidAndExtremeGradients() {
        val random = Random(123)
        val pairs = listOf("#000000" to "#FFFFFF", "#FF0000" to "#00FF00", "#FFFF00" to "#0000FF") +
            (1..100).map { "#%06X".format(random.nextInt(0x1000000)) to "#%06X".format(random.nextInt(0x1000000)) }
        fun lum(color: Int) = NoteContrast.relativeLuminance(
            (color shr 16) and 255, (color shr 8) and 255, color and 255
        ).toDouble()
        pairs.forEach { (start, end) ->
            QuoteBackground.entries.filterNot { it == QuoteBackground.Transparent }.forEach { mode ->
                val palette = QuotesWidgetPalette.resolve(QuotesWidgetSettings(
                    backgroundStyle = mode, background = start, gradientEnd = end,
                    textColor = start, authorColor = end
                ), start)
                (0..100).forEach { position ->
                    val f = position / 100.0
                    fun channel(shift: Int) = ((((palette.start shr shift) and 255) * (1 - f)) +
                        (((palette.end shr shift) and 255) * f)).toInt()
                    val bg = 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                    listOf(palette.ink, palette.authorInk).forEach { ink ->
                        val ratio = (maxOf(lum(bg), lum(ink)) + .05) / (minOf(lum(bg), lum(ink)) + .05)
                        assertTrue("$start -> $end at $position: $ratio", ratio >= 4.5)
                    }
                }
            }
        }
        val defaults = QuotesWidgetSettings()
        val palette = QuotesWidgetPalette.resolve(defaults, "quote")
        assertEquals(StickerHex.toArgb(defaults.textColor), palette.ink)
        assertEquals(StickerHex.toArgb(defaults.authorColor), palette.authorInk)
    }

    @Test fun legacySettingsKeepCardAppearanceWithNoOuterLayer() {
        val migrated = QuotesWidgetSettings.fromJson("""{
            "backgroundStyle":"Gradient", "background":"#123456", "gradientEnd":"#654321",
            "cornerRadiusDp":16, "paddingDp":12, "showAuthor":false
        }""")
        assertEquals(QuoteBackground.Gradient, migrated.backgroundStyle)
        assertEquals("#123456", migrated.background)
        assertEquals("#654321", migrated.gradientEnd)
        assertEquals(16f, migrated.cornerRadiusDp)
        assertEquals(12f, migrated.paddingDp)
        assertFalse(migrated.showAuthor)
        assertEquals(QuoteBackground.Transparent, migrated.outerBackgroundStyle)
        assertEquals(0f, migrated.outerPaddingDp)
        assertEquals(100, migrated.cardOpacityPercent)
        assertEquals(1.12f, migrated.lineSpacing)
    }

    @Test fun newAppearanceBoundsAreClampedAndLayerColorsStayIndependent() {
        val value = QuotesWidgetSettings(outerOpacityPercent = -10, cardOpacityPercent = 200,
            outerPaddingDp = Float.NaN, outerCornerRadiusDp = 100f, borderWidthDp = Float.POSITIVE_INFINITY,
            borderColor = "invalid", lineSpacing = 10f).sanitized()
        assertEquals(0, value.outerOpacityPercent)
        assertEquals(100, value.cardOpacityPercent)
        assertEquals(0f, value.outerPaddingDp)
        assertEquals(48f, value.outerCornerRadiusDp)
        assertEquals(1f, value.borderWidthDp)
        assertEquals("#55555F", value.borderColor)
        assertEquals(1.8f, value.lineSpacing)
        val settings = QuotesWidgetSettings(background = "#112233", outerBackground = "#AABBCC",
            outerGradientEnd = "#DDEEFF", outerBackgroundStyle = QuoteBackground.Gradient)
        assertEquals(StickerHex.toArgb("#112233"), QuotesWidgetPalette.resolve(settings, "quote").start)
        assertEquals(StickerHex.toArgb("#AABBCC") to StickerHex.toArgb("#DDEEFF"),
            QuotesWidgetPalette.backgroundColors(settings.outerBackgroundStyle, settings.outerBackground, settings.outerGradientEnd, "quote"))
    }

    @Test fun transparentAndTranslucentCardsRetainRequestedInkWithoutFalseContrastGuarantee() {
        listOf(QuotesWidgetSettings(backgroundStyle = QuoteBackground.Transparent),
            QuotesWidgetSettings(cardOpacityPercent = 30)).forEach { defaults ->
            val settings = defaults.copy(textColor = "#112233", authorColor = "#445566")
            val palette = QuotesWidgetPalette.resolve(settings, "quote")
            assertEquals(StickerHex.toArgb(settings.textColor), palette.ink)
            assertEquals(StickerHex.toArgb(settings.authorColor), palette.authorInk)
        }
    }

    @Test fun gradientDirectionsSpanTheirNamedAxes() {
        assertEquals(1f, QuoteGradientDirection.Horizontal.x)
        assertEquals(0f, QuoteGradientDirection.Horizontal.y)
        assertEquals(0f, QuoteGradientDirection.Vertical.x)
        assertEquals(1f, QuoteGradientDirection.Vertical.y)
        assertEquals(1f, QuoteGradientDirection.Diagonal.x)
        assertEquals(1f, QuoteGradientDirection.Diagonal.y)
    }

    private fun quote(id: String, topic: String) = ClarityQuote(id, "Sample $id", "Named author", topic, "Source", "https://example.org")
}
