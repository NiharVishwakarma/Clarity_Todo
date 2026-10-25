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

/**
 * A note. The type, storage keys and JSON names still say "sticker" so data saved by earlier versions loads
 * unchanged; only user-facing text says "Note". Unknown stored fields (such as the removed "pinned") are ignored.
 */
data class Sticker(
    val id: String,
    val title: String,
    val content: String,
    val color: StickerColor = StickerColor.Yellow,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val style: StickerStyle? = null
)

/** Pure list operations and JSON mapping, kept free of Android types so they are unit-testable. */
object StickerOps {
    fun display(stickers: List<Sticker>): List<Sticker> = stickers.sortedByDescending { it.updatedAt }

    fun create(title: String, content: String, color: StickerColor, now: Long, id: String = UUID.randomUUID().toString(), style: StickerStyle? = null): Sticker? {
        if (title.isBlank() && content.isBlank()) return null
        return Sticker(id, title.trim(), content.trim(), color, now, now, style)
    }

    fun edit(stickers: List<Sticker>, id: String, title: String, content: String, color: StickerColor, now: Long): List<Sticker> {
        if (title.isBlank() && content.isBlank()) return stickers
        return stickers.map { if (it.id == id) it.copy(title = title.trim(), content = content.trim(), color = color, updatedAt = now) else it }
    }

    fun rename(stickers: List<Sticker>, id: String, title: String, now: Long): List<Sticker> {
        if (title.isBlank()) return stickers
        return stickers.map { if (it.id == id) it.copy(title = title.trim(), updatedAt = now) else it }
    }

    fun duplicate(stickers: List<Sticker>, id: String, newId: String, now: Long): List<Sticker> {
        val source = stickers.firstOrNull { it.id == id } ?: return stickers
        val title = if (source.title.isBlank()) "" else "${source.title} (copy)"
        return stickers + source.copy(id = newId, title = title, createdAt = now, updatedAt = now)
    }

    fun setStyle(stickers: List<Sticker>, id: String, style: StickerStyle?): List<Sticker> =
        stickers.map { if (it.id == id) it.copy(style = style) else it }

    fun delete(stickers: List<Sticker>, id: String): List<Sticker> = stickers.filterNot { it.id == id }

    fun encode(stickers: List<Sticker>): String = JSONArray().also { array ->
        stickers.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                .put("color", it.color.name).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).also { o -> it.style?.let { s -> o.put("style", s.toJson()) } })
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
                    o.optLong("createdAt"), o.optLong("updatedAt"), StickerStyle.fromJson(o.optJSONObject("style")))
            }
        }.getOrDefault(emptyList())
    }
}

object StickerStore {
    private const val PREFS = "sticker_store"
    private const val KEY = "stickers_v1"
    private const val KEY_STYLE = "sticker_style_v1"
    private const val KEY_COLORS = "sticker_color_book"
    private const val KEY_MIGRATED = "notes_per_note_v1"
    private val lock = Any()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Sticker> = synchronized(lock) {
        migrate(context)
        StickerOps.decode(prefs(context).getString(KEY, null))
    }

    /**
     * One-time move from the old shared look (global style + one shared background image) to a full style and
     * image per note. Each note keeps exactly the look it had, and text colour stays manual until the user opts in.
     */
    private fun migrate(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val global = globalStyle(context)
        val migrated = StickerOps.decode(p.getString(KEY, null)).map { note ->
            val own = note.style
            val look = if (own != null) global.copy(background = own.background, text = own.text) else StickerStyle.resolve(note, global)
            note.copy(style = look.copy(autoContrast = false, textShadow = false, glow = false))
        }
        if (WidgetBackgrounds.has(context, WidgetBackgrounds.STICKER)) {
            migrated.forEach { WidgetBackgrounds.copy(context, WidgetBackgrounds.STICKER, WidgetBackgrounds.noteKey(it.id)) }
            WidgetBackgrounds.clear(context, WidgetBackgrounds.STICKER)
        }
        p.edit().putString(KEY, StickerOps.encode(migrated)).putBoolean(KEY_MIGRATED, true).commit()
    }

    private fun save(context: Context, notes: List<Sticker>) {
        check(prefs(context).edit().putString(KEY, StickerOps.encode(notes)).commit()) { "Could not persist notes" }
        runCatching { com.example.darktodo.widget.StickerWidget.updateAll(context) }
            .onFailure { android.util.Log.e("StickerStore", "Could not refresh note widgets after saving", it) }
    }

    fun add(context: Context, title: String, content: String, style: StickerStyle? = null): List<Sticker> = synchronized(lock) {
        val current = load(context)
        val base = style?.sanitized() ?: StickerStyle.resolve(Sticker("", "", ""), globalStyle(context)).copy(autoContrast = true)
        val note = StickerOps.create(title, content, StickerColor.Yellow, System.currentTimeMillis(), style = base) ?: return current
        (current + note).also { save(context, it) }
    }

    fun edit(context: Context, id: String, title: String, content: String, style: StickerStyle? = null): List<Sticker> = synchronized(lock) {
        val current = load(context)
        val color = current.firstOrNull { it.id == id }?.color ?: StickerColor.Yellow
        val edited = StickerOps.edit(current, id, title, content, color, System.currentTimeMillis())
        (if (style == null) edited else StickerOps.setStyle(edited, id, style.sanitized())).also { save(context, it) }
    }

    fun rename(context: Context, id: String, title: String): List<Sticker> = synchronized(lock) {
        StickerOps.rename(load(context), id, title, System.currentTimeMillis()).also { save(context, it) }
    }

    fun duplicate(context: Context, id: String): List<Sticker> = synchronized(lock) {
        val current = load(context)
        val newId = UUID.randomUUID().toString()
        val updated = StickerOps.duplicate(current, id, newId, System.currentTimeMillis())
        if (updated.size == current.size) return current
        WidgetBackgrounds.copy(context, WidgetBackgrounds.noteKey(id), WidgetBackgrounds.noteKey(newId))
        updated.also { save(context, it) }
    }

    fun delete(context: Context, id: String): List<Sticker> = synchronized(lock) {
        val updated = StickerOps.delete(load(context), id)
        WidgetBackgrounds.clear(context, WidgetBackgrounds.noteKey(id))
        updated.also { save(context, it) }
    }

    fun setStyle(context: Context, id: String, style: StickerStyle): List<Sticker> = synchronized(lock) {
        StickerOps.setStyle(load(context), id, style.sanitized()).also { save(context, it) }
    }

    /** Former app-wide style; still the base for any note that has no style of its own. */
    fun globalStyle(context: Context): StickerStyle = StickerStyle.fromJson(
        prefs(context).getString(KEY_STYLE, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    ) ?: StickerStyle.DEFAULT

    fun styleFor(context: Context, note: Sticker): StickerStyle = StickerStyle.resolve(note, globalStyle(context))

    fun colorBook(context: Context): StickerColorBook = StickerColorBook.decode(prefs(context).getString(KEY_COLORS, null))

    fun saveColorBook(context: Context, book: StickerColorBook) {
        prefs(context).edit().putString(KEY_COLORS, book.encode()).apply()
    }
}
