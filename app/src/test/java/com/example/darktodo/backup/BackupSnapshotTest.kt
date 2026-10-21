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
        listOf("sticker_store", "widget_fonts", "system_monitor_widget_appearance",
            "vault_widget_appearance", "style_snapshots", "navigation_prefs").forEach {
            source.getJSONObject("prefs").remove(it)
        }
        val plan = BackupSnapshot.decode(source.toString())
        assertFalse(plan.prefs.containsKey("sticker_store"))
        assertFalse(plan.prefs.containsKey("widget_fonts"))
        assertFalse(plan.prefs.containsKey("system_monitor_widget_appearance"))
    }

    private fun portableSnapshot() = snapshot().put("version", 2).put("assets", JSONObject())

    private fun asset(source: JSONObject, name: String, bytes: ByteArray) {
        source.getJSONObject("files").put(name, Base64.getEncoder().encodeToString(bytes))
        source.getJSONObject("assets").put(name, JSONObject().put("size", bytes.size)
            .put("sha256", BackupHistory.digest(bytes)))
    }

    private fun registerFont(source: JSONObject, id: String = "abc-123.ttf") {
        source.getJSONObject("prefs").put("widget_fonts", BackupCodec.encodePrefs(mapOf(
            "imported_fonts" to JSONArray().put(JSONObject().put("id", id).put("name", "Example Bold")
                .put("variant", "Bold")).toString()
        )))
    }

    @Test fun portableAssetsRetainFontIdsNamesMetadataAndEveryAppearanceValue() {
        val source = portableSnapshot()
        registerFont(source)
        val font = ByteBuffer.allocate(12).putInt(0x00010000).array()
        asset(source, "font_abc-123.ttf", font)
        val wallpaper = byteArrayOf(1, 2, 3)
        asset(source, "wallpaper_system_monitor", wallpaper)
        val monitor = """{"capturedAt":1000,"values":{"Model":"Original device","RAM used":"1 GB"}}""".toByteArray()
        asset(source, "monitor_snapshot", monitor)
        val appearance = mapOf("background_color" to "#000000", "card_opacity" to 59,
            "font_file_id" to "abc-123.ttf", "wallpaper_enabled" to true)
        source.getJSONObject("prefs").put("system_monitor_widget_appearance", BackupCodec.encodePrefs(appearance))
            .put("vault_widget_appearance", BackupCodec.encodePrefs(appearance))
        val first = BackupSnapshot.decode(source.toString())
        val second = BackupSnapshot.decode(source.toString())
        assertEquals(appearance, first.prefs.getValue("system_monitor_widget_appearance"))
        assertEquals(appearance, first.prefs.getValue("vault_widget_appearance"))
        assertEquals(setOf("abc-123.ttf"), BackupSnapshot.fontIds(first.prefs))
        assertTrue(first.prefs.getValue("widget_fonts").getValue("imported_fonts").toString().contains("\"variant\":\"Bold\""))
        assertTrue(font.contentEquals(first.files.getValue("font_abc-123.ttf")))
        assertTrue(wallpaper.contentEquals(first.files.getValue("wallpaper_system_monitor")))
        assertTrue(monitor.contentEquals(first.files.getValue("monitor_snapshot")))
        assertEquals(first.prefs, second.prefs)
        assertEquals(first.files.keys, second.files.keys)
    }

    @Test fun rejectsMissingFontsInvalidIdsDuplicateRegistryAndInvalidFontData() {
        val missing = portableSnapshot()
        registerFont(missing)
        expectFailure { BackupSnapshot.decode(missing.toString()) }
        val unsafe = portableSnapshot()
        registerFont(unsafe, "../abc.ttf")
        expectFailure { BackupSnapshot.decode(unsafe.toString()) }
        val duplicate = portableSnapshot()
        val record = JSONObject().put("id", "abc.ttf").put("name", "Example")
        duplicate.getJSONObject("prefs").put("widget_fonts", BackupCodec.encodePrefs(mapOf(
            "imported_fonts" to JSONArray().put(record).put(record).toString())))
        expectFailure { BackupSnapshot.decode(duplicate.toString()) }
        val invalid = portableSnapshot()
        registerFont(invalid)
        asset(invalid, "font_abc-123.ttf", ByteArray(12))
        expectFailure { BackupSnapshot.decode(invalid.toString()) }
    }

    @Test fun rejectsMismatchedManifestAndMissingNewPreferenceStores() {
        val source = portableSnapshot()
        asset(source, "wallpaper_todo", byteArrayOf(1, 2))
        source.getJSONObject("assets").getJSONObject("wallpaper_todo").put("sha256", "0".repeat(64))
        expectFailure { BackupSnapshot.decode(source.toString()) }
        source.getJSONObject("assets").remove("wallpaper_todo")
        expectFailure { BackupSnapshot.decode(source.toString()) }
        expectFailure { BackupSnapshot.decode(portableSnapshot().apply {
            getJSONObject("prefs").remove("widget_fonts")
        }.toString()) }
    }

    @Test fun rejectsDanglingFontSelectionsInWidgetsNotesAndSavedStyles() {
        listOf("system_monitor_widget_appearance", "vault_widget_appearance", "app_typography").forEach { name ->
            val source = portableSnapshot()
            source.getJSONObject("prefs").put(name, BackupCodec.encodePrefs(mapOf("font_file_id" to "abc.ttf")))
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
        val note = portableSnapshot()
        note.getJSONObject("prefs").put("sticker_store", BackupCodec.encodePrefs(mapOf(
            "stickers_v1" to """[{"id":"note-1","style":{"fontFileId":"abc.ttf"}}]""")))
        expectFailure { BackupSnapshot.decode(note.toString()) }
        val style = portableSnapshot()
        style.getJSONObject("prefs").put("style_snapshots", BackupCodec.encodePrefs(mapOf(
            "todo_widget_default" to BackupCodec.encodePrefs(mapOf("todo_widget_font_file_id" to "abc.ttf")).toString())))
        expectFailure { BackupSnapshot.decode(style.toString()) }
    }

    @Test fun rejectsInvalidManifestSizesAndUnexpectedManifestEntries() {
        listOf<Any>(1, 2.5, "2").forEach { size ->
            val source = portableSnapshot()
            asset(source, "wallpaper_todo", byteArrayOf(1, 2))
            source.getJSONObject("assets").getJSONObject("wallpaper_todo").put("size", size)
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
        val extra = portableSnapshot()
        extra.getJSONObject("assets").put("missing_asset", JSONObject())
        expectFailure { BackupSnapshot.decode(extra.toString()) }
    }

    @Test fun preservesDistinctLegacyFontIdsEvenWhenTheirContentsMatch() {
        val source = portableSnapshot()
        val registry = JSONArray()
        val bytes = ByteBuffer.allocate(12).putInt(0x00010000).array()
        listOf("abc.ttf", "def.ttf").forEach { id ->
            registry.put(JSONObject().put("id", id).put("name", "Shared name"))
            asset(source, "font_$id", bytes)
        }
        source.getJSONObject("prefs").put("widget_fonts", BackupCodec.encodePrefs(mapOf(
            "imported_fonts" to registry.toString())))
        source.getJSONObject("prefs").put("app_typography", BackupCodec.encodePrefs(mapOf("font_file_id" to "def.ttf")))
        val plan = BackupSnapshot.decode(source.toString())
        assertEquals(setOf("abc.ttf", "def.ttf"), BackupSnapshot.fontIds(plan.prefs))
        assertEquals("def.ttf", plan.prefs.getValue("app_typography")["font_file_id"])
        assertEquals(setOf("font_abc.ttf", "font_def.ttf"), plan.files.keys)
    }

    @Test fun preservesNoteTextThatLooksLikeJsonWithoutInterpretingItAsMetadata() {
        val source = portableSnapshot()
        source.getJSONObject("prefs").put("sticker_store", BackupCodec.encodePrefs(mapOf(
            "stickers_v1" to """[{"id":"note-1","content":"{not JSON"}]""")))
        BackupSnapshot.decode(source.toString())
    }

    @Test fun rejectsCorruptMonitorSnapshotsAndForbiddenDeviceBoundSecrets() {
        listOf("""{"capturedAt":0,"values":{}}""", """{"capturedAt":1,"values":{"RAM":12}}""", "{").forEach {
            val source = portableSnapshot()
            asset(source, "monitor_snapshot", it.toByteArray())
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
        listOf("vault_biometric", "clarity-vault-biometric.bin", "download_file", "cache_file").forEach {
            val source = portableSnapshot()
            asset(source, it, byteArrayOf(1))
            expectFailure { BackupSnapshot.decode(source.toString()) }
        }
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
