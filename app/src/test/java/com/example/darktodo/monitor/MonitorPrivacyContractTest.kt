package com.example.darktodo.monitor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPrivacyContractTest {
    private fun source(path: String): String {
        val main = File("src/main/java/com/example/darktodo").let {
            if (it.isDirectory) it else File("app/src/main/java/com/example/darktodo")
        }
        return File(main, path).readText()
    }

    @Test fun snapshotIsPersistedOnlyInNoBackupStorage() {
        val repository = source("monitor/MonitorRepository.kt")
        assertTrue(Regex("""AtomicFile\s*\(\s*File\s*\(\s*context\.noBackupFilesDir\s*,\s*"monitor-snapshot\.json"""")
            .containsMatchIn(repository))
        assertFalse(repository.contains("getSharedPreferences"))
    }

    @Test fun cloudBackupIncludesOnlyPortableMonitorStateAndNotReportsOrCaches() {
        val backup = source("backup/BackupManager.kt")
        assertTrue(source("backup/BackupSnapshot.kt").contains("\"system_monitor_widget_appearance\""))
        assertTrue(backup.contains("MonitorRepository.backupBytes(context)"))
        assertTrue(backup.contains("MonitorRepository.restoreBytes(context"))
        val blobs = backup.substringAfter("private fun blobFiles(").substringBefore("/** Replaces local data")
        val fixedNames = Regex(""""([^"]+)"\s+to\s+""")
            .findAll(blobs.substringBefore("val fontPrefs")).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("vault", "wallpaper_todo", "wallpaper_calendar", "wallpaper_system_monitor"), fixedNames)
        assertTrue(blobs.contains("""files["wallpaper_note_${'$'}{note.id}"]"""))
        assertFalse(blobs.contains("report", ignoreCase = true))
        assertFalse(blobs.contains("listFiles"))
        assertFalse(blobs.contains(".walk"))
        assertFalse(blobs.contains("cacheDir"))
        assertFalse(source("monitor/MonitorRepository.kt").contains("BackupScheduler.requestBackup"))
    }

    @Test fun reportsWriteOnlyToUserSelectedDestinationWithoutPrivateCopies() {
        val reports = source("monitor/MonitorReports.kt")
        assertTrue(reports.contains("""context.contentResolver.openOutputStream(uri, "wt")"""))
        assertFalse(reports.contains("context.filesDir"))
        assertFalse(reports.contains("context.noBackupFilesDir"))
        assertFalse(reports.contains("getSharedPreferences"))
    }
}
