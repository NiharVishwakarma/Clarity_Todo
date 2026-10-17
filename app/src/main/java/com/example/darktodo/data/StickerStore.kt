package com.example.darktodo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class StickerColor(val label: String, val background: Long, val ink: Long) {
    Yellow("Yellow", 0xFFFFE680, 0xFF3B3200),
    Blue("Blue", 0xFF9CCBFF, 0xFF00294D),
    Green("Green", 0xFFA8E6A1, 0xFF0B3310),
    Pink("Pink", 0xFFFFB3D0, 0xFF4D0A26),
    Purple("Purple", 0xFFD0B8FF, 0xFF2A0F5C);

    companion object {
        fun fromName(name: String?): StickerColor = entries.firstOrNull { it.name == name } ?: Yellow
    }
}

// Reminders, fonts, categories and widget integration are planned; add fields here (with defaults
// in StickerCodec) rather than changing the storage key.
data class Sticker(
    val id: String,
    val title: String,
    val content: String,
    val color: StickerColor = StickerColor.Yellow,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Pure list operations and JSON mapping, kept free of Android types so they are unit-testable. */
object StickerOps {
    fun display(stickers: List<Sticker>): List<Sticker> =
        stickers.sortedWith(compareByDescending<Sticker> { it.pinned }.thenByDescending { it.updatedAt })

    fun create(title: String, content: String, color: StickerColor, now: Long, id: String = UUID.randomUUID().toString()): Sticker? {
        if (title.isBlank() && content.isBlank()) return null
        return Sticker(id, title.trim(), content.trim(), color, false, now, now)
    }

    fun edit(stickers: List<Sticker>, id: String, title: String, content: String, color: StickerColor, now: Long): List<Sticker> {
        if (title.isBlank() && content.isBlank()) return stickers
        return stickers.map { if (it.id == id) it.copy(title = title.trim(), content = content.trim(), color = color, updatedAt = now) else it }
    }

    fun setPinned(stickers: List<Sticker>, id: String, pinned: Boolean): List<Sticker> =
        stickers.map { if (it.id == id && it.pinned != pinned) it.copy(pinned = pinned) else it }

    fun delete(stickers: List<Sticker>, id: String): List<Sticker> = stickers.filterNot { it.id == id }

    fun encode(stickers: List<Sticker>): String = JSONArray().also { array ->
        stickers.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                .put("color", it.color.name).put("pinned", it.pinned).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
        }
    }.toString()

    fun decode(json: String?): List<Sticker> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                Sticker(id, o.optString("title"), o.optString("content"), StickerColor.fromName(o.optString("color")),
                    o.optBoolean("pinned"), o.optLong("createdAt"), o.optLong("updatedAt"))
            }
        }.getOrDefault(emptyList())
    }
}

object StickerStore {
    private const val PREFS = "sticker_store"
    private const val KEY = "stickers_v1"
    private val lock = Any()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Sticker> = synchronized(lock) { StickerOps.decode(prefs(context).getString(KEY, null)) }

    private fun save(context: Context, stickers: List<Sticker>) {
        check(prefs(context).edit().putString(KEY, StickerOps.encode(stickers)).commit()) { "Could not persist stickers" }
    }

    fun add(context: Context, title: String, content: String, color: StickerColor): List<Sticker> = synchronized(lock) {
        val current = load(context)
        val sticker = StickerOps.create(title, content, color, System.currentTimeMillis()) ?: return current
        (current + sticker).also { save(context, it) }
    }

    fun edit(context: Context, id: String, title: String, content: String, color: StickerColor): List<Sticker> = synchronized(lock) {
        StickerOps.edit(load(context), id, title, content, color, System.currentTimeMillis()).also { save(context, it) }
    }

    fun setPinned(context: Context, id: String, pinned: Boolean): List<Sticker> = synchronized(lock) {
        StickerOps.setPinned(load(context), id, pinned).also { save(context, it) }
    }

    fun delete(context: Context, id: String): List<Sticker> = synchronized(lock) {
        StickerOps.delete(load(context), id).also { save(context, it) }
    }
}
