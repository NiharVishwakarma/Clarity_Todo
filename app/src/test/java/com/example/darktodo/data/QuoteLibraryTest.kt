package com.example.darktodo.data

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuoteLibraryTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }

    @Test fun bundledQuotesHaveStableUniqueIdsAndVerifiedSourceMetadata() {
        val array = JSONArray(File(main, "assets/quotes.json").readText())
        val ids = mutableSetOf<String>()
        val texts = mutableSetOf<String>()
        val validTopics = QuoteLibrary.topics.map { it.id }.toSet()
        for (index in 0 until array.length()) {
            val quote = array.getJSONObject(index)
            assertTrue(ids.add(quote.getString("id")))
            assertTrue(texts.add(quote.getString("text").trim().lowercase()))
            assertTrue(quote.getString("text").isNotBlank())
            assertTrue(quote.getString("author").isNotBlank())
            assertTrue(quote.getString("source").isNotBlank())
            assertTrue(quote.getString("sourceUrl").startsWith("https://"))
            assertTrue(quote.getString("topic") in validTopics)
        }
        assertEquals(16, ids.size)
        assertTrue(validTopics.containsAll(setOf("anime", "discipline", "happiness")))
        assertFalse((0 until array.length()).any { array.getJSONObject(it).getString("topic") == "anime" })
    }

    @Test fun randomSelectionAvoidsRecentQuotesUntilAlternativesAreExhausted() {
        val quotes = listOf(
            quote("one", "success"),
            quote("two", "success"),
            quote("three", "wisdom")
        )
        val chosen = QuoteLibrary.choose(quotes, setOf("success"), listOf("one"), Random(0))
        assertNotNull(chosen)
        assertEquals("two", chosen?.id)
        assertNotNull(QuoteLibrary.choose(quotes, setOf("success"), listOf("one", "two"), Random(0)))
        assertNull(QuoteLibrary.choose(quotes, setOf("anime"), emptyList(), Random(0)))
        assertNull(QuoteLibrary.choose(quotes, setOf("custom:anime"), emptyList(), Random(0)))
        assertEquals(
            "one",
            QuoteLibrary.choose(quotes, setOf("custom:Sample one"), emptyList(), Random(0))?.id
        )
        val multipleTopics = QuoteLibrary.choose(quotes, setOf("success", "wisdom"), emptyList(), Random(0))
        assertTrue(multipleTopics != null && multipleTopics.id in setOf("one", "two", "three"))
    }

    @Test fun allTopicsAndMultipleSelectionsAreSupported() {
        assertEquals(setOf(QuoteLibrary.ALL_TOPICS), QuoteLibrary.toggleTopic(emptySet(), "all"))
        val selected = QuoteLibrary.toggleTopic(setOf("all"), "success")
        assertEquals(setOf("success"), selected)
        assertFalse(QuoteLibrary.ALL_TOPICS in selected)
        assertEquals(setOf(QuoteLibrary.ALL_TOPICS), QuoteLibrary.toggleTopic(selected, "success"))
        assertEquals(setOf("success", "wisdom"), setOf("all").let {
            QuoteLibrary.toggleTopic(QuoteLibrary.toggleTopic(it, "success"), "wisdom")
        })
    }

    private fun quote(id: String, topic: String) = ClarityQuote(
        id = id,
        text = "Sample $id",
        author = "Unknown",
        topic = topic,
        source = "Test source",
        sourceUrl = "https://example.org"
    )
}
