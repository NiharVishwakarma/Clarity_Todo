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

    fun create(title: String, content: String, color: StickerColor, now: Long, id: String = UUID.randomUUID().toString(), style: StickerStyle? = null): Sticker? {
        if (title.isBlank() && content.isBlank()) return null
        return Sticker(id, title.trim(), content.trim(), color, false, now, now, style)
    }

    fun edit(stickers: List<Sticker>, id: String, title: String, content: String, color: StickerColor, now: Long): List<Sticker> {
        if (title.isBlank() && content.isBlank()) return stickers
        return stickers.map { if (it.id == id) it.copy(title = title.trim(), content = content.trim(), color = color, updatedAt = now) else it }
    }

    fun setPinned(stickers: List<Sticker>, id: String, pinned: Boolean): List<Sticker> =
        stickers.map { if (it.id == id && it.pinned != pinned) it.copy(pinned = pinned) else it }

    fun setStyle(stickers: List<Sticker>, id: String, style: StickerStyle?): List<Sticker> =
        stickers.map { if (it.id == id) it.copy(style = style) else it }

    fun delete(stickers: List<Sticker>, id: String): List<Sticker> = stickers.filterNot { it.id == id }

    fun encode(stickers: List<Sticker>): String = JSONArray().also { array ->
        stickers.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                .put("color", it.color.name).put("pinned", it.pinned).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).also { o -> it.style?.let { s -> o.put("style", s.toJson()) } })
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
                    o.optBoolean("pinned"), o.optLong("createdAt"), o.optLong("updatedAt"), StickerStyle.fromJson(o.optJSONObject("style")))
            }
        }.getOrDefault(emptyList())
    }
}

object StickerStore {
    private const val PREFS = "sticker_store"
    private const val KEY = "stickers_v1"
    private const val KEY_STYLE = "sticker_style_v1"
    private const val KEY_SAVE_DEFAULT = "sticker_save_default"
    private const val KEY_COLORS = "sticker_color_book"
    private val lock = Any()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Sticker> = synchronized(lock) { StickerOps.decode(prefs(context).getString(KEY, null)) }

    private fun save(context: Context, stickers: List<Sticker>) {
        check(prefs(context).edit().putString(KEY, StickerOps.encode(stickers)).commit()) { "Could not persist stickers" }
        runCatching { com.example.darktodo.widget.StickerWidget.updateAll(context) }
    }

    fun add(context: Context, title: String, content: String, color: StickerColor): List<Sticker> = synchronized(lock) {
        val current = load(context)
        val inherited = if (saveAsDefault(context)) globalStyle(context) else null
        val sticker = StickerOps.create(title, content, color, System.currentTimeMillis(), style = inherited) ?: return current
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

    fun setStyle(context: Context, id: String, style: StickerStyle?): List<Sticker> = synchronized(lock) {
        StickerOps.setStyle(load(context), id, style?.sanitized()).also { save(context, it) }
    }

    fun globalStyle(context: Context): StickerStyle = StickerStyle.fromJson(
        prefs(context).getString(KEY_STYLE, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    ) ?: StickerStyle.DEFAULT

    fun saveGlobalStyle(context: Context, style: StickerStyle) {
        prefs(context).edit().putString(KEY_STYLE, style.sanitized().toJson().toString()).apply()
        runCatching { com.example.darktodo.widget.StickerWidget.updateAll(context) }
    }

    /** When true, new stickers copy the global style (colours included) instead of using a preset colour. */
    fun saveAsDefault(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE_DEFAULT, false)

    fun setSaveAsDefault(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVE_DEFAULT, enabled).apply()
    }

    fun colorBook(context: Context): StickerColorBook = StickerColorBook.decode(prefs(context).getString(KEY_COLORS, null))

    fun saveColorBook(context: Context, book: StickerColorBook) {
        prefs(context).edit().putString(KEY_COLORS, book.encode()).apply()
    }
}
