package com.example.darktodo.backup

import org.json.JSONArray
import org.json.JSONObject

/** Typed JSON form of SharedPreferences contents so a restore reproduces every value with its original type. */
object BackupCodec {
    const val VERSION = 2

    fun encodePrefs(values: Map<String, *>): JSONObject {
        val out = JSONObject()
        values.forEach { (key, value) ->
            val entry = when (value) {
                is String -> JSONObject().put("t", "s").put("v", value)
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> null
            }
            if (entry != null) out.put(key, entry)
        }
        return out
    }

    fun decodePrefs(json: JSONObject): Map<String, Any> {
        val out = linkedMapOf<String, Any>()
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            val value: Any? = when (entry.optString("t")) {
                "s" -> entry.optString("v")
                "b" -> entry.optBoolean("v")
                "i" -> entry.optInt("v")
                "l" -> entry.optLong("v")
                "f" -> entry.optDouble("v").toFloat()
                "ss" -> entry.optJSONArray("v")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                else -> null
            }
            if (value != null) out[key] = value
        }
        return out
    }
}
