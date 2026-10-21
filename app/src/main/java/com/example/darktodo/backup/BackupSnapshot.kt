package com.example.darktodo.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64

/** Fully decodes a restore plan before any preferences or files may be modified. */
internal object BackupSnapshot {
    private val legacyPreferences = listOf("todo_store", "calendar_widget_settings", "app_typography", "widget_backgrounds", "vault_auth")
    val preferenceFiles = legacyPreferences + listOf(
        "sticker_store", "widget_fonts", "system_monitor_widget_appearance",
        "vault_widget_appearance", "style_snapshots", "navigation_prefs"
    )
    private val fontIdPattern = Regex("^[a-f0-9-]+\\.(ttf|otf|ttc)$")
    const val MAX_FONT_BYTES = 20 * 1024 * 1024
    data class Plan(val prefs: Map<String, Map<String, Any>>, val files: Map<String, ByteArray>)

    fun decode(bytes: ByteArray): Plan {
        val text = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IOException("The backup contains invalid text encoding. Nothing was restored.", error)
        }
        return decode(text)
    }

    fun decode(json: String): Plan = try {
        val root = JSONObject(json)
        val version = root.get("version")
        if (version !is Number || version.toDouble() != version.toInt().toDouble() ||
            version.toInt() !in 1..BackupCodec.VERSION) throw IOException("Unsupported backup format. Update Clarity before restoring.")
        val time = root.get("createdAt")
        if (time !is Number || time.toLong() <= 0) throw IOException("The backup creation timestamp is missing or invalid.")
        val prefs = root.getJSONObject("prefs")
        // The original format predates Notes; those older snapshots may omit sticker_store.
        (if (version.toInt() >= 2) preferenceFiles else legacyPreferences).forEach {
            if (!prefs.has(it)) throw IOException("The backup is incomplete (missing $it). Nothing was restored.")
        }
        val decoded = linkedMapOf<String, Map<String, Any>>()
        preferenceFiles.filter { prefs.has(it) }.forEach { name ->
            decoded[name] = decodePreferences(prefs.getJSONObject(name))
        }
        listOf("active_tasks_v1", "task_history_v1").forEach { key ->
            decoded["todo_store"]?.get(key)?.let { value ->
                if (value !is String) throw IOException("The backup task data is invalid.")
                val array = JSONArray(value)
                val ids = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val strings = if (key == "active_tasks_v1") listOf("id", "title") else listOf("id", "taskId", "title", "type")
                    if (strings.any { item.get(it) !is String } || item.getString("id").isBlank() || !ids.add(item.getString("id"))) {
                        throw IOException("The backup task records are incomplete or invalid.")
                    }
                    if (key == "active_tasks_v1") {
                        if (item.get("completed") !is Boolean) throw IOException("The backup task completion state is invalid.")
                    } else {
                        val at = item.get("at")
                        if (at !is Number || at.toDouble() != at.toLong().toDouble()) throw IOException("The backup task history date is invalid.")
                    }
                }
            }
        }
        val noteIds = mutableSetOf<String>()
        decoded["sticker_store"]?.get("stickers_v1")?.let { value ->
            if (value !is String) throw IOException("The backup note data is invalid.")
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val id = array.getJSONObject(i).getString("id")
                if (!id.matches(Regex("[A-Za-z0-9-]+")) || !noteIds.add(id)) throw IOException("The backup note IDs are invalid.")
            }
        }
        val fontIds = fontIds(decoded)
        val allowed = setOf("vault", "wallpaper_todo", "wallpaper_calendar") +
            noteIds.map { "wallpaper_note_$it" } +
            if (version.toInt() >= 2) setOf("wallpaper_system_monitor", "monitor_snapshot") + fontIds.map { "font_$it" } else emptySet()
        val files = root.getJSONObject("files")
        val blobs = linkedMapOf<String, ByteArray>()
        files.keys().forEach { name ->
            if (name !in allowed) throw IOException("The backup contains an unsupported file reference.")
            val value = files.get(name)
            if (value !is String) throw IOException("The backup contains a damaged file.")
            val bytes = Base64.getDecoder().decode(value)
            if (bytes.isEmpty()) throw IOException("The backup contains an empty file.")
            if (name == "vault" && (bytes.size < 48 || ByteBuffer.wrap(bytes).int != 0x434c5654)) {
                throw IOException("The encrypted vault file is damaged. Nothing was restored.")
            }
            if (name.startsWith("font_")) validateFontHeader(bytes)
            if (name == "monitor_snapshot") validateMonitorSnapshot(bytes)
            blobs[name] = bytes
        }
        if (version.toInt() >= 2) {
            val manifest = root.getJSONObject("assets")
            if (manifest.keys().asSequence().toSet() != blobs.keys) throw IOException("The asset manifest is incomplete.")
            blobs.forEach { (name, bytes) ->
                val entry = manifest.getJSONObject(name)
                val size = entry.get("size")
                if (size !is Number || size.toDouble() != bytes.size.toDouble() ||
                    entry.getString("sha256") != BackupHistory.digest(bytes)) {
                    throw IOException("A backed-up asset failed integrity validation: $name")
                }
            }
            fontIds.forEach {
                if ("font_$it" !in blobs) throw IOException("An imported font is missing from the backup: $it")
            }
            validateFontReferences(decoded, fontIds)
        }
        Plan(decoded, blobs)
    } catch (error: JSONException) {
        throw IOException("The backup is incomplete or damaged. Nothing was restored.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("The backup contains invalid encoded data. Nothing was restored.", error)
    }

    fun fontIds(prefs: Map<String, Map<String, Any>>): Set<String> {
        val raw = prefs["widget_fonts"]?.get("imported_fonts") ?: return emptySet()
        if (raw !is String) throw IOException("The imported font registry is invalid.")
        val registry = JSONArray(raw)
        val ids = linkedSetOf<String>()
        for (index in 0 until registry.length()) {
            val font = registry.getJSONObject(index)
            val id = font.getString("id")
            if (!fontIdPattern.matches(id) || font.getString("name").isBlank() || !ids.add(id)) {
                throw IOException("The imported font registry contains an invalid or duplicate record.")
            }
        }
        return ids
    }

    private fun validateFontReferences(prefs: Map<String, Map<String, Any>>, ids: Set<String>) {
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    val item = value.get(key)
                    if ((key.endsWith("font_file_id") || key == "fontFileId") &&
                        item is String && item.isNotBlank() && item !in ids) {
                        throw IOException("A selected imported font has no backed-up registry entry.")
                    }
                    visit(item)
                }
                is JSONArray -> (0 until value.length()).forEach { visit(value.get(it)) }
            }
        }
        prefs.forEach { (name, values) ->
            values.forEach { (key, value) ->
                if (key.endsWith("font_file_id") && value is String && value.isNotBlank() && value !in ids) {
                    throw IOException("A selected imported font is missing: $value")
                }
                if ((name == "sticker_store" && key == "stickers_v1") || name == "style_snapshots") {
                    // Do not interpret note text or arbitrary preference strings as JSON.
                    val json = if (name == "style_snapshots" && value is String)
                        JSONObject(decodePreferences(JSONObject(value)))
                    else if (value is String && value.startsWith("[")) JSONArray(value) else null
                    if (json != null) visit(json)
                }
            }
        }
    }

    private fun validateFontHeader(bytes: ByteArray) {
        if (bytes.size !in 12..MAX_FONT_BYTES) throw IOException("An imported font has an invalid size.")
        if (ByteBuffer.wrap(bytes).int !in setOf(0x00010000, 0x4F54544F, 0x74746366, 0x74727565)) {
            throw IOException("An imported font has an invalid format.")
        }
    }

    fun validateMonitorSnapshot(bytes: ByteArray) {
        val json = JSONObject(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
        val capturedAt = json.get("capturedAt")
        if (capturedAt !is Number || capturedAt.toLong() <= 0 ||
            capturedAt.toDouble() != capturedAt.toLong().toDouble()) throw IOException("The monitor snapshot timestamp is invalid.")
        val values = json.getJSONObject("values")
        if (values.keys().asSequence().any { values.get(it) !is String }) throw IOException("The monitor snapshot values are invalid.")
    }

    private fun decodePreferences(json: JSONObject): Map<String, Any> {
        json.keys().forEach { key ->
            val entry = json.getJSONObject(key)
            val value = entry.get("v")
            val valid = when (entry.getString("t")) {
                "s" -> value is String
                "b" -> value is Boolean
                "i" -> value is Number && value.toDouble() == value.toInt().toDouble()
                "l" -> value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble()
                "f" -> value is Number && value.toFloat().isFinite()
                "ss" -> value is JSONArray && (0 until value.length()).all { value.get(it) is String }
                else -> false
            }
            if (!valid) throw IOException("The backup contains invalid preference data. Nothing was restored.")
        }
        return BackupCodec.decodePrefs(json)
    }
}
