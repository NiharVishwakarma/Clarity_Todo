package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerOpsTest {
    private fun note(id: String, updated: Long = 0L, color: StickerColor = StickerColor.Yellow) =
        Sticker(id, "t$id", "c$id", color, 0L, updated)

    @Test fun fiveColorsAvailable() =
        assertEquals(listOf("Yellow", "Blue", "Green", "Pink", "Purple"), StickerColor.entries.map { it.label })

    @Test fun createTrimsAndRejectsEmpty() {
        val s = StickerOps.create("  Hi ", " body ", StickerColor.Pink, 5L, "id")!!
        assertEquals("Hi", s.title); assertEquals("body", s.content); assertEquals(StickerColor.Pink, s.color)
        assertEquals(5L, s.updatedAt)
        assertNull(StickerOps.create(" ", "", StickerColor.Blue, 1L))
    }

    @Test fun editUpdatesOnlyTargetAndTimestamp() {
        val list = listOf(note("a"), note("b"))
        val edited = StickerOps.edit(list, "a", "New", "Body", StickerColor.Green, 99L)
        assertEquals("New", edited[0].title); assertEquals(StickerColor.Green, edited[0].color); assertEquals(99L, edited[0].updatedAt)
        assertEquals(list[1], edited[1])
    }

    @Test fun editWithEmptyContentIsIgnored() {
        val list = listOf(note("a"))
        assertEquals(list, StickerOps.edit(list, "a", "", " ", StickerColor.Blue, 9L))
    }

    @Test fun renameChangesOnlyTheTitle() {
        val list = listOf(note("a", updated = 1), note("b"))
        val renamed = StickerOps.rename(list, "a", "  Shopping ", 50L)
        assertEquals("Shopping", renamed[0].title)
        assertEquals("ca", renamed[0].content)
        assertEquals(50L, renamed[0].updatedAt)
        assertEquals(list[1], renamed[1])
        assertEquals(list, StickerOps.rename(list, "a", "   ", 60L))
    }

    @Test fun duplicateAppendsAnIndependentCopyWithItsStyle() {
        val style = StickerStyle(background = "#102030", borderWidthDp = 3)
        val list = listOf(Sticker("a", "Ideas", "text", StickerColor.Blue, 1L, 2L, style))
        val result = StickerOps.duplicate(list, "a", "copy-id", 10L)
        assertEquals(2, result.size)
        val copy = result.first { it.id == "copy-id" }
        assertEquals("Ideas (copy)", copy.title)
        assertEquals("text", copy.content)
        assertEquals(style, copy.style)
        assertEquals(10L, copy.createdAt)
        assertEquals(list, StickerOps.duplicate(list, "missing", "x", 10L))
    }

    @Test fun deleteRemovesOnlyTarget() =
        assertEquals(listOf("b"), StickerOps.delete(listOf(note("a"), note("b")), "a").map { it.id })

    @Test fun newestFirst() {
        val order = StickerOps.display(listOf(note("old", updated = 1), note("new", updated = 3), note("mid", updated = 2)))
        assertEquals(listOf("new", "mid", "old"), order.map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val list = listOf(Sticker("a", "Title", "Line1\nLine2 \"quoted\"", StickerColor.Purple, 1L, 2L))
        assertEquals(list, StickerOps.decode(StickerOps.encode(list)))
    }

    @Test fun decodeToleratesBadInput() {
        assertTrue(StickerOps.decode(null).isEmpty())
        assertTrue(StickerOps.decode("not json").isEmpty())
        val partial = StickerOps.decode("""[{"id":"x","color":"Nope"},{"title":"no id"}]""")
        assertEquals(1, partial.size)
        assertEquals(StickerColor.Yellow, partial[0].color)
        assertNotNull(partial[0].id)
    }

    @Test fun notesSavedWithThePinFieldsStillLoad() {
        val json = """[{"id":"a","title":"t","content":"c","color":"Blue","pinned":true,"createdAt":1,"updatedAt":2,
            "style":{"background":"#112233","text":"#FFFFFF","pin":"#FF0000"}}]"""
        val loaded = StickerOps.decode(json)
        assertEquals(1, loaded.size)
        assertEquals("#112233", loaded[0].style?.background)
        assertEquals(false, loaded[0].style?.autoContrast)
    }
}
