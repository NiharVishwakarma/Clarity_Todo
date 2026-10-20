package com.example.darktodo.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64

class BackupSnapshotTest {
    private fun snapshot(): JSONObject {
        val prefs = JSONObject()
        BackupSnapshot.preferenceFiles.forEach { prefs.put(it, JSONObject()) }
        return JSONObject().put("version", 1).put("createdAt", 1000).put("prefs", prefs).put("files", JSONObject())
    }

    @Test fun validatesCompleteVersionOneAndRetainsPreferenceTypes() {
        val source = snapshot()
        source.getJSONObject("prefs").put("todo_store", BackupCodec.encodePrefs(mapOf(
            "active_tasks_v1" to """[{"id":"task-1","title":"Task","completed":false}]""",
            "task_history_v1" to """[{"id":"event-1","taskId":"task-1","title":"Task","type":"Created","at":1000}]""",
            "count" to 2, "flag" to true, "tags" to setOf("a", "b")
        )))
        val plan = BackupSnapshot.decode(source.toString())
        assertEquals(2, plan.prefs.getValue("todo_store")["count"])
        assertEquals(setOf("a", "b"), plan.prefs.getValue("todo_store")["tags"])
        assertTrue(plan.files.isEmpty())
    }

    @Test fun acceptsLegacySnapshotBeforeNotesWereAdded() {
        val source = snapshot()
        source.getJSONObject("prefs").remove("sticker_store")
        val plan = BackupSnapshot.decode(source.toString())
        assertFalse(plan.prefs.containsKey("sticker_store"))
    }

    @Test fun rejectsMalformedUtf8RatherThanReplacingCorruptedText() {
        BackupSnapshot.decode(snapshot().toString().toByteArray(Charsets.UTF_8))
        expectFailure { BackupSnapshot.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }

    @Test fun rejectsMissingSectionsFilesTimestampAndUnsupportedVersion() {
        expectFailure { BackupSnapshot.decode(snapshot().apply { remove("prefs") }.toString()) }
        expectFailure { BackupSnapshot.decode(snapshot().apply { remove("files") }.toString()) }
        expectFailure { BackupSnapshot.decode(snapshot().apply { remove("createdAt") }.toString()) }
        expectFailure { BackupSnapshot.decode(snapshot().put("version", 999).toString()) }
        expectFailure { BackupSnapshot.decode(snapshot().put("version", 1.5).toString()) }
        expectFailure { BackupSnapshot.decode(snapshot().apply { getJSONObject("prefs").remove("todo_store") }.toString()) }
    }

    @Test fun invalidTypedPreferencesAreNeverSilentlyDefaultedOrSkipped() {
        listOf(
            JSONObject().put("t", "unknown").put("v", 1),
            JSONObject().put("t", "b").put("v", "not-a-boolean"),
            JSONObject().put("t", "i").put("v", 1.1),
            JSONObject().put("t", "i").put("v", Long.MAX_VALUE),
            JSONObject().put("t", "ss").put("v", JSONArray().put(5))
        ).forEach { entry ->
            val source = snapshot()
            source.getJSONObject("prefs").getJSONObject("todo_store").put("key", entry)
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
    }

    @Test fun rejectsCorruptNestedTaskAndNoteJsonBeforeAnyRestorePlanIsReturned() {
        listOf("active_tasks_v1" to "{", "task_history_v1" to "[5]").forEach { (key, value) ->
            val source = snapshot()
            source.getJSONObject("prefs").put("todo_store", BackupCodec.encodePrefs(mapOf(key to value)))
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
        val source = snapshot()
        source.getJSONObject("prefs").put("sticker_store", BackupCodec.encodePrefs(mapOf("stickers_v1" to """[{"id":"../escape"}]""")))
        expectFailure { BackupSnapshot.decode(source.toString()) }
    }

    @Test fun decodesOnlyEncryptedVaultAndWhitelistedNoteImages() {
        val source = snapshot()
        source.getJSONObject("prefs").put("sticker_store", BackupCodec.encodePrefs(mapOf("stickers_v1" to """[{"id":"note-1"}]""")))
        val vault = ByteBuffer.allocate(48).putInt(0x434c5654).array()
        source.getJSONObject("files").put("vault", Base64.getEncoder().encodeToString(vault))
            .put("wallpaper_note_note-1", Base64.getEncoder().encodeToString(byteArrayOf(1, 2)))
        val plan = BackupSnapshot.decode(source.toString())
        assertTrue(vault.contentEquals(plan.files.getValue("vault")))
        assertEquals(2, plan.files.getValue("wallpaper_note_note-1").size)
    }

    @Test fun rejectsIncompleteTaskRecordsAndDuplicateIdsBeforeReplacingData() {
        listOf(
            "active_tasks_v1" to """[{"id":"task-1"}]""",
            "active_tasks_v1" to """[{"id":"task-1","title":"Task","completed":"false"}]""",
            "active_tasks_v1" to """[{"id":"task-1","title":"Task","completed":false},{"id":"task-1","title":"Other","completed":true}]""",
            "task_history_v1" to """[{"id":"event-1","taskId":"task-1","title":"Task","type":"Created","at":"bad"}]"""
        ).forEach { (key, value) ->
            val source = snapshot()
            source.getJSONObject("prefs").put("todo_store", BackupCodec.encodePrefs(mapOf(key to value)))
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
    }

    @Test fun rejectsBadBase64EmptyFilesPlaintextVaultAndUnknownPaths() {
        listOf(
            "wallpaper_todo" to "not base64!",
            "wallpaper_todo" to "",
            "vault" to Base64.getEncoder().encodeToString("plaintext passwords".toByteArray()),
            "../outside" to "AQ==",
            "wallpaper_note_unknown" to "AQ=="
        ).forEach { (name, value) ->
            val source = snapshot()
            source.getJSONObject("files").put(name, value)
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
    }

    private fun expectFailure(block: () -> Unit) {
        try { block(); throw AssertionError("Expected invalid backup to fail.") } catch (_: IOException) { }
    }
}
