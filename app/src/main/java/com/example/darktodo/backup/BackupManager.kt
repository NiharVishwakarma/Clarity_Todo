package com.example.darktodo.backup

import android.content.Context
import android.util.Base64
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
    val PREF_FILES = listOf("todo_store", "calendar_widget_settings", "app_typography", "widget_backgrounds", "vault_auth", "sticker_store")

    class Outcome(val ok: Boolean, val retry: Boolean, val message: String)

    private fun state(context: Context) = context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)

    fun email(context: Context): String? = state(context).getString("email", null)
    fun autoEnabled(context: Context): Boolean = state(context).getBoolean("auto", false)
    fun lastBackupAt(context: Context): Long = state(context).getLong("last", 0L)
    fun lastStatus(context: Context): String = state(context).getString("status", "") ?: ""
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
        val prefs = JSONObject()
        PREF_FILES.forEach { name -> prefs.put(name, BackupCodec.encodePrefs(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)) }
        val files = JSONObject()
        blobFiles(context).forEach { (name, file) ->
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
    fun applySnapshot(context: Context, json: String): Boolean {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        if (version < 1 || version > BackupCodec.VERSION) throw IOException("This backup was made by a different version of Clarity.")
        val prefs = root.optJSONObject("prefs") ?: throw IOException("The backup is empty or damaged.")
        val files = root.optJSONObject("files") ?: JSONObject()
        val decodedPrefs = linkedMapOf<String, Map<String, Any>>()
        PREF_FILES.forEach { name ->
            if (prefs.has(name)) {
                val values = prefs.optJSONObject(name) ?: throw IOException("The backup settings are damaged.")
                decodedPrefs[name] = BackupCodec.decodePrefs(values)
            }
        }
        val decodedFiles = linkedMapOf<String, ByteArray>()
        files.keys().forEach { name ->
            val encoded = files.optString(name, null) ?: throw IOException("The backup file list is damaged.")
            decodedFiles[name] = try {
                Base64.decode(encoded, Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                throw IOException("The backup contains a damaged file and was not restored.")
            }
        }
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
                val bytes = decodedFiles[name]
                if (bytes != null && bytes.isNotEmpty()) {
                    val temporary = File(target.parentFile, target.name + ".restore")
                    temporary.writeBytes(bytes)
                    if (!temporary.renameTo(target)) { target.writeBytes(temporary.readBytes()); temporary.delete() }
                } else if (name != "vault") target.delete()
            }
            // The biometric key lives in this device's Keystore and cannot be restored; unlock with the master password first.
            runCatching { LocalVaultStore.usePasswordAuthentication(context) }
        }
        com.example.darktodo.widget.TodoWidget.updateAll(context)
        com.example.darktodo.widget.CalendarWidget.updateAll(context)
        com.example.darktodo.widget.StickerWidget.updateAll(context)
        return decodedFiles["vault"]?.isNotEmpty() == true
    }

    fun backupNow(context: Context, token: String? = null): Outcome {
        val accessToken = token ?: silentToken(context)
            ?: return fail(context, "Google access must be granted again. Open Settings > Backup & Restore.", retry = false)
        return try {
            DriveClient.upload(accessToken, FILE_NAME, buildSnapshot(context).toByteArray(Charsets.UTF_8))
            state(context).edit().putLong("last", System.currentTimeMillis()).putString("status", "Backed up").apply()
            Outcome(true, false, "Backup complete.")
        } catch (error: DriveClient.AuthExpiredException) {
            fail(context, error.message ?: "Google access expired.", retry = false)
        } catch (error: IOException) {
            fail(context, error.message ?: "Network problem. Will retry.", retry = true)
        } catch (error: Exception) {
            fail(context, error.message ?: "Backup failed.", retry = false)
        }
    }

    private fun fail(context: Context, message: String, retry: Boolean): Outcome {
        state(context).edit().putString("status", "Last attempt failed: $message").apply()
        return Outcome(false, retry, message)
    }

    fun findBackup(token: String): DriveClient.Remote? = DriveClient.find(token, FILE_NAME)

    /** Downloads and applies the cloud backup, enabling automatic backup afterwards. Returns whether it held a vault. */
    fun restore(context: Context, token: String): Boolean {
        val remote = findBackup(token) ?: throw IOException("No backup was found in this Google account.")
        val json = DriveClient.download(token, remote.id).toString(Charsets.UTF_8)
        val hadVault = applySnapshot(context, json)
        state(context).edit().putBoolean("auto", true).putLong("last", System.currentTimeMillis()).putString("status", "Restored").apply()
        return hadVault
    }

    fun deleteBackup(context: Context, token: String): Boolean {
        val remote = findBackup(token) ?: return false
        DriveClient.delete(token, remote.id)
        state(context).edit().putBoolean("auto", false).remove("last").putString("status", "Cloud backup deleted").apply()
        return true
    }
}
