package com.example.darktodo.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64

/** Fully decodes a restore plan before any preferences or files may be modified. */
internal object BackupSnapshot {
    val preferenceFiles = listOf("todo_store", "calendar_widget_settings", "app_typography", "widget_backgrounds", "vault_auth", "sticker_store")
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
        preferenceFiles.filterNot { it == "sticker_store" }.forEach {
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
        val allowed = setOf("vault", "wallpaper_todo", "wallpaper_calendar") + noteIds.map { "wallpaper_note_$it" }
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
            blobs[name] = bytes
        }
        Plan(decoded, blobs)
    } catch (error: JSONException) {
        throw IOException("The backup is incomplete or damaged. Nothing was restored.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("The backup contains invalid encoded data. Nothing was restored.", error)
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
