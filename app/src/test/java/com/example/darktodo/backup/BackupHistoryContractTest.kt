package com.example.darktodo.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHistoryContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()

    @Test fun retriesReserveAnIdAndPersistPayloadBeforeCreatingThenVerifyBeforeRetention() {
        val manager = source("backup/BackupManager.kt")
        val upload = manager.substringAfter("private fun uploadSnapshot")
        assertTrue(upload.indexOf("writeAtomic(journal") < upload.lastIndexOf("finishUpload(context, token, pending)"))
        assertTrue(manager.contains("pending-drive-backup.json"))
        assertTrue(manager.contains("pending.getString(\"operation\") == operationId"))
        assertTrue(manager.contains("history(token).firstOrNull { it.operationId == operationId }"))
        assertTrue(manager.indexOf("uploadSnapshot(context, accessToken") < manager.indexOf("BackupHistory.excess"))
        val finish = manager.substringAfter("private fun finishUpload")
        assertTrue(finish.indexOf("downloaded.contentEquals(bytes)") < finish.indexOf("DriveClient.markVerified"))
        assertTrue(finish.indexOf("DriveClient.markVerified") < finish.indexOf("putLong(\"last\""))
        val drive = source("backup/DriveClient.kt")
        assertTrue(drive.contains("nextPageToken"))
        assertTrue(drive.contains("seenPages.add(page)"))
        assertTrue(drive.contains("metadata(token, id)?.let { return it }"))
        assertFalse(drive.contains("fun find("))
        assertTrue(source("backup/BackupScheduler.kt").contains("automatic = true, operationId = id.toString()"))
    }

    @Test fun selectedRestoreNeverFallsBackToLatestAndCreatesSafetyBeforeWrites() {
        val manager = source("backup/BackupManager.kt")
        val restore = manager.substringAfter("fun restore(").substringBefore("fun deleteBackup")
        assertTrue(restore.contains("firstOrNull { it.id == backupId }"))
        assertFalse(restore.contains("findBackup("))
        val validation = restore.indexOf("BackupSnapshot.decode(bytes)")
        assertTrue(validation >= 0)
        assertTrue(validation < restore.indexOf("uploadSnapshot(context, token, \"safety\""))
        assertTrue(restore.contains("UUID.randomUUID().toString(), safety)"))
        assertTrue(restore.contains("writeAtomic(recovery, safety.toByteArray"))
        assertTrue(restore.indexOf("uploadSnapshot(context, token, \"safety\"") < restore.indexOf("applySnapshot(context, json)"))
        assertFalse(restore.contains("DriveClient.delete"))
        assertFalse(restore.contains("BackupHistory.excess"))
        assertTrue(restore.contains("applySnapshot(context, safety)"))
        assertFalse(restore.contains("putLong(\"last\""))
    }

    @Test fun settingsOffersHistoryMetadataRetryExactSelectionAndExplicitConfirmation() {
        val screen = source("BackupScreens.kt")
        listOf(
            "BackupHistoryDialog", "Backup history", "No backups yet.", "Refresh history",
            "Could not load backup history.", "point.id", "point.size", "point.version",
            "backupDate(point)", "Restore this backup", "selectedId = it.id",
            "Restore selected backup?", "confirm = BackupConfirm.RESTORE",
            "BackupManager.RETENTION_LIMIT", "BackupManager.restore(context, token, selectedId"
        ).forEach { assertTrue(it, screen.contains(it)) }
    }
}
