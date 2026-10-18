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
    data class Remote(val id: String, val modifiedTime: String)

    fun find(token: String, name: String): Remote? {
        val query = URLEncoder.encode("name='$name'", "UTF-8")
        val url = "$FILES?spaces=appDataFolder&q=$query&fields=" + URLEncoder.encode("files(id,modifiedTime)", "UTF-8")
        val files = JSONObject(String(request(token, url, "GET"))).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).let { Remote(it.getString("id"), it.optString("modifiedTime")) }
    }

    fun upload(token: String, name: String, bytes: ByteArray) {
        val existing = find(token, name)
        val metadata = JSONObject().put("name", name).apply { if (existing == null) put("parents", org.json.JSONArray().put("appDataFolder")) }
        val boundary = "clarity-" + UUID.randomUUID()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--".toByteArray())
        }.toByteArray()
        val url = if (existing == null) "$UPLOAD?uploadType=multipart" else "$UPLOAD/${existing.id}?uploadType=multipart"
        val headers = mutableMapOf("Content-Type" to "multipart/related; boundary=$boundary")
        if (existing != null) headers["X-HTTP-Method-Override"] = "PATCH"
        request(token, url, "POST", body, headers)
    }

    fun download(token: String, id: String): ByteArray = request(token, "$FILES/$id?alt=media", "GET")

    fun delete(token: String, id: String) { request(token, "$FILES/$id", "DELETE") }

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
