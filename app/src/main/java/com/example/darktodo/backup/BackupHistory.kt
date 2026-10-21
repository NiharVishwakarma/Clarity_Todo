package com.example.darktodo.backup

import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant

data class BackupPoint(
    val id: String,
    val name: String,
    val createdAt: Long?,
    val type: String?,
    val version: Int?,
    val size: Long?,
    val sha256: String?,
    val md5: String?,
    val legacy: Boolean,
    val operationId: String? = null,
    val verified: Boolean = false
)

internal object BackupHistory {
    const val LIMIT = 10
    const val LEGACY_NAME = "clarity-backup.json"
    const val MARKER = "snapshot-v1"

    fun digest(bytes: ByteArray, algorithm: String = "SHA-256"): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    fun parse(file: JSONObject): BackupPoint? {
        val name = file.optString("name")
        val properties = file.optJSONObject("appProperties") ?: JSONObject()
        val legacy = name == LEGACY_NAME
        if (!legacy && properties.optString("clarityBackup") != MARKER) return null
        val id = file.getString("id")
        if (!id.matches(Regex("[A-Za-z0-9_-]+"))) throw IOException("Google returned an invalid backup ID.")
        fun time(value: String): Long? = try { Instant.parse(value).toEpochMilli() } catch (_: java.time.format.DateTimeParseException) { null }
        return BackupPoint(
            id, name,
            properties.optString("snapshotTime").toLongOrNull()?.takeIf { it > 0 }
                ?: time(file.optString(if (legacy) "modifiedTime" else "createdTime")),
            properties.optString("backupType").takeIf { it in setOf("manual", "automatic", "safety") },
            properties.optString("schema").toIntOrNull(),
            file.optString("size").toLongOrNull()?.takeIf { it >= 0 },
            properties.optString("sha256").takeIf { it.matches(Regex("[a-f0-9]{64}")) },
            file.optString("md5Checksum").takeIf { it.matches(Regex("[a-f0-9]{32}")) },
            legacy, properties.optString("operationId").takeIf { it.isNotBlank() },
            properties.optString("verified") == "true"
        )
    }

    fun newestFirst(points: List<BackupPoint>): List<BackupPoint> =
        points.distinctBy { it.id }.sortedWith(compareByDescending<BackupPoint> { it.createdAt ?: Long.MIN_VALUE }.thenBy { it.id })

    // Legacy and unverified/unknown-format files are never automatically removed.
    fun excess(points: List<BackupPoint>, protectedIds: Set<String> = emptySet()): List<BackupPoint> =
        newestFirst(points.filter { !it.legacy && it.verified && it.sha256 != null && it.createdAt != null && it.version in 1..BackupCodec.VERSION })
            .drop(LIMIT).filterNot { it.id in protectedIds }

    fun verify(point: BackupPoint, bytes: ByteArray) {
        if (point.size != null && bytes.size.toLong() != point.size) throw IOException("Backup size does not match. Nothing was restored.")
        if (point.sha256 != null && digest(bytes) != point.sha256) throw IOException("Backup integrity check failed. Nothing was restored.")
        if (point.md5 != null && digest(bytes, "MD5") != point.md5) throw IOException("Drive checksum does not match. Nothing was restored.")
        if (!point.legacy && point.sha256 == null) throw IOException("This snapshot has no integrity metadata. Nothing was restored.")
    }
}
