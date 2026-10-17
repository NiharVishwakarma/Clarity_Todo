package com.example.darktodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerOpsTest {
    private fun sticker(id: String, pinned: Boolean = false, updated: Long = 0L, color: StickerColor = StickerColor.Yellow) =
        Sticker(id, "t$id", "c$id", color, pinned, 0L, updated)

    @Test fun fiveColorsAvailable() =
        assertEquals(listOf("Yellow", "Blue", "Green", "Pink", "Purple"), StickerColor.entries.map { it.label })

    @Test fun createTrimsAndRejectsEmpty() {
        val s = StickerOps.create("  Hi ", " body ", StickerColor.Pink, 5L, "id")!!
        assertEquals("Hi", s.title); assertEquals("body", s.content); assertEquals(StickerColor.Pink, s.color)
        assertTrue(!s.pinned); assertEquals(5L, s.updatedAt)
        assertNull(StickerOps.create(" ", "", StickerColor.Blue, 1L))
    }

    @Test fun editUpdatesOnlyTargetAndTimestamp() {
        val list = listOf(sticker("a"), sticker("b"))
        val edited = StickerOps.edit(list, "a", "New", "Body", StickerColor.Green, 99L)
        assertEquals("New", edited[0].title); assertEquals(StickerColor.Green, edited[0].color); assertEquals(99L, edited[0].updatedAt)
        assertEquals(list[1], edited[1])
    }

    @Test fun editWithEmptyContentIsIgnored() {
        val list = listOf(sticker("a"))
        assertEquals(list, StickerOps.edit(list, "a", "", " ", StickerColor.Blue, 9L))
    }

    @Test fun pinAndUnpin() {
        val pinned = StickerOps.setPinned(listOf(sticker("a")), "a", true)
        assertTrue(pinned[0].pinned)
        assertTrue(!StickerOps.setPinned(pinned, "a", false)[0].pinned)
    }

    @Test fun deleteRemovesOnlyTarget() =
        assertEquals(listOf("b"), StickerOps.delete(listOf(sticker("a"), sticker("b")), "a").map { it.id })

    @Test fun pinnedFirstThenNewestFirst() {
        val order = StickerOps.display(listOf(sticker("old", updated = 1), sticker("new", updated = 3), sticker("pin", pinned = true, updated = 0), sticker("mid", updated = 2)))
        assertEquals(listOf("pin", "new", "mid", "old"), order.map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val list = listOf(Sticker("a", "Title", "Line1\nLine2 \"quoted\"", StickerColor.Purple, true, 1L, 2L))
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
}
