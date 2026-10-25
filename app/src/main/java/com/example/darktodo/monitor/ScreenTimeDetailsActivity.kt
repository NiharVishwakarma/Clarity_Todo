package com.example.darktodo.monitor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.darktodo.UiTokens
import com.example.darktodo.widget.SystemMonitorWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Today's per-app usage breakdown, opened from the System Monitor screen-time tile. Data stays in memory. */
class ScreenTimeDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM_WIDGET = "com.example.darktodo.monitor.extra.FROM_WIDGET"
        private const val WELLBEING_ACTION = "android.settings.WELLBEING_SETTINGS"
    }

    private var refreshKey by mutableIntStateOf(0)
    private var knownAccess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        knownAccess = ScreenTime.hasAccess(this)
        if (ScreenTime.shouldOpenUsageAccess(
                intent.getBooleanExtra(EXTRA_FROM_WIDGET, false), savedInstanceState != null, knownAccess)) {
            openUsageAccess()
        }
        val wellbeing = wellbeingIntent()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = UiTokens.Accent, background = UiTokens.Background)) {
                ScreenTimeDetailsScreen(
                    refreshKey = refreshKey,
                    wellbeing = wellbeing,
                    onBack = ::finish,
                    onRefresh = { refreshKey++ },
                    onGrantAccess = ::openUsageAccess,
                    onOpenWellbeing = { if (!launchSafely(it)) Toast.makeText(this, "Digital Wellbeing could not be opened.", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
        // Recheck on every return (e.g. from Settings) so widgets follow a grant or revocation.
        val access = ScreenTime.hasAccess(this)
        if (access != knownAccess) {
            knownAccess = access
            SystemMonitorWidget.renderAll(applicationContext)
        }
    }

    private fun openUsageAccess() {
        val destinations = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
        if (destinations.any { launchable(it) && launchSafely(it) }) return
        Toast.makeText(this, "Usage Access settings are not available on this device.", Toast.LENGTH_LONG).show()
    }

    /** The undocumented Wellbeing action is offered only when it resolves to an accessible activity. */
    private fun wellbeingIntent(): Intent? = Intent(WELLBEING_ACTION).takeIf(::launchable)

    private fun launchable(intent: Intent): Boolean {
        val info = runCatching { intent.resolveActivityInfo(packageManager, 0) }.getOrNull() ?: return false
        return info.enabled && info.exported &&
            (info.permission == null || checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
    }

    private fun launchSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Log.w("ScreenTime", "Destination unavailable", error)
        false
    } catch (error: SecurityException) {
        Log.w("ScreenTime", "Destination denied", error)
        false
    }
}

private data class AppRow(val packageName: String, val label: String, val icon: ImageBitmap?, val millis: Long)

private sealed interface DetailsState {
    data object Loading : DetailsState
    data object PermissionRequired : DetailsState
    data class Loaded(val result: ScreenTimeResult, val rows: List<AppRow>) : DetailsState
}

private fun loadDetails(context: Context): DetailsState {
    val result = ScreenTime.read(context)
    if (result.status == ScreenTimeStatus.PERMISSION_REQUIRED) return DetailsState.PermissionRequired
    val pm = context.packageManager
    val iconPx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val rows = result.ranked.map { usage ->
        // Apps hidden by package visibility or uninstalled keep their package name and a generic icon.
        val info = runCatching { pm.getApplicationInfo(usage.packageName, 0) }.getOrNull()
        val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: usage.packageName
        val icon = info?.let { runCatching { pm.getApplicationIcon(it).toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull() }
        AppRow(usage.packageName, label, icon, usage.durationMillis)
    }
    return DetailsState.Loaded(result, rows)
}

@Composable
private fun ScreenTimeDetailsScreen(
    refreshKey: Int,
    wellbeing: Intent?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenWellbeing: (Intent) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf<DetailsState>(DetailsState.Loading) }
    LaunchedEffect(refreshKey) {
        state = withContext(Dispatchers.IO) { loadDetails(context.applicationContext) }
    }
    Column(
        Modifier.fillMaxSize().background(UiTokens.Background).safeDrawingPadding()
            .padding(horizontal = UiTokens.PageMargin)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = UiTokens.SmallGap), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = UiTokens.Ink)
            }
            Text("Screen time", color = UiTokens.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, "Refresh", tint = UiTokens.Accent)
            }
        }
        when (val current = state) {
            DetailsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UiTokens.Accent)
            }
            DetailsState.PermissionRequired -> Message(
                "Usage Access required",
                "Allow Usage Access for Clarity to see how long you used each app today. Calculations stay on this device and are never backed up or uploaded."
            ) {
                Button(onClick = onGrantAccess, colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Accent)) {
                    Text("Open Usage Access settings")
                }
            }
            is DetailsState.Loaded -> Loaded(current, wellbeing, onOpenWellbeing)
        }
    }
}

@Composable
private fun Loaded(state: DetailsState.Loaded, wellbeing: Intent?, onOpenWellbeing: (Intent) -> Unit) {
    val result = state.result
    val max = state.rows.firstOrNull()?.millis?.coerceAtLeast(1) ?: 1L
    LazyColumn(verticalArrangement = Arrangement.spacedBy(UiTokens.SmallGap)) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(UiTokens.CardRadius)).background(UiTokens.Surface)
                    .padding(16.dp)
            ) {
                Text("Today · Since midnight", color = UiTokens.Muted, fontSize = 13.sp)
                Text(
                    if (result.status == ScreenTimeStatus.UNAVAILABLE) "Unavailable" else ScreenTime.duration(result.durationMillis),
                    color = UiTokens.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold
                )
                if (result.status == ScreenTimeStatus.PARTIAL) {
                    Text("Partial: Android's usage history has gaps today (for example, a restart).",
                        color = UiTokens.Muted, fontSize = 12.sp)
                }
                Text("Android usage estimate, excluding Clarity. May differ from Digital Wellbeing.",
                    color = UiTokens.Subdued, fontSize = 12.sp)
                if (wellbeing != null) {
                    TextButton(onClick = { onOpenWellbeing(wellbeing) }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Open Digital Wellbeing", color = UiTokens.Accent)
                    }
                }
            }
        }
        if (state.rows.isEmpty()) {
            item {
                Message(
                    "No app usage yet",
                    if (result.status == ScreenTimeStatus.UNAVAILABLE)
                        "Android has not provided usage data since midnight. Unlock the device, use some apps, then refresh."
                    else "No other apps have been used since midnight."
                )
            }
        } else {
            item { Text("Apps", color = UiTokens.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = UiTokens.SmallGap)) }
            items(state.rows, key = { it.packageName }) { row -> AppUsageRow(row, max) }
            item { Spacer(Modifier.height(UiTokens.SectionGap)) }
        }
    }
}

@Composable
private fun AppUsageRow(row: AppRow, max: Long) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(UiTokens.RowRadius)).background(UiTokens.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.icon != null) {
            Image(row.icon, null, Modifier.size(36.dp))
        } else {
            Icon(Icons.Outlined.Android, null, Modifier.size(36.dp), tint = UiTokens.Muted)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.label, color = UiTokens.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { (row.millis.toFloat() / max).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = UiTokens.Accent,
                trackColor = UiTokens.SurfaceRaised,
                drawStopIndicator = {}
            )
        }
        Text(ScreenTime.appDuration(row.millis), color = UiTokens.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Message(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(UiTokens.CardRadius)).background(UiTokens.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SmallGap)
    ) {
        Text(title, color = UiTokens.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = UiTokens.Muted, fontSize = 14.sp)
        action?.invoke()
    }
}
