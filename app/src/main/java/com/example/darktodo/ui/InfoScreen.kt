package com.example.darktodo.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.darktodo.UiTokens
import com.example.darktodo.monitor.CacheResult
import com.example.darktodo.monitor.DiscoveryResult
import com.example.darktodo.monitor.MonitorCache
import com.example.darktodo.monitor.MonitorDocuments
import com.example.darktodo.monitor.MonitorFormats
import com.example.darktodo.monitor.MonitorReports
import com.example.darktodo.monitor.MonitorRepository
import com.example.darktodo.monitor.MonitorSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val securityKeys = setOf("Security patch", "OS build time (not install date)", "OS install date", "OS update availability",
    "Device lock configured", "Storage encryption status")

@Composable
fun InfoScreen(onOpenAppearance: (() -> Unit)? = null, onSnapshotUpdated: () -> Unit = {}) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = UiTokens.Accent, onPrimary = UiTokens.Ink,
        primaryContainer = UiTokens.SurfaceRaised, onPrimaryContainer = UiTokens.Ink,
        secondary = UiTokens.Muted, onSecondary = UiTokens.Background,
        secondaryContainer = UiTokens.SurfaceRaised, onSecondaryContainer = UiTokens.Ink,
        background = UiTokens.Background, onBackground = UiTokens.Ink,
        surface = UiTokens.Surface, onSurface = UiTokens.Ink,
        surfaceVariant = UiTokens.SurfaceRaised, onSurfaceVariant = UiTokens.Muted,
        outline = UiTokens.Subdued
    )) {
        Surface(color = UiTokens.Background, contentColor = UiTokens.Ink, modifier = Modifier.fillMaxSize()) {
            InfoMonitorContent(onOpenAppearance, onSnapshotUpdated)
        }
    }
}

@Composable
private fun InfoMonitorContent(onOpenAppearance: (() -> Unit)? = null, onSnapshotUpdated: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val updatedCallback by rememberUpdatedState(onSnapshotUpdated)
    var snapshot by remember { mutableStateOf<MonitorSnapshot?>(null) }
    var loadingSnapshot by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshFailed by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("Loading local snapshot…") }
    var section by rememberSaveable { mutableStateOf("Device") }
    var working by remember { mutableStateOf(false) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var discovery by remember { mutableStateOf<DiscoveryResult?>(null) }
    var documentFilter by rememberSaveable { mutableStateOf("All") }
    var documentSearch by rememberSaveable { mutableStateOf("") }
    var largestFirst by rememberSaveable { mutableStateOf(false) }
    var cache by remember { mutableStateOf<CacheResult?>(null) }
    var confirmCache by remember { mutableStateOf(false) }

    fun work(onCancelled: (() -> String)? = null, block: suspend () -> Unit) {
        if (working) return
        working = true
        operation = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) {
                message = onCancelled?.invoke() ?: "Cancelled. Previous snapshot preserved; an export or cleanup may be partial."
                throw cancelled
            } catch (_: Exception) {
                message = "Operation failed. Previous snapshot preserved. Check access and retry; exported files may be incomplete."
            } finally { working = false }
        }
    }
    suspend fun refresh(): MonitorSnapshot {
        refreshing = true
        val fresh = try {
            MonitorRepository.refresh(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            refreshFailed = true
            throw error
        } finally {
            refreshing = false
            now = System.currentTimeMillis()
        }
        snapshot = fresh
        refreshFailed = false
        updatedCallback()
        return fresh
    }
    fun selected(uri: Uri?, tree: Boolean) {
        pickerPending = false
        if (uri == null) message = if (tree) "Folder selection cancelled." else "Document selection cancelled."
        if (uri != null) work {
            discovery = MonitorDocuments.discover(context, uri, tree)
            message = discovery!!.notes
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected(it, false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { selected(it, true) }
    fun export(uri: Uri?, format: String) {
        pickerPending = false
        if (uri == null) message = "${format.uppercase()} export cancelled; no report written."
        if (uri != null) work {
            val fresh = refresh()
            MonitorReports.export(context, uri, format, fresh)
            message = "Saved ${format.uppercase()} report captured ${MonitorFormats.timestamp(fresh.capturedAt)}."
        }
    }
    val textExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { export(it, "txt") }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { export(it, "csv") }
    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { export(it, "pdf") }
    fun open(intent: Intent, fallback: Intent = Intent(Settings.ACTION_SETTINGS)) {
        message = if (openMonitorIntent(context, intent, fallback)) "Opened Android screen." else "No compatible Android screen or file handler is available."
    }

    LaunchedEffect(lifecycle) {
        snapshot = withContext(Dispatchers.IO) { MonitorRepository.last(context) }
        loadingSnapshot = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                if (!working && !pickerPending) work { refresh(); message = "Snapshot refreshed locally. Auto-refresh every 30 seconds while visible." }
                delay(30_000)
            }
        }
    }
    val sections = listOf("Device", "Memory", "Storage", "Battery", "Security and updates", "Downloads and reports", "Maintenance")
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Local device monitor · no root · no cloud upload", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (onOpenAppearance != null) {
                    SettingsGearButton("System Monitor widget appearance", onClick = onOpenAppearance)
                }
            }
            sections.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { title ->
                        OutlinedButton(onClick = { section = title }, modifier = Modifier.weight(1f)) {
                            Text(if (section == title) "• $title" else title)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !working && !pickerPending, onClick = { work { refresh(); message = "Snapshot refreshed." } }) { Text("Refresh") }
                if (working) OutlinedButton(onClick = { operation?.cancel() }) { Text("Cancel") }
            }
            Text(MonitorFormats.snapshotStatus(snapshot?.capturedAt, now, refreshing || loadingSnapshot, refreshFailed),
                color = if (refreshFailed || snapshot?.let { MonitorFormats.snapshotStale(it.capturedAt, now, false) } == true)
                    UiTokens.Accent else UiTokens.Muted,
                style = MaterialTheme.typography.bodySmall)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text(section, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
        when (section) {
            "Device", "Memory", "Storage", "Battery", "Security and updates" -> {
                val values = snapshot?.values.orEmpty()
                fun memoryKey(key: String) = key.contains("RAM")
                fun storageKey(key: String) = key.contains("storage", ignoreCase = true) && key !in securityKeys
                fun batteryKey(key: String) = key.startsWith("Battery") || key == "Charging" || key.startsWith("Per-app battery")
                val keys = when (section) {
                    "Memory" -> values.keys.filter(::memoryKey)
                    "Storage" -> values.keys.filter(::storageKey)
                    "Battery" -> values.keys.filter(::batteryKey)
                    "Security and updates" -> values.keys.filter { it in securityKeys }
                    else -> values.keys.filter { !memoryKey(it) && !storageKey(it) && !batteryKey(it) && it !in securityKeys }
                }
                if (section == "Memory") item { InfoProgress("RAM utilization", values["RAM utilization"]) }
                if (section == "Storage") item {
                    InfoProgress("Internal storage utilization", values["internal storage utilization"])
                }
                items(keys) { key -> InfoMetric(key, values[key] ?: "Unavailable") }
                if (section == "Device") item {
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }) { Text("About device") }
                }
                if (section == "Security and updates") item {
                    Text("The build timestamp is not the OS install date. Android does not expose reliable OS install dates or universal update availability to ordinary apps.")
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Android security settings") }
                    OutlinedButton(onClick = { open(Intent("android.settings.SYSTEM_UPDATE_SETTINGS")) }) { Text("Check updates in Settings") }
                }
                if (section == "Storage") item {
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }) { Text("Android storage settings") }
                }
                if (section == "Battery") item {
                    Text("Health is the platform condition code, not remaining design capacity. No per-app estimates are invented.")
                    OutlinedButton(onClick = { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) { Text("Android battery usage") }
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }) { Text("Battery saver settings") }
                }
            }
            "Downloads and reports" -> {
                item {
                    Text("Android scoped storage does not expose every download. Select one file or a permitted folder; no broad storage access is requested. Access is session-only. Android 11+ can restrict selecting the Downloads root. An 8-second deadline requests provider cancellation; providers may ignore it, so response time cannot be guaranteed.")
                    OutlinedButton(enabled = !working && !pickerPending, onClick = {
                        pickerPending = true
                        try { documentPicker.launch(arrayOf("*/*")) } catch (_: Exception) { pickerPending = false; message = "No document picker available." }
                    }) { Text("Select document") }
                    OutlinedButton(enabled = !working && !pickerPending, onClick = {
                        pickerPending = true
                        try { folderPicker.launch(null) } catch (_: Exception) { pickerPending = false; message = "No folder picker available." }
                    }) { Text("Select folder") }
                    OutlinedButton(onClick = { open(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    }) }) { Text("Open system Downloads") }
                    discovery?.let { result ->
                        Text("${result.files.size} discovered documents${if (result.partial) " · partial" else ""}")
                        Text(result.notes)
                        Text("Known bytes: ${MonitorFormats.bytes(result.files.mapNotNull { it.size }.fold(0L) { a, b -> if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b })}; unknown sizes: ${result.files.count { it.size == null }}")
                        OutlinedTextField(value = documentSearch, onValueChange = { documentSearch = it.take(200) },
                            label = { Text("Search selected documents") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "APK", "Archives").forEach { filter ->
                                TextButton(onClick = { documentFilter = filter }) { Text(if (filter == documentFilter) "• $filter" else filter) }
                            }
                        }
                        TextButton(onClick = { largestFirst = !largestFirst }) { Text(if (largestFirst) "Sort: largest known size" else "Sort: name") }
                    }
                }
                item {
                    Text("Reports", style = MaterialTheme.typography.titleMedium)
                    Text("Export a newly collected, coherent snapshot with its UTC timestamp. Reports contain device/build details, including model and build fingerprint, but no unique hardware IDs, task data, passwords, or selected document names. Choose the destination yourself. PDF uses Android's native PDF renderer; font glyph coverage depends on Android.")
                    val filename = "clarity-info-${snapshot?.capturedAt ?: System.currentTimeMillis()}"
                    OutlinedButton(enabled = !working && !pickerPending, onClick = { pickerPending = true; try { textExport.launch("$filename.txt") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } }) { Text("Save TXT") }
                    OutlinedButton(enabled = !working && !pickerPending, onClick = { pickerPending = true; try { csvExport.launch("$filename.csv") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } }) { Text("Save CSV") }
                    OutlinedButton(enabled = !working && !pickerPending, onClick = { pickerPending = true; try { pdfExport.launch("$filename.pdf") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } }) { Text("Save PDF") }
                    Text("A failed or cancelled export can leave an incomplete destination document; delete it using your file manager.")
                }
                val documents = discovery?.files.orEmpty().filter { file ->
                    file.name.contains(documentSearch, ignoreCase = true) && when (documentFilter) {
                        "APK" -> file.kind.startsWith("APK")
                        "Archives" -> file.kind.contains("archive", ignoreCase = true)
                        else -> true
                    }
                }.let { files -> if (largestFirst) files.sortedByDescending { it.size ?: -1 } else files.sortedBy { it.name.lowercase(java.util.Locale.ROOT) } }
                items(documents, key = { it.uri.toString() }) { file ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiTokens.CardRadius),
                        colors = CardDefaults.cardColors(containerColor = UiTokens.Surface, contentColor = UiTokens.Ink)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall)
                            Text("${file.kind} · ${MonitorFormats.bytes(file.size)}")
                            Text("Modified: ${file.modified?.let(MonitorFormats::timestamp) ?: "Unavailable"}")
                            TextButton(onClick = {
                                val view = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                message = if (openMonitorIntent(context, view, null)) "Opened selected document." else "Cannot open this document; access may be revoked or no viewer is installed."
                            }) { Text("Open with Android") }
                        }
                    }
                }
            }
            "Maintenance" -> item {
                Text("Only Clarity's cacheDir and codeCacheDir are eligible. Tasks, notes, vault, settings, imports, reports and other apps are not cleared. No RAM boosting, task killing or device-wide cache cleaning.")
                cache?.let { Text("${MonitorFormats.bytes(it.bytes)} · ${it.files} files · ${it.failures} skipped/errors${if (it.partial) " · partial" else ""}") }
                OutlinedButton(enabled = !working && !pickerPending, onClick = { work { cache = MonitorCache.inspect(context); message = "Cache measurement completed; files may change while the app runs." } }) { Text("Measure app cache") }
                OutlinedButton(enabled = !working && !pickerPending, onClick = { confirmCache = true }) { Text("Clear Clarity cache…") }
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Clarity app settings") }
                Text("Traversal is bounded, cancellable and skips symbolic links. Android may recreate caches immediately. Results report actual processed files, not a promise of increased free space.")
            }
        }
    }
    if (confirmCache) AlertDialog(
        onDismissRequest = { confirmCache = false },
        title = { Text("Clear Clarity cache?") },
        text = { Text("Delete only this app's disposable cache and compiled-code cache. Files may be recreated and startup may be slower. This cannot be undone. All task, note, vault and settings data is kept.") },
        confirmButton = { TextButton(enabled = !working, onClick = {
            confirmCache = false
            var cleanupSummary: String? = null
            work(onCancelled = {
                cleanupSummary?.let { "$it Snapshot refresh cancelled; previous snapshot preserved." }
                    ?: "Cache cleanup cancelled; some cache files may already have been deleted."
            }) {
                val result = MonitorCache.clear(context)
                cache = result
                val summary = "Deleted ${MonitorFormats.bytes(result.bytes)} in ${result.files} cache files; ${result.failures} skipped/errors${if (result.partial) "; partial cleanup" else ""}."
                cleanupSummary = summary
                message = summary
                try {
                    refresh()
                    message = "$summary Metrics refreshed."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "$summary Metrics refresh failed; previous successful snapshot remains available."
                }
            }
        }) { Text("Clear cache") } },
        dismissButton = { TextButton(onClick = { confirmCache = false }) { Text("Cancel") } }
    )
}

@Composable
private fun InfoMetric(label: String, value: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(UiTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = UiTokens.Surface, contentColor = UiTokens.Ink)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoProgress(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label: ${value ?: "Unavailable"}", color = UiTokens.Muted)
        MonitorFormats.percentFraction(value)?.let { fraction ->
            LinearProgressIndicator(progress = { fraction }, color = UiTokens.Accent,
                trackColor = UiTokens.SurfaceRaised, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun openMonitorIntent(context: Context, intent: Intent, fallback: Intent?): Boolean {
    for (candidate in listOfNotNull(intent, fallback)) {
        try {
            context.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) { }
        catch (_: SecurityException) { }
    }
    return false
}
