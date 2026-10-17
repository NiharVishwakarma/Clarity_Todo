package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerStyleTest {
    @Test fun hexIsNormalizedFromPastedValues() {
        assertEquals("#FFD700", StickerHex.normalize("ffd700"))
        assertEquals("#C4B5FD", StickerHex.normalize("  #c4b5fd "))
        assertEquals("#FFAA00", StickerHex.normalize("#fa0"))
        assertNull(StickerHex.normalize("#12345"))
        assertNull(StickerHex.normalize("zzzzzz"))
        assertNull(StickerHex.normalize(null))
    }

    @Test fun sanitizedClampsOutOfRangeValues() {
        val s = StickerStyle(background = "nope", fontSizeSp = 99f, paddingDp = -4, opacityPercent = 0, fontFamily = "comic").sanitized()
        assertEquals(StickerStyle.DEFAULT.background, s.background)
        assertEquals(28f, s.fontSizeSp)
        assertEquals(4, s.paddingDp)
        assertEquals(30, s.opacityPercent)
        assertEquals(StickerStyle.DEFAULT.fontFamily, s.fontFamily)
    }

    @Test fun styleRoundTripsThroughStickerJson() {
        val style = StickerStyle(background = "#000000", text = "#FFFFFF", fontWeight = 700, widgetSize = StickerWidgetSize.Large, cornerRadiusDp = 8)
        val sticker = Sticker("1", "t", "c", StickerColor.Blue, style = style)
        val decoded = StickerOps.decode(StickerOps.encode(listOf(sticker, sticker.copy(id = "2", style = null))))
        assertEquals(style, decoded[0].style)
        assertNull(decoded[1].style)
    }

    @Test fun stickerWithoutOverrideKeepsPresetColoursButUsesGlobalTypography() {
        val global = StickerStyle(fontSizeSp = 20f, cornerRadiusDp = 4)
        val resolved = StickerStyle.resolve(Sticker("1", "t", "c", StickerColor.Blue), global)
        assertEquals(20f, resolved.fontSizeSp)
        assertEquals(4, resolved.cornerRadiusDp)
        assertEquals("#9CCBFF", resolved.background)
    }

    @Test fun colourBookKeepsNewestRecentsAndTogglesFavourites() {
        var book = StickerColorBook()
        (1..10).forEach { book = book.withRecent("#%06X".format(it)) }
        assertEquals(StickerColorBook.MAX_RECENTS, book.recents.size)
        book = book.withRecent("#ff0000").withRecent("#00ff00").withRecent("#FF0000")
        assertEquals("#FF0000", book.recents.first())
        assertEquals(1, book.recents.count { it == "#FF0000" })
        book = book.toggleFavorite("ff0000")
        assertEquals(listOf("#FF0000"), book.favorites)
        assertEquals(emptyList<String>(), book.toggleFavorite("#FF0000").favorites)
        assertEquals(book, StickerColorBook.decode(book.encode()))
    }
}
