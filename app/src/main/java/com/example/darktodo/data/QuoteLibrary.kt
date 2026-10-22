package com.example.darktodo.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import kotlin.random.Random

data class ClarityQuote(
    val id: String,
    val text: String,
    val author: String,
    val topic: String,
    val source: String,
    val sourceUrl: String,
    val authorVerified: Boolean = author.isNotBlank() &&
        !author.equals("Unknown", ignoreCase = true) &&
        !author.equals("Anonymous", ignoreCase = true) &&
        source.isNotBlank() && sourceUrl.startsWith("https://")
) {
    fun attributedText(): String = "\u201c$text\u201d\n\u2014 $author"
}

data class QuoteTopic(val id: String, val label: String)

object QuoteLibrary {
    const val ALL_TOPICS = "all"
    const val CUSTOM_PREFIX = "custom:"
    const val PREFS = "quotes_store"
    private const val SELECTED_KEY = "selected_topics"
    private const val CUSTOM_KEY = "custom_topics"
    private const val HISTORY_KEY = "recent_ids"
    private const val FAVORITES_KEY = "favorite_ids"
    private const val EXPANDED_KEY = "section_expanded"
    private const val MAX_RECENT = 30

    val topics = listOf(
        QuoteTopic("discipline", "Motivation and Discipline"),
        QuoteTopic("success", "Success and Ambition"),
        QuoteTopic("stoicism", "Stoicism and Philosophy"),
        QuoteTopic("wisdom", "Life and Wisdom"),
        QuoteTopic("love", "Love and Relationships"),
        QuoteTopic("mental-strength", "Mental Strength"),
        QuoteTopic("spirituality", "Spirituality and the Universe"),
        QuoteTopic("anime", "Anime"),
        QuoteTopic("dark", "Dark and Mysterious"),
        QuoteTopic("creativity", "Creativity and Focus"),
        QuoteTopic("happiness", "Happiness and Gratitude")
    )

    fun load(context: Context): List<ClarityQuote> {
        val json = context.applicationContext.assets.open("quotes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ClarityQuote(
                id = item.getString("id"),
                text = item.getString("text"),
                author = item.getString("author"),
                topic = item.getString("topic"),
                source = item.getString("source"),
                sourceUrl = item.getString("sourceUrl"),
                // Bundled entries have cited attribution; explicit metadata can override it.
                authorVerified = item.optBoolean("authorVerified",
                    item.optString("author").isNotBlank() &&
                        !item.optString("author").equals("Unknown", true) &&
                        !item.optString("author").equals("Anonymous", true) &&
                        item.optString("source").isNotBlank() &&
                        item.optString("sourceUrl").startsWith("https://"))
            )
        }
    }

    fun choose(
        quotes: List<ClarityQuote>,
        selectedTopics: Set<String>,
        recentIds: List<String>,
        random: Random = Random.Default
    ): ClarityQuote? {
        val localTopics = selectedTopics
            .filterNot { it == ALL_TOPICS || it.startsWith(CUSTOM_PREFIX) }
            .toSet()
        val customSelected = selectedTopics.filter { it.startsWith(CUSTOM_PREFIX) }
            .map { it.removePrefix(CUSTOM_PREFIX) }
        val candidates = quotes.filter { quote ->
            selectedTopics.isEmpty() ||
                ALL_TOPICS in selectedTopics ||
                quote.topic in localTopics ||
                customSelected.any { topic ->
                    quote.text.contains(topic, ignoreCase = true) ||
                        quote.author.contains(topic, ignoreCase = true) ||
                        quote.source.contains(topic, ignoreCase = true) ||
                        topics.firstOrNull { it.id == quote.topic }?.label?.contains(topic, ignoreCase = true) == true
                }
        }
        if (candidates.isEmpty()) return null
        val unseen = candidates.filterNot { it.id in recentIds }
        return (unseen.ifEmpty { candidates }).random(random)
    }

    fun selectedTopics(context: Context): Set<String> =
        stringSet(context, SELECTED_KEY).ifEmpty { setOf(ALL_TOPICS) }

    fun saveSelectedTopics(context: Context, topics: Set<String>) {
        prefs(context).edit().putString(SELECTED_KEY, JSONArray(topics.toList()).toString()).apply()
    }

    fun customTopics(context: Context): List<String> =
        stringList(context, CUSTOM_KEY).distinctBy { it.lowercase() }

    fun addCustomTopic(context: Context, topic: String): List<String> {
        val clean = topic.trim().take(40)
        if (clean.isBlank()) return customTopics(context)
        val existing = customTopics(context)
        val result = if (existing.any { it.equals(clean, ignoreCase = true) }) existing else existing + clean
        prefs(context).edit().putString(CUSTOM_KEY, JSONArray(result).toString()).apply()
        return result
    }

    fun recentIds(context: Context): List<String> = stringList(context, HISTORY_KEY)
    fun favoriteIds(context: Context): Set<String> = stringSet(context, FAVORITES_KEY)
    fun isExpanded(context: Context): Boolean = prefs(context).getBoolean(EXPANDED_KEY, false)

    fun setExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(EXPANDED_KEY, expanded).apply()
    }

    fun recordViewed(context: Context, quote: ClarityQuote): List<String> {
        val updated = (listOf(quote.id) + recentIds(context).filterNot { it == quote.id })
            .take(MAX_RECENT)
        prefs(context).edit().putString(HISTORY_KEY, JSONArray(updated).toString()).apply()
        return updated
    }

    fun toggleFavorite(context: Context, quoteId: String): Set<String> {
        val favorites = favoriteIds(context).toMutableSet()
        if (!favorites.add(quoteId)) favorites.remove(quoteId)
        prefs(context).edit().putString(FAVORITES_KEY, JSONArray(favorites.toList()).toString()).apply()
        return favorites
    }

    fun toggleTopic(selected: Set<String>, topicId: String): Set<String> =
        if (topicId == ALL_TOPICS) setOf(ALL_TOPICS)
        else (selected - ALL_TOPICS).let { current ->
            if (topicId in current) current - topicId else current + topicId
        }.ifEmpty { setOf(ALL_TOPICS) }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stringList(context: Context, key: String): List<String> = try {
        val array = JSONArray(prefs(context).getString(key, "[]"))
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (error: JSONException) {
        Log.e("QuoteLibrary", "Ignoring invalid local quote preference: $key", error)
        emptyList()
    }

    private fun stringSet(context: Context, key: String): Set<String> = stringList(context, key).toSet()
}
