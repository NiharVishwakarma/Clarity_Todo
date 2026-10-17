package com.example.darktodo.data

import android.content.Context
import org.json.JSONObject

/**
 * "Save As Default Style" for widgets that have a fixed number of instances (Tasks, Calendar):
 * snapshots every customization key of a settings file so it can be restored later.
 */
object StyleSnapshot {
    private const val STORE = "style_snapshots"

    private fun owned(key: String, prefix: String) = key.startsWith(prefix)

    fun save(context: Context, snapshotName: String, file: String, prefix: String) {
        val source = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
        val json = JSONObject()
        source.all.forEach { (key, value) ->
            if (!owned(key, prefix) || value == null) return@forEach
            val type = when (value) { is String -> "s"; is Int -> "i"; is Float -> "f"; is Boolean -> "b"; else -> return@forEach }
            json.put(key, JSONObject().put("t", type).put("v", value))
        }
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(snapshotName, json.toString()).apply()
    }

    fun has(context: Context, snapshotName: String): Boolean =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE).contains(snapshotName)

    /** Returns false when nothing was saved or the snapshot is unreadable; settings are untouched in that case. */
    fun restore(context: Context, snapshotName: String, file: String, prefix: String): Boolean {
        val raw = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(snapshotName, null) ?: return false
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val target = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
        val editor = target.edit()
        target.all.keys.filter { owned(it, prefix) }.forEach { editor.remove(it) }
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            when (entry.optString("t")) {
                "s" -> editor.putString(key, entry.optString("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
            }
        }
        editor.apply()
        return true
    }

    const val TODO = "todo_widget_default"
    const val CALENDAR = "calendar_widget_default"
}
