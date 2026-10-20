package com.example.darktodo.backup

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Minimal Drive v3 REST client limited to the private appDataFolder. Blocking; call off the main thread. */
object DriveClient {
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    class AuthExpiredException : IOException("Google authorization expired. Try signing in again to continue.")
    private const val FIELDS = "id,name,createdTime,modifiedTime,size,md5Checksum,appProperties"
    private class MissingFile : IOException("That backup no longer exists. Refresh backup history.")
    private class ExistingFile : IOException("That snapshot already exists.")

    fun list(token: String): List<BackupPoint> {
        val query = "trashed = false and (name = '${BackupHistory.LEGACY_NAME}' or appProperties has { key='clarityBackup' and value='${BackupHistory.MARKER}' })"
        val points = mutableListOf<BackupPoint>()
        var page: String? = null
        val seenPages = mutableSetOf<String>()
        do {
            val url = "$FILES?spaces=appDataFolder&pageSize=100&q=${encode(query)}&fields=${encode("nextPageToken,files($FIELDS)")}" +
                (page?.let { "&pageToken=${encode(it)}" } ?: "")
            val result = JSONObject(request(token, url, "GET").toString(Charsets.UTF_8))
            val files = if (result.has("files")) result.getJSONArray("files") else org.json.JSONArray()
            for (index in 0 until files.length()) BackupHistory.parse(files.getJSONObject(index))?.let(points::add)
            page = result.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (page != null && !seenPages.add(page)) throw IOException("Google repeated a history page. Please refresh.")
        } while (page != null)
        return BackupHistory.newestFirst(points)
    }

    fun generateId(token: String): String =
        JSONObject(request(token, "$FILES/generateIds?count=1&space=appDataFolder&type=files", "GET").toString(Charsets.UTF_8))
            .getJSONArray("ids").getString(0).also(::checkId)

    fun metadata(token: String, id: String): BackupPoint? {
        checkId(id)
        return try {
            BackupHistory.parse(JSONObject(request(token, "$FILES/$id?fields=${encode(FIELDS)}", "GET").toString(Charsets.UTF_8)))
        } catch (_: MissingFile) { null }
    }

    /** A reserved Drive ID makes retries of even a timed-out create idempotent. Never patches a snapshot. */
    fun upload(token: String, id: String, name: String, bytes: ByteArray, properties: JSONObject): BackupPoint {
        checkId(id)
        metadata(token, id)?.let { return it }
        val fileMetadata = JSONObject().put("id", id).put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder")).put("appProperties", properties)
        val boundary = "clarity-" + UUID.randomUUID()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$fileMetadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        try {
            request(token, "$UPLOAD?uploadType=multipart", "POST", body, mapOf("Content-Type" to "multipart/related; boundary=$boundary"))
        } catch (_: ExistingFile) {
            // The previous attempt may have reached Drive even if its response never reached us.
        }
        return metadata(token, id) ?: throw IOException("Google did not confirm the uploaded snapshot. Retry to verify it.")
    }

    fun download(token: String, id: String): ByteArray {
        checkId(id)
        return request(token, "$FILES/$id?alt=media", "GET")
    }

    fun delete(token: String, id: String) {
        checkId(id)
        try { request(token, "$FILES/$id", "DELETE") } catch (_: MissingFile) { /* Already deleted on a prior attempt. */ }
    }

    fun markVerified(token: String, id: String, properties: JSONObject): BackupPoint {
        checkId(id)
        val body = JSONObject().put("appProperties", properties.put("verified", "true")).toString().toByteArray(Charsets.UTF_8)
        val response = request(token, "$FILES/$id?fields=${encode(FIELDS)}", "POST", body,
            mapOf("Content-Type" to "application/json", "X-HTTP-Method-Override" to "PATCH"))
        return BackupHistory.parse(JSONObject(response.toString(Charsets.UTF_8)))
            ?.takeIf { it.verified } ?: throw IOException("Google did not confirm backup verification. Retry this backup.")
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun checkId(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid backup ID." } }

    private fun request(token: String, url: String, method: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap()): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code == 401) throw AuthExpiredException()
            if (code == 404) throw MissingFile()
            if (code == 409) throw ExistingFile()
            if (code !in 200..299) {
                val detail = runCatching { connection.errorStream?.readBytes()?.toString(Charsets.UTF_8)?.take(200) }.getOrNull().orEmpty()
                if (code == 403) {
                    throw IOException("Google Drive denied private backup access. Confirm Drive API is enabled and the OAuth consent screen grants drive.appdata. $detail".trim())
                }
                throw IOException("Google Drive error $code. $detail".trim())
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
