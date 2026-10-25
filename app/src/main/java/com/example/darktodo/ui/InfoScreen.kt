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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val dashboardSurface = Color(0xFF17171B)
private val infoCategories = listOf(
    "Device" to Icons.Outlined.Smartphone,
    "Memory" to Icons.Outlined.Memory,
    "Storage" to Icons.Outlined.Storage,
    "Battery" to Icons.Outlined.BatteryChargingFull,
    "Security and updates" to Icons.Outlined.VerifiedUser,
    "Downloads and reports" to Icons.Outlined.Download,
    "Maintenance" to Icons.Outlined.Build
)

@Composable
fun InfoScreen(onOpenAppearance: (() -> Unit)? = null, onSnapshotUpdated: () -> Unit = {}) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = UiTokens.Accent, onPrimary = UiTokens.Ink,
        primaryContainer = UiTokens.SurfaceRaised, onPrimaryContainer = UiTokens.Ink,
        secondary = UiTokens.Muted, onSecondary = UiTokens.Background,
        secondaryContainer = UiTokens.SurfaceRaised, onSecondaryContainer = UiTokens.Ink,
        background = Color.Black, onBackground = UiTokens.Ink,
        surface = dashboardSurface, onSurface = UiTokens.Ink,
        surfaceVariant = UiTokens.SurfaceRaised, onSurfaceVariant = UiTokens.Muted,
        outline = UiTokens.Subdued
    )) {
        Surface(color = Color.Black, contentColor = UiTokens.Ink, modifier = Modifier.fillMaxSize()) {
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
    var message by remember { mutableStateOf<String?>(null) }
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
            } catch (error: Exception) {
                android.util.Log.e("InfoScreen", "Monitor operation failed", error)
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
            message = "Saved ${format.uppercase()} report captured ${MonitorFormats.localTimestamp(fresh.capturedAt)}."
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
                if (!working && !pickerPending) work { refresh(); message = null }
                delay(30_000)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device monitor", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (snapshot?.restoredFromBackup == true) "Restored snapshot · Refresh for this device"
                        else MonitorFormats.dashboardStatus(snapshot?.capturedAt, now, refreshing || loadingSnapshot, refreshFailed),
                        color = if (refreshFailed) UiTokens.Accent else UiTokens.Muted,
                        fontSize = 12.sp
                    )
                }
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = UiTokens.Accent, strokeWidth = 2.dp)
                IconButton(enabled = working || !pickerPending, onClick = {
                    if (working) operation?.cancel() else work { refresh(); message = null }
                }) {
                    Icon(if (working) Icons.Outlined.Close else Icons.Outlined.Refresh,
                        contentDescription = if (working) "Cancel monitor operation" else "Refresh device metrics",
                        tint = if (pickerPending && !working) UiTokens.Subdued else UiTokens.Ink)
                }
                if (onOpenAppearance != null) {
                    SettingsGearButton("System Monitor widget appearance", onClick = onOpenAppearance)
                }
            }
            Text("Local snapshots · 30s while visible · No root", color = UiTokens.Muted, fontSize = 12.sp)
            Text("No monitoring uploads; saved snapshots may be included in optional Drive backups.",
                color = UiTokens.Muted, fontSize = 12.sp)
            message?.let { Text(it, color = UiTokens.Muted, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                infoCategories.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (title, icon) ->
                            InfoCategoryTile(title, icon, section == title, Modifier.weight(1f)) { section = title }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text(section.replace(" and ", " & "), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp))
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
                if (section == "Device") item { DeviceSummaryCard(values) }
                if (section == "Memory") item {
                    MetricSummaryCard("RAM used", values["Used RAM"], values["RAM utilization"],
                        "Total" to values["Total RAM"], "Available" to values["Available RAM"])
                }
                if (section == "Storage") item {
                    MetricSummaryCard("Internal storage used", values["Used internal storage"], values["internal storage utilization"],
                        "Total" to values["Total internal storage"], "Available" to values["Available internal storage"])
                }
                if (section == "Battery") item {
                    MetricSummaryCard("Battery", values["Battery"], values["Battery"],
                        "State" to values["Charging"], "Power source" to values["Battery power source"])
                }
                val summarizedKeys = when (section) {
                    "Memory" -> setOf("Used RAM", "Total RAM", "Available RAM", "RAM utilization")
                    "Storage" -> setOf("Used internal storage", "Total internal storage", "Available internal storage", "internal storage utilization")
                    "Battery" -> setOf("Battery", "Charging", "Battery power source")
                    else -> emptySet()
                }
                item {
                    InfoDetailsCard(keys.filterNot { it in summarizedKeys }.map { key ->
                        val display = if (key == "OS build time (not install date)")
                            MonitorFormats.localIsoTimestamp(values[key] ?: "Unavailable") else values[key] ?: "Unavailable"
                        (if (key == "Device") "Device codename" else key) to display
                    })
                }
                if (section == "Device") item {
                    CompactActionRow("About device", Icons.Outlined.Smartphone, onClick = { open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) })
                }
                if (section == "Security and updates") item {
                    InfoCaption("System properties only, not a security audit. Build time is not the OS install date; Android does not expose reliable install dates or universal update availability.")
                    CompactActionRow("Android security settings", Icons.Outlined.VerifiedUser, onClick = { open(Intent(Settings.ACTION_SECURITY_SETTINGS)) })
                    CompactActionRow("Check updates in Settings", Icons.Outlined.Refresh, onClick = { open(Intent("android.settings.SYSTEM_UPDATE_SETTINGS")) })
                }
                if (section == "Storage") item {
                    CompactActionRow("Android storage settings", Icons.Outlined.Storage, onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) })
                }
                if (section == "Battery") item {
                    InfoCaption("Health is the platform condition code, not remaining design capacity. No per-app estimates are invented.")
                    CompactActionRow("Android battery usage", Icons.Outlined.BatteryChargingFull, onClick = { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) })
                    CompactActionRow("Battery saver settings", Icons.Outlined.BatteryChargingFull, onClick = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) })
                }
            }
            "Downloads and reports" -> {
                item {
                    InfoCaption("Select a document or permitted folder; access is session-only, not a scan of all downloads. Android 11+ can restrict the Downloads root. Discovery requests cancellation after 8 seconds; providers may ignore it.")
                    CompactActionRow("Select document", Icons.Outlined.Description, enabled = !working && !pickerPending, onClick = {
                        pickerPending = true
                        try { documentPicker.launch(arrayOf("*/*")) } catch (_: Exception) { pickerPending = false; message = "No document picker available." }
                    })
                    CompactActionRow("Select folder", Icons.Outlined.FolderOpen, enabled = !working && !pickerPending, onClick = {
                        pickerPending = true
                        try { folderPicker.launch(null) } catch (_: Exception) { pickerPending = false; message = "No folder picker available." }
                    })
                    CompactActionRow("Open system Downloads", Icons.Outlined.Download, onClick = { open(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    }) })
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
                    InfoCaption("Export a fresh snapshot with UTC timestamps to your chosen destination. Includes device/build details, not unique hardware IDs, tasks, passwords or selected document names. PDF font coverage depends on Android.")
                    val filename = "clarity-info-${snapshot?.capturedAt ?: System.currentTimeMillis()}"
                    CompactActionRow("Save TXT report", Icons.Outlined.Description, enabled = !working && !pickerPending, onClick = { pickerPending = true; try { textExport.launch("$filename.txt") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } })
                    CompactActionRow("Save CSV report", Icons.Outlined.Description, enabled = !working && !pickerPending, onClick = { pickerPending = true; try { csvExport.launch("$filename.csv") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } })
                    CompactActionRow("Save PDF report", Icons.Outlined.Description, enabled = !working && !pickerPending, onClick = { pickerPending = true; try { pdfExport.launch("$filename.pdf") } catch (_: Exception) { pickerPending = false; message = "No export picker available." } })
                    InfoCaption("Failed or cancelled exports may leave an incomplete document; remove it with your file manager.")
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
                            InfoCaption("Modified: ${file.modified?.let(MonitorFormats::localTimestamp) ?: "Unavailable"}")
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
                InfoCaption("Only Clarity's disposable cache and code cache. Tasks, notes, vault, settings, imports, reports and other apps are kept. No RAM boosting, task killing or device-wide cleaning.")
                cache?.let { Text("${MonitorFormats.bytes(it.bytes)} · ${it.files} files · ${it.failures} skipped/errors${if (it.partial) " · partial" else ""}") }
                CompactActionRow("Measure app cache", Icons.Outlined.Storage, enabled = !working && !pickerPending, onClick = { work { cache = MonitorCache.inspect(context); message = "Cache measurement completed; files may change while the app runs." } })
                CompactActionRow("Clear Clarity cache…", Icons.Outlined.DeleteOutline, destructive = true, enabled = !working && !pickerPending, onClick = { confirmCache = true })
                CompactActionRow("Clarity app settings", ClarityIcons.Settings, onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
                InfoCaption("Bounded, cancellable traversal skips symbolic links. Android may recreate caches immediately; processed bytes do not guarantee increased free space.")
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
private fun InfoCategoryTile(title: String, icon: ImageVector, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (isSelected) UiTokens.Accent.copy(alpha = .12f) else dashboardSurface,
        shape = RoundedCornerShape(16.dp), modifier = modifier.heightIn(min = 56.dp).semantics { selected = isSelected }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = if (isSelected) UiTokens.Accent else UiTokens.Muted, modifier = Modifier.size(20.dp))
            Text(title.replace(" and ", " & "), color = UiTokens.Ink, fontSize = 13.sp,
                lineHeight = 16.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun DeviceSummaryCard(values: Map<String, String>) {
    Surface(color = dashboardSurface, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = UiTokens.Muted, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Device information", fontSize = 12.sp, color = UiTokens.Muted)
                Text(MonitorFormats.deviceIdentity(values), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Brand: ${values["Brand"] ?: "Unavailable"}", color = UiTokens.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun InfoDetailsCard(entries: List<Pair<String, String>>) {
    Surface(color = dashboardSurface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (entries.isEmpty()) InfoCaption("No snapshot available. Refresh to collect device information.")
            entries.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = UiTokens.Subdued.copy(alpha = .3f))
                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, fontSize = 12.sp, color = UiTokens.Muted)
                            Text(value, fontSize = 14.sp, color = UiTokens.Ink)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, fontSize = 12.sp, color = UiTokens.Muted, modifier = Modifier.weight(.45f))
                            Text(value, fontSize = 14.sp, color = UiTokens.Ink, modifier = Modifier.weight(.55f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSummaryCard(label: String, value: String?, percentage: String?,
    first: Pair<String, String?>, second: Pair<String, String?>) {
    Surface(color = dashboardSurface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = UiTokens.Muted, fontSize = 14.sp)
            Text(value ?: "Unavailable", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            MonitorFormats.percentFraction(percentage)?.let { fraction ->
                if (percentage != value) Text("$percentage used", color = UiTokens.Muted, fontSize = 13.sp)
                LinearProgressIndicator(progress = { fraction }, color = UiTokens.Accent,
                    trackColor = UiTokens.SurfaceRaised, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(first, second).forEach { (name, reading) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, color = UiTokens.Muted, fontSize = 12.sp)
                        Text(reading ?: "Unavailable", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCaption(text: String) {
    Text(text, color = UiTokens.Muted, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun CompactActionRow(label: String, icon: ImageVector, enabled: Boolean = true,
    destructive: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = dashboardSurface,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.heightIn(min = 52.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tint = if (!enabled) UiTokens.Subdued else if (destructive) UiTokens.Accent else UiTokens.Ink
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(label, color = tint, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = UiTokens.Muted, modifier = Modifier.size(18.dp))
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
