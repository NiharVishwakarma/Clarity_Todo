package com.example.darktodo.monitor

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DiscoveredDocument(
    val uri: Uri, val name: String, val mime: String, val size: Long?, val modified: Long?
) {
    val kind: String get() = documentKind(name, mime)
}

fun documentKind(name: String, mime: String): String {
    val lower = name.lowercase(java.util.Locale.ROOT)
    return when {
        lower.endsWith(".apk") || mime == "application/vnd.android.package-archive" -> "APK installer"
        listOf(".apks", ".xapk", ".apkm").any(lower::endsWith) -> "APK bundle archive (not installed)"
        listOf(".zip", ".rar", ".7z", ".tar", ".gz").any(lower::endsWith) -> "Archive"
        mime.startsWith("image/") -> "Image"
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        else -> "Document"
    }
}

data class DiscoveryResult(val files: List<DiscoveredDocument>, val partial: Boolean, val notes: String)

internal class DiscoveryDirectories(private val maxDepth: Int = 12, private val maxDirectories: Int = 2000) {
    private val seen = HashSet<String>()
    fun accept(id: String, depth: Int): Boolean =
        depth <= maxDepth && seen.size < maxDirectories && seen.add(id)
}

internal suspend fun runDiscoveryWithinDeadline(
    timeoutMillis: Long,
    cancelProvider: () -> Unit = {},
    block: suspend () -> Unit
): Boolean = coroutineScope {
    // Register before synchronous provider work; cancellation can be signalled from another thread.
    val watchdog = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { delay(timeoutMillis) }
        finally { cancelProvider() }
    }
    try {
        withTimeoutOrNull(timeoutMillis) { block(); true } ?: false
    } finally {
        watchdog.cancel()
    }
}

object MonitorDocuments {
    private const val DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR
    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun discover(context: Context, uri: Uri, tree: Boolean): DiscoveryResult = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveredDocument>()
        var partial = false
        var timedOut = false
        var inspected = 0
        val started = System.nanoTime()
        val scanSignal = CancellationSignal()
        val directories = DiscoveryDirectories()
        val pending = ArrayDeque<Pair<String, Int>>()
        if (tree) {
            val root = DocumentsContract.getTreeDocumentId(uri)
            directories.accept(root, 0)
            pending.add(root to 0)
        }
        suspend fun read(queryUri: Uri, depth: Int, single: Boolean) {
            currentCoroutineContext().ensureActive()
            val cursor = suspendCancellableCoroutine<android.database.Cursor?> { continuation ->
                continuation.invokeOnCancellation { scanSignal.cancel() }
                try {
                    val projection = if (single) arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE) else columns
                    val result = context.contentResolver.query(queryUri, projection, null, null, null, scanSignal)
                    continuation.resume(result) { result?.close() }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            if (cursor == null) { partial = true; return }
            cursor.use {
                fun string(key: String): String? = it.getColumnIndex(key).takeIf { index -> index >= 0 }
                    ?.let { index -> if (it.isNull(index)) null else it.getString(index) }
                fun number(key: String): Long? = it.getColumnIndex(key).takeIf { index -> index >= 0 }
                    ?.let { index -> if (it.isNull(index)) null else it.getLong(index).takeIf { n -> n >= 0 } }
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (++inspected > 2000 || System.nanoTime() - started > 8_000_000_000L) {
                        partial = true; break
                    }
                    val id = if (single) null else string(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mime = if (single) context.contentResolver.getType(queryUri).orEmpty()
                        else string(DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                    if (mime == DIRECTORY) {
                        if (id == null) partial = true
                        else if (directories.accept(id, depth + 1)) pending.add(id to depth + 1)
                        else partial = true
                    } else {
                        val itemUri = if (single) queryUri else id?.let { documentId ->
                            DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                        }
                        if (itemUri == null) { partial = true; continue }
                        found.add(DiscoveredDocument(itemUri, string(OpenableColumns.DISPLAY_NAME) ?: "Unnamed document",
                            mime.ifBlank { "application/octet-stream" }, number(OpenableColumns.SIZE),
                            if (single) null else number(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { n -> n > 0 }))
                    }
                }
            }
        }
        val completed = runDiscoveryWithinDeadline(8_000, scanSignal::cancel) {
            try {
                if (!tree) read(uri, 0, true)
                while (pending.isNotEmpty() && inspected < 2000 && System.nanoTime() - started <= 8_000_000_000L) {
                    currentCoroutineContext().ensureActive()
                    val (id, depth) = pending.removeFirst()
                    try {
                        read(DocumentsContract.buildChildDocumentsUriUsingTree(uri, id), depth, false)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { partial = true }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { partial = true }
        }
        if (!completed || System.nanoTime() - started >= 8_000_000_000L) {
            partial = true
            timedOut = true
        }
        if (pending.isNotEmpty()) partial = true
        DiscoveryResult(found.distinctBy { it.uri }, partial,
            when {
                timedOut -> "Partial results: the 8-second deadline expired and provider cancellation was requested. Providers may ignore cancellation or block while reading cursor rows; no hard response-time guarantee is possible."
                partial -> "Partial results: access revoked, provider error, cycle, or scan limit reached. Select again to retry."
                else -> "Only your selected document/folder was inspected. Sizes may be unknown; this is not a device-wide Downloads inventory."
            }
        )
    }
}

data class CacheResult(val bytes: Long, val files: Int, val failures: Int, val partial: Boolean)

object MonitorCache {
    suspend fun inspect(context: Context): CacheResult = run(context, false)
    suspend fun clear(context: Context): CacheResult = run(context, true)
    private suspend fun run(context: Context, delete: Boolean): CacheResult = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        walk(listOf(context.cacheDir, context.codeCacheDir), delete) { coroutine.ensureActive() }
    }

    internal fun walk(roots: List<File>, delete: Boolean, checkCancelled: () -> Unit = {}): CacheResult {
        var bytes = 0L
        var files = 0
        var failures = 0
        var partial = false
        var visited = 0
        val started = System.nanoTime()
        for (rootFile in roots.distinctBy { it.absolutePath }) {
            val root = rootFile.toPath().toAbsolutePath().normalize()
            if (Files.isSymbolicLink(root)) { failures++; partial = true; continue }
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) continue
            fun visit(path: java.nio.file.Path, depth: Int) {
                checkCancelled()
                if (++visited > 5000 || depth > 32 || System.nanoTime() - started > 5_000_000_000L) {
                    partial = true; return
                }
                try {
                    if (!path.normalize().startsWith(root) || Files.isSymbolicLink(path) ||
                        !path.toRealPath().startsWith(root.toRealPath())) { failures++; partial = true; return }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.newDirectoryStream(path).use { children ->
                            for (child in children) {
                                if (visited >= 5000 || System.nanoTime() - started > 5_000_000_000L) {
                                    partial = true; break
                                }
                                visit(child, depth + 1)
                            }
                        }
                        if (delete && path != root) {
                            try { Files.delete(path) } catch (_: Exception) { failures++; partial = true }
                        }
                    } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        val size = Files.size(path)
                        if (!delete || Files.deleteIfExists(path)) {
                            bytes = if (Long.MAX_VALUE - bytes < size) Long.MAX_VALUE else bytes + size
                            files++
                        } else { failures++; partial = true }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failures++; partial = true }
            }
            visit(root, 0)
        }
        return CacheResult(bytes, files, failures, partial)
    }
}
