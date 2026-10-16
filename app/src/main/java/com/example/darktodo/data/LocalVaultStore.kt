package com.example.darktodo.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager

data class VaultEntry(val id: String, val title: String, val username: String, val password: String, val notes: String)

/** Password-encrypted vault file under noBackupFilesDir; it is never synced or backed up. */
object LocalVaultStore {
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val MAGIC = 0x434C5654 // CLVT
    private const val AUTH_PREFS = "vault_auth"
    private const val AUTH_MODE = "primary"
    const val AUTH_PASSWORD = "password"
    const val AUTH_BIOMETRIC = "biometric"
    private const val BIOMETRIC_ALIAS = "clarity_vault_biometric_key"
    private const val BIOMETRIC_FILE = "clarity-vault-biometric.bin"
    internal fun file(context: Context) = File(context.noBackupFilesDir, "clarity-vault.bin")
    private fun biometricFile(context: Context) = File(context.noBackupFilesDir, BIOMETRIC_FILE)

    fun primaryAuth(context: Context): String = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
        .getString(AUTH_MODE, AUTH_PASSWORD) ?: AUTH_PASSWORD

    fun canAuthenticateBiometric(context: Context): Int = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

    fun prepareBiometricEnrollment(): Cipher {
        val key = getOrCreateBiometricKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun prepareBiometricUnlock(context: Context): Pair<Cipher, ByteArray> {
        val wrapper = biometricFile(context).readBytes()
        require(wrapper.size > 12) { "Fingerprint unlock is not configured." }
        val iv = wrapper.copyOfRange(0, 12)
        val encrypted = wrapper.copyOfRange(12, wrapper.size)
        val key = keyStore().getKey(BIOMETRIC_ALIAS, null) ?: error("Fingerprint key is unavailable.")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher to encrypted
    }

    fun saveBiometricWrapper(context: Context, cipher: Cipher, masterPassword: String) {
        val clear = masterPassword.toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(clear) } finally { clear.fill(0) }
        biometricFile(context).writeBytes(cipher.iv + encrypted)
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putString(AUTH_MODE, AUTH_BIOMETRIC).apply()
    }

    fun usePasswordAuthentication(context: Context) {
        biometricFile(context).delete()
        keyStore().deleteEntry(BIOMETRIC_ALIAS)
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putString(AUTH_MODE, AUTH_PASSWORD).apply()
    }

    private fun getOrCreateBiometricKey(): java.security.Key {
        val store = keyStore()
        runCatching { store.getKey(BIOMETRIC_ALIAS, null) }.getOrNull()?.let { return it }
        runCatching { store.deleteEntry(BIOMETRIC_ALIAS) }
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun keyStore() = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun exists(context: Context) = file(context).exists()

    const val DEFAULT_AUTO_LOCK_SECONDS = 300
    private const val AUTO_LOCK_KEY = "auto_lock_seconds"

    /** Idle time before an open vault locks itself; 0 means only lock when the app is left. */
    fun autoLockSeconds(context: Context): Int = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
        .getInt(AUTO_LOCK_KEY, DEFAULT_AUTO_LOCK_SECONDS).coerceAtLeast(0)

    fun setAutoLockSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putInt(AUTO_LOCK_KEY, seconds.coerceAtLeast(0)).apply()
    }

    /**
     * Re-encrypts the vault under a new master password and fresh salt. The biometric wrapper holds
     * the old password, so it is removed. Returns an error message, or null on success.
     */
    fun changeMasterPassword(context: Context, current: CharArray, replacement: CharArray): String? {
        if (replacement.size < 8) return "Use at least 8 characters for the new password."
        val entries = unlock(context, current) ?: return "The current password is incorrect."
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        return try {
            write(context, key(replacement, salt), salt, entries)
            if (primaryAuth(context) == AUTH_BIOMETRIC) usePasswordAuthentication(context)
            null
        } catch (error: Exception) { error.message ?: "Could not change the master password." }
    }

    fun create(context: Context, masterPassword: CharArray, entries: List<VaultEntry> = emptyList()) {
        require(masterPassword.size >= 8) { "Use at least 8 characters for the vault password." }
        require(!exists(context)) { "The vault already exists." }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        write(context, key(masterPassword, salt), salt, entries)
    }

    fun unlock(context: Context, masterPassword: CharArray): List<VaultEntry>? = runCatching {
        val bytes = file(context).readBytes()
        val input = ByteBuffer.wrap(bytes)
        require(input.int == MAGIC)
        val salt = ByteArray(SALT_SIZE).also { input.get(it) }
        val iv = ByteArray(IV_SIZE).also { input.get(it) }
        val encrypted = ByteArray(input.remaining()).also { input.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(masterPassword, salt), GCMParameterSpec(128, iv))
        val array = JSONArray(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            VaultEntry(item.getString("id"), item.getString("title"), item.optString("username"), item.getString("password"), item.optString("notes"))
        }
    }.getOrNull()

    fun save(context: Context, masterPassword: CharArray, entries: List<VaultEntry>) {
        val old = ByteBuffer.wrap(file(context).readBytes())
        require(old.int == MAGIC)
        val salt = ByteArray(SALT_SIZE).also { old.get(it) }
        val iv = ByteArray(IV_SIZE).also { old.get(it) }
        val encrypted = ByteArray(old.remaining()).also { old.get(it) }
        val key = key(masterPassword, salt)
        val check = Cipher.getInstance("AES/GCM/NoPadding")
        check.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        check.doFinal(encrypted) // Authenticate before replacing the existing file.
        write(context, key, salt, entries)
    }

    private fun write(context: Context, key: SecretKeySpec, salt: ByteArray, entries: List<VaultEntry>) {
        val json = JSONArray().apply { entries.forEach { entry -> put(JSONObject().apply {
            put("id", entry.id); put("title", entry.title); put("username", entry.username)
            put("password", entry.password); put("notes", entry.notes)
        }) } }.toString().toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(json)
        val output = ByteBuffer.allocate(4 + SALT_SIZE + IV_SIZE + encrypted.size)
            .putInt(MAGIC).put(salt).put(iv).put(encrypted).array()
        val target = file(context)
        val temporary = File(context.noBackupFilesDir, "clarity-vault.tmp")
        temporary.writeBytes(output)
        if (!temporary.renameTo(target)) {
            target.writeBytes(output)
            temporary.delete()
        }
        com.example.darktodo.backup.BackupScheduler.requestBackup(context)
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
}
