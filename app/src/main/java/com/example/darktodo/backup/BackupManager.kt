package com.example.darktodo.backup

import android.content.Context
import android.util.Base64
import android.util.AtomicFile
import com.example.darktodo.data.LocalVaultStore
import com.example.darktodo.data.StickerStore
import com.example.darktodo.data.WidgetBackgrounds
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Snapshot, upload and restore of Clarity data in the user's private Google Drive appDataFolder.
 * Local storage stays the working database; the vault is uploaded only as its already-encrypted file.
 * Encryption keys (the Keystore biometric key) are never read or uploaded.
 */
object BackupManager {
    const val FILE_NAME = "clarity-backup.json"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val STATE = "backup_state"

    // vault_auth is included for the auto-lock timer; the biometric choice is reset on restore.
    val PREF_FILES = BackupSnapshot.preferenceFiles
    const val RETENTION_LIMIT = BackupHistory.LIMIT

    class Outcome(val ok: Boolean, val retry: Boolean, val message: String)

    private fun state(context: Context) = context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)

    fun email(context: Context): String? = state(context).getString("email", null)
    fun autoEnabled(context: Context): Boolean = state(context).getBoolean("auto", false)
    fun lastBackupAt(context: Context): Long = state(context).getLong("last", 0L)
    fun lastStatus(context: Context): String = state(context).getString("status", "") ?: ""
    fun lastError(context: Context): String? = state(context).getString("last_error", null)
    fun onboarded(context: Context): Boolean = state(context).getBoolean("onboarded", false)

    fun setConnected(context: Context, email: String?) { state(context).edit().putString("email", email ?: "Google account").apply() }
    fun setAutoEnabled(context: Context, enabled: Boolean) { state(context).edit().putBoolean("auto", enabled).apply() }
    fun markOnboarded(context: Context) { state(context).edit().putBoolean("onboarded", true).apply() }
    fun disconnect(context: Context) { state(context).edit().remove("email").putBoolean("auto", false).remove("status").apply() }

    /** True only on a clean first launch: no local tasks, history or vault and the prompt was not answered yet. */
    fun needsWelcome(context: Context): Boolean {
        if (onboarded(context)) return false
        val hasData = LocalVaultStore.exists(context) || PREF_FILES.any { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
        }
        if (hasData) { markOnboarded(context); return false }
        return true
    }

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE), Scope("email"))).build()

    /** Returns an access token without any UI, or null when the user must grant access again. Blocking. */
    fun silentToken(context: Context): String? = runCatching {
        val result = Tasks.await(Identity.getAuthorizationClient(context.applicationContext).authorize(authorizationRequest()))
        if (result.hasResolution()) null else result.accessToken
    }.getOrNull()

    fun buildSnapshot(context: Context): String {
        // Run note migration before capturing its preferences and image allow-list.
        val blobs = blobFiles(context)
        val prefs = JSONObject()
        PREF_FILES.forEach { name -> prefs.put(name, BackupCodec.encodePrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)) }
        val files = JSONObject()
        blobs.forEach { (name, file) ->
            if (file.isFile) files.put(name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
        return JSONObject().put("version", BackupCodec.VERSION).put("createdAt", System.currentTimeMillis())
            .put("prefs", prefs).put("files", files).toString()
    }

    /** Fixed allow-list of backed up files, so a backup can never write outside these locations. */
    private fun blobFiles(context: Context): Map<String, File> {
        val files = linkedMapOf(
            "vault" to LocalVaultStore.file(context),
            "wallpaper_todo" to WidgetBackgrounds.file(context, WidgetBackgrounds.TODO),
            "wallpaper_calendar" to WidgetBackgrounds.file(context, WidgetBackgrounds.CALENDAR)
        )
        // One image per note. Names are built from stored note ids, never taken from the backup file.
        StickerStore.load(context).forEach { note ->
            files["wallpaper_note_${note.id}"] = WidgetBackgrounds.file(context, WidgetBackgrounds.noteKey(note.id))
        }
        return files
    }

    /** Replaces local data with the snapshot. Returns true when the snapshot contained a vault. */
    @Suppress("UNCHECKED_CAST")
    private fun applySnapshot(context: Context, json: String): Boolean {
        val plan = BackupSnapshot.decode(json)
        val decodedPrefs = plan.prefs
        val decodedFiles = plan.files
        BackupScheduler.withSuspended {
            decodedPrefs.forEach { (name, values) ->
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value as Set<String>)
                    }
                }
                check(editor.commit()) { "Could not write restored settings." }
            }
            blobFiles(context).forEach { (name, target) ->
                if (name.startsWith("wallpaper_note_") && !decodedPrefs.containsKey("sticker_store")) return@forEach
                val bytes = decodedFiles[name]
                if (bytes != null && bytes.isNotEmpty()) {
                    writeAtomic(AtomicFile(target), bytes)
                } else if (name != "vault" && target.exists() && !target.delete()) {
                    throw IOException("Could not replace a restored background image.")
                }
            }
            // The biometric key lives in this device's Keystore and cannot be restored; unlock with the master password first.
            LocalVaultStore.usePasswordAuthentication(context)
        }
        com.example.darktodo.widget.TodoWidget.updateAll(context)
        com.example.darktodo.widget.CalendarWidget.updateAll(context)
        com.example.darktodo.widget.StickerWidget.updateAll(context)
        com.example.darktodo.widget.VaultWidget.updateAll(context)
        return decodedFiles["vault"]?.isNotEmpty() == true
    }

    @Synchronized
    fun backupNow(context: Context, token: String? = null, automatic: Boolean = false, operationId: String = UUID.randomUUID().toString()): Outcome {
        if (automatic && !autoEnabled(context)) return Outcome(true, false, "Automatic backup is paused.")
        val accessToken = token ?: silentToken(context)
            ?: return fail(context, "Google access must be granted again. Open Settings > Backup & Restore.", retry = false)
        return try {
            val effectiveId = if (automatic) operationId else {
                state(context).getString("manual_operation", null) ?: operationId.also {
                    check(state(context).edit().putString("manual_operation", it).commit()) { "Could not save the backup request." }
                }
            }
            val point = uploadSnapshot(context, accessToken, if (automatic) "automatic" else "manual", effectiveId)
            if (!automatic) check(state(context).edit().remove("manual_operation").commit()) { "Could not finish the backup request. Retry to confirm it." }
            state(context).edit().remove("last_error").putString("status", "Backed up").apply()
            var notice = "Backup complete."
            try {
                val points = history(accessToken)
                BackupHistory.excess(points, setOf(point.id)).forEach { DriveClient.delete(accessToken, it.id) }
            } catch (error: Exception) {
                notice += " Older snapshots were kept because retention cleanup failed. Refresh history and try a later backup."
                state(context).edit().putString("status", "Backed up; older snapshots kept (cleanup deferred)").apply()
                android.util.Log.w("BackupManager", "Backup retention cleanup failed (${error.javaClass.simpleName})")
            }
            Outcome(true, false, notice)
        } catch (error: DriveClient.AuthExpiredException) {
            fail(context, error.message ?: "Google access expired.", retry = false)
        } catch (error: IOException) {
            fail(context, error.message ?: "Network problem. Will retry.", retry = true)
        } catch (error: Exception) {
            fail(context, error.message ?: "Backup failed.", retry = false)
        }
    }

    private fun fail(context: Context, message: String, retry: Boolean): Outcome {
        // A failed upload must not replace the last successful backup status/time.
        state(context).edit().putString("last_error", message).apply()
        return Outcome(false, retry, message)
    }

    @Synchronized
    fun history(token: String): List<BackupPoint> = DriveClient.list(token)

    fun findBackup(token: String): BackupPoint? = history(token).firstOrNull()

    /** Downloads and applies the cloud backup, enabling automatic backup afterwards. Returns whether it held a vault. */
    @Synchronized
    fun restore(context: Context, token: String, backupId: String): Boolean {
        val remote = history(token).firstOrNull { it.id == backupId }
            ?: throw IOException("The selected backup is no longer available. Refresh backup history.")
        val bytes = DriveClient.download(token, remote.id)
        BackupHistory.verify(remote, bytes)
        val json = bytes.toString(Charsets.UTF_8)
        val plan = BackupSnapshot.decode(bytes)
        validateImages(plan)
        // Cloud safety snapshot is mandatory; no pruning while the selected point is in use.
        val safety = buildSnapshot(context)
        uploadSnapshot(context, token, "safety", UUID.randomUUID().toString(), safety)
        val recovery = AtomicFile(File(context.noBackupFilesDir, "backup-restore-safety.json"))
        writeAtomic(recovery, safety.toByteArray(Charsets.UTF_8))
        val hadVault = try {
            applySnapshot(context, json)
        } catch (error: Exception) {
            try {
                applySnapshot(context, safety)
                if (!BackupSnapshot.decode(safety).files.containsKey("vault")) {
                    val vault = LocalVaultStore.file(context)
                    if (vault.exists() && !vault.delete()) throw IOException("Could not recover the previous empty vault state.")
                }
            } catch (rollback: Exception) {
                throw IOException("Restore failed and automatic recovery failed. A local safety copy and a Safety restore point were retained. Restore the Safety point before making further changes.", rollback)
            }
            throw IOException("Restore failed. Previous data was recovered; the Safety point is also available.", error)
        }
        state(context).edit().putBoolean("auto", true).putString("status", "Restored selected backup").apply()
        return hadVault
    }

    @Synchronized
    fun deleteBackup(context: Context, token: String): Boolean {
        val points = history(token)
        if (points.isEmpty()) return false
        setAutoEnabled(context, false)
        BackupScheduler.cancel(context)
        points.forEach { DriveClient.delete(token, it.id) }
        AtomicFile(File(context.noBackupFilesDir, "pending-drive-backup.json")).delete()
        state(context).edit().putBoolean("auto", false).remove("last").remove("last_error")
            .remove("manual_operation").remove("completed_operation").remove("completed_backup").putString("status", "Cloud backup history deleted").apply()
        return true
    }

    /** One durable pending payload/reserved ID survives worker retries and lost HTTP responses. */
    private fun uploadSnapshot(context: Context, token: String, type: String, operationId: String, capturedPayload: String? = null): BackupPoint {
        val journal = AtomicFile(File(context.noBackupFilesDir, "pending-drive-backup.json"))
        val currentEmail = email(context) ?: throw IOException("Connect a Google account before backing up.")
        var pending = if (journal.baseFile.exists() || File(journal.baseFile.path + ".bak").exists()) {
            JSONObject(journal.readFully().toString(Charsets.UTF_8))
        } else null
        if (pending != null && pending.getString("account") != currentEmail) {
            throw IOException("An unfinished backup belongs to another account. Reconnect that account to finish it before backing up here.")
        }
        if (pending != null) {
            val recovered = finishUpload(context, token, pending)
            journal.delete()
            if (pending.getString("operation") == operationId) return recovered
        }
        // A worker ID is stable across retries; successful retries must not create another snapshot.
        val completed = state(context).getString("completed_operation", null)
        if (completed == operationId) {
            val completedId = state(context).getString("completed_backup", null)
                ?: throw IOException("The completed backup reference is missing.")
            val point = DriveClient.metadata(token, completedId) ?: throw IOException("The completed backup was removed.")
            val bytes = DriveClient.download(token, point.id)
            BackupHistory.verify(point, bytes)
            validateImages(BackupSnapshot.decode(bytes))
            return point
        }
        history(token).firstOrNull { it.operationId == operationId }?.let {
            val bytes = DriveClient.download(token, it.id)
            BackupHistory.verify(it, bytes)
            validateImages(BackupSnapshot.decode(bytes))
            return it
        }
        val payload = capturedPayload ?: buildSnapshot(context)
        validateImages(BackupSnapshot.decode(payload))
        pending = JSONObject().put("id", DriveClient.generateId(token)).put("operation", operationId)
            .put("account", currentEmail).put("type", type).put("payload", payload)
        writeAtomic(journal, pending.toString().toByteArray(Charsets.UTF_8))
        val point = finishUpload(context, token, pending)
        journal.delete()
        return point
    }

    private fun finishUpload(context: Context, token: String, pending: JSONObject): BackupPoint {
        val bytes = pending.getString("payload").toByteArray(Charsets.UTF_8)
        validateImages(BackupSnapshot.decode(pending.getString("payload")))
        val hash = BackupHistory.digest(bytes)
        val time = JSONObject(pending.getString("payload")).getLong("createdAt")
        val properties = JSONObject().put("clarityBackup", BackupHistory.MARKER)
            .put("snapshotTime", time.toString()).put("backupType", pending.getString("type"))
            .put("schema", BackupCodec.VERSION.toString()).put("sha256", hash)
            .put("operationId", pending.getString("operation"))
        val id = pending.getString("id")
        var point = DriveClient.upload(token, id, "clarity-backup-$time-$id.json", bytes, properties)
        if (point.sha256 != hash) throw IOException("Uploaded snapshot metadata does not match. Retry to verify it.")
        val downloaded = DriveClient.download(token, point.id)
        BackupHistory.verify(point, downloaded)
        if (!downloaded.contentEquals(bytes)) throw IOException("Uploaded snapshot verification failed. The previous backups were kept.")
        validateImages(BackupSnapshot.decode(downloaded))
        if (!point.verified) point = DriveClient.markVerified(token, point.id, properties)
        val editor = state(context).edit().putLong("last", time).putString("status", "Backed up")
            .remove("last_error")
            .putString("completed_operation", pending.getString("operation")).putString("completed_backup", point.id)
        if (state(context).getString("manual_operation", null) == pending.getString("operation")) editor.remove("manual_operation")
        check(editor.commit()) {
            "Could not record the uploaded backup. Retry to finish verification."
        }
        return point
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun validateImages(plan: BackupSnapshot.Plan) {
        plan.files.filterKeys { it != "vault" }.values.forEach { image ->
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("A backed-up image is damaged. Nothing was restored.")
        }
    }
}
