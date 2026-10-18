package com.example.darktodo.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickerStyleTest {
    @Test fun backgroundDepthIsOptInForNewAndLegacyStyles() {
        assertEquals(false, StickerStyle().backgroundDepth)
        assertEquals(false, StickerStyle.DEFAULT.backgroundDepth)
        assertEquals(false, StickerStyle.fromJson(JSONObject())!!.backgroundDepth)
        assertEquals(false, StickerStyle.fromJson(JSONObject("""{"background":"#112233"}"""))!!.backgroundDepth)
    }

    @Test fun backgroundDepthRoundTripsAndSurvivesSanitization() {
        listOf(false, true).forEach { enabled ->
            val style = StickerStyle(backgroundDepth = enabled)
            assertEquals(enabled, style.toJson().getBoolean("backgroundDepth"))
            assertEquals(style, StickerStyle.fromJson(style.toJson()))
            assertEquals(enabled, style.copy(paddingDp = -1).sanitized().backgroundDepth)
        }
    }

    @Test fun notesKeepIndependentDepthFlagsAcrossJsonAndStyleResolution() {
        val depth = StickerStyle(backgroundDepth = true)
        val flat = StickerStyle(backgroundDepth = false)
        val notes = listOf(Sticker("a", "Depth", "text", style = depth), Sticker("b", "Flat", "text", style = flat))
        val decoded = StickerOps.decode(StickerOps.encode(notes))
        assertEquals(true, StickerStyle.resolve(decoded[0], flat).backgroundDepth)
        assertEquals(false, StickerStyle.resolve(decoded[1], depth).backgroundDepth)
        assertEquals(depth, decoded[0].style)
        assertEquals(flat, decoded[1].style)
    }

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

    @Test fun styleRoundTripsThroughNoteJson() {
        val style = StickerStyle(background = "#000000", text = "#FFFFFF", fontWeight = 700, widgetSize = StickerWidgetSize.Large, cornerRadiusDp = 8)
        val note = Sticker("1", "t", "c", StickerColor.Blue, style = style)
        val decoded = StickerOps.decode(StickerOps.encode(listOf(note, note.copy(id = "2", style = null))))
        assertEquals(style, decoded[0].style)
        assertNull(decoded[1].style)
    }

    @Test fun noteWithoutStyleKeepsPresetColoursButUsesGlobalTypography() {
        val global = StickerStyle(fontSizeSp = 20f, cornerRadiusDp = 4)
        val resolved = StickerStyle.resolve(Sticker("1", "t", "c", StickerColor.Blue), global)
        assertEquals(20f, resolved.fontSizeSp)
        assertEquals(4, resolved.cornerRadiusDp)
        assertEquals("#9CCBFF", resolved.background)
    }

    @Test fun customisedGlobalColoursApplyToNotesWithoutStyle() {
        val global = StickerStyle(background = "#101010", text = "#EEEEEE")
        val resolved = StickerStyle.resolve(Sticker("1", "t", "c", StickerColor.Blue), global)
        assertEquals("#101010", resolved.background)
        assertEquals("#EEEEEE", resolved.text)
    }

    @Test fun aNotesOwnStyleIsUsedAsIs() {
        val global = StickerStyle(fontFileId = "f.ttf", borderWidthDp = 4, background = "#101010")
        val own = StickerStyle(background = "#222222", text = "#333333", fontSizeSp = 27f, borderWidthDp = 0)
        val resolved = StickerStyle.resolve(Sticker("1", "t", "c", style = own), global)
        assertEquals(own, resolved)
    }

    @Test fun fontFileBorderAndReadabilityRoundTripThroughJson() {
        val style = StickerStyle(fontFileId = "abc.ttf", borderWidthDp = 3, borderColor = "#112233", autoContrast = false, textShadow = true, glow = true)
        assertEquals(style, StickerStyle.fromJson(style.toJson()))
        assertNull(StickerStyle.fromJson(StickerStyle().toJson())!!.fontFileId)
    }

    @Test fun stylesSavedBeforeReadabilityOptionsKeepTheirLook() {
        val legacy = StickerStyle.fromJson(JSONObject("""{"background":"#FFE680","text":"#3B3200","pin":"#000000"}"""))!!
        assertEquals(false, legacy.autoContrast)
        assertEquals(false, legacy.textShadow)
        assertEquals(false, legacy.glow)
        assertEquals("#3B3200", legacy.text)
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
