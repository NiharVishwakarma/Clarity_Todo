package com.example.darktodo

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.text.format.Formatter
import com.example.darktodo.backup.BackupManager
import com.example.darktodo.backup.BackupPoint
import com.example.darktodo.ui.ClarityGlyph
import com.example.darktodo.ui.ClarityIcons
import com.example.darktodo.ui.SettingsNavRow
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * Returns a function that obtains a Drive access token (showing Google's consent screen only when needed)
 * and then calls [onAuthorized] with the token and account email.
 */
@Composable
internal fun rememberDriveAuthorizer(
    onError: (String) -> Unit,
    onCancelled: () -> Unit = {},
    onAuthorized: (token: String, email: String?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val authorized by rememberUpdatedState(onAuthorized)
    val failed by rememberUpdatedState(onError)
    val cancelled by rememberUpdatedState(onCancelled)
    var launching by remember { mutableStateOf(false) }
    fun authorizationError(error: Throwable): String = when ((error as? ApiException)?.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR -> "Google rejected this app's OAuth setup. Register an Android OAuth client for com.example.darktodo and the SHA-1 of this APK's signing key."
        CommonStatusCodes.NETWORK_ERROR -> "Could not connect to Google. Check your internet connection and try again."
        else -> "Google authorization failed${(error as? ApiException)?.statusCode?.let { " (code $it)" }.orEmpty()}. Check Google Play services, OAuth consent and Drive API configuration, then try again."
    }
    fun handle(result: AuthorizationResult) {
        launching = false
        val token = result.accessToken
        if (token.isNullOrBlank()) failed("Google did not grant Drive access. Check that the drive.appdata scope is enabled for this app.")
        else authorized(token, runCatching { result.toGoogleSignInAccount()?.email }.getOrNull())
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        launching = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
                .fold({ handle(it) }, { failed(authorizationError(it)) })
        } else if (result.data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true) {
            failed("Google authorization could not open. Check Google Play services and try again.")
        } else if (result.resultCode == Activity.RESULT_CANCELED) cancelled()
        else failed("Google authorization did not complete. Try again or check the OAuth configuration.")
    }
    return {
        if (!launching) {
            launching = true
            try {
                Identity.getAuthorizationClient(context).authorize(BackupManager.authorizationRequest())
                .addOnSuccessListener { result ->
                    val pending = result.pendingIntent
                    if (result.hasResolution()) {
                        if (pending == null) {
                            launching = false
                            failed("Google did not provide an authorization screen. Check Google Play services and try again.")
                        } else {
                            runCatching { launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                                .onFailure { launching = false; failed(authorizationError(it)) }
                        }
                    } else handle(result)
                }
                .addOnFailureListener {
                    launching = false
                    if ((it as? ApiException)?.statusCode == CommonStatusCodes.CANCELED) cancelled()
                    else failed(authorizationError(it))
                }
            } catch (error: Exception) {
                launching = false
                failed(authorizationError(error))
            }
        }
    }
}

private fun formatTime(millis: Long): String = if (millis <= 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

private enum class BackupConfirm { BACKUP, RESTORE, DELETE, DISCONNECT }
private enum class BackupAuthorization { CONNECT, BACKUP, HISTORY, RESTORE, DELETE }

private fun backupDate(point: BackupPoint): String = point.createdAt?.let(::formatTime) ?: "Date unavailable"

@Composable
private fun HelpItem(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, color = SecInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = SecMuted, fontSize = 13.sp)
    }
}

@Composable
private fun BackupInfoDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SecCard,
        title = { Text(title, color = SecInk) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = SecAccent) } }
    )
}

/** Help icon for the Data heading; opens a summary of what Drive backup stores and keeps. */
@Composable
internal fun BackupHelpButton() {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = "About Google Drive backup" },
        contentAlignment = Alignment.Center
    ) { ClarityGlyph(ClarityIcons.Info, size = 20.dp, tint = UiTokens.Muted) }
    if (open) BackupInfoDialog("About Google Drive backup", onDismiss = { open = false }) {
        HelpItem("Where backups go", "Backups are saved in Clarity's private app-data folder in your Google Drive. Clarity requests access to that folder only, not to the rest of your Drive.")
        HelpItem("Vault", "Only the vault's encrypted file is uploaded. Your master password and the biometric key are not uploaded; the biometric key stays in this device's Keystore and is not restored, so after a restore you unlock with your master password.")
        HelpItem("What is included", "Tasks and history, Notes, Calendar and To-Do settings, app typography, widget backgrounds and wallpapers, and the vault file. Imported custom fonts are not backed up. Apart from the vault, this data is stored as ordinary data in the private folder; Clarity does not add its own encryption to it.")
        HelpItem("Snapshots", "The latest ${BackupManager.RETENTION_LIMIT} verified new snapshots are kept, including Safety points. Older ones are removed only after a new upload is verified. Legacy backups are kept separately. Restoring first creates a Safety point and does not delete history.")
        HelpItem("Deleting", "Delete backup history permanently removes every Clarity restore point from your Drive app-data folder and turns automatic backup off. Data on this device is not changed. Disconnecting keeps your cloud backups.")
    }
}

@Composable
private fun BackupHistoryDialog(
    points: List<BackupPoint>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (BackupPoint) -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SecCard, shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Backup history", color = SecInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Dates use this device's time zone. The latest ${BackupManager.RETENTION_LIMIT} new snapshots are kept, including Safety points. Older snapshots are deleted only after a new backup is uploaded and verified. Legacy backups are kept separately. Restore temporarily keeps extra snapshots.",
                    color = SecMuted, fontSize = 12.sp
                )
                if (points.isEmpty()) Text("No backups yet. Create your first backup with Backup now, then refresh this history.", color = SecInk)
                points.forEach { point ->
                    Surface(color = UiTokens.Background, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(backupDate(point), color = SecInk, fontWeight = FontWeight.Bold)
                            val type = when (point.type) {
                                "manual" -> "Manual"
                                "automatic" -> "Automatic"
                                "safety" -> "Safety (before restore)"
                                else -> if (point.legacy) "Legacy backup · date is Drive's last modification" else "Type unavailable"
                            }
                            Text(type, color = SecMuted, fontSize = 12.sp)
                            if (!point.legacy && !point.verified) Text("Upload not yet verified. Restore will check it before changing data.", color = SecAccent, fontSize = 12.sp)
                            Text(
                                listOfNotNull(
                                    point.size?.let { Formatter.formatFileSize(context, it) },
                                    point.version?.let { "Format $it" }
                                ).joinToString(" · ").ifBlank { "Additional metadata unavailable" },
                                color = SecMuted, fontSize = 12.sp
                            )
                            Text("ID: ${point.id}", color = SecMuted, fontSize = 11.sp)
                            TextButton(onClick = { onRestore(point) }) { Text("Restore this backup", color = SecAccent) }
                        }
                    }
                }
                TextButton(onClick = onRefresh) { Text("Refresh history", color = SecAccent) }
                TextButton(onClick = onDismiss) { Text("Close", color = SecMuted) }
            }
        }
    }
}

/** Settings > Backup & Restore. */
@Composable
internal fun BackupSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<BackupConfirm?>(null) }
    var points by remember { mutableStateOf<List<BackupPoint>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<BackupPoint?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var backupOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<BackupAuthorization?>(null) }
    val authorize = rememberDriveAuthorizer(
        onError = { pending = null; message = it; busy = false },
        onCancelled = { pending = null; message = "Google authorization was cancelled. Your local data is unchanged."; busy = false },
        onAuthorized = { token, accountEmail ->
            val action = pending
            pending = null
            if (action == null) {
                message = "No Google connection request is active. Tap Connect Google account to try again."
                busy = false
            } else {
                busy = true
                if (action == BackupAuthorization.CONNECT) {
                    BackupManager.setConnected(context, accountEmail)
                    revision++
                }
                scope.launch {
                    if (action == BackupAuthorization.HISTORY) {
                        val result = withContext(Dispatchers.IO) { runCatching { BackupManager.history(token) } }
                        result.fold(
                            onSuccess = { points = it; showHistory = true; message = null },
                            onFailure = { message = "Could not load backup history. ${it.message ?: "Check your internet connection."} Tap Backup history to retry." }
                        )
                        busy = false
                        return@launch
                    }
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            when (action) {
                                BackupAuthorization.CONNECT -> {
                                    if (BackupManager.findBackup(token) == null) {
                                        BackupManager.setAutoEnabled(context, true)
                                        BackupManager.backupNow(context, token, operationId = backupOperation ?: error("Missing backup operation ID.")).let {
                                            if (!it.ok) throw java.io.IOException(it.message)
                                            it.message
                                        }
                                    } else "Connected. Existing restore points were kept. Open Backup history to choose one, or Backup now to create another."
                                }
                                BackupAuthorization.BACKUP -> {
                                    BackupManager.setAutoEnabled(context, true)
                                    BackupManager.backupNow(context, token, operationId = backupOperation ?: error("Missing backup operation ID.")).let {
                                        if (!it.ok) throw java.io.IOException(it.message)
                                        it.message
                                    }
                                }
                                BackupAuthorization.RESTORE -> {
                                    BackupManager.restore(context, token, selectedId ?: error("Select a backup before restoring."))
                                    "Backup restored."
                                }
                                BackupAuthorization.DELETE ->
                                    if (BackupManager.deleteBackup(context, token)) "Cloud backup history deleted." else "No cloud backup was found."
                                BackupAuthorization.HISTORY -> error("History is loaded separately.")
                            }
                        }
                    }
                    result.fold(
                        onSuccess = {
                            message = it
                            if (action == BackupAuthorization.BACKUP || action == BackupAuthorization.CONNECT) backupOperation = null
                            if (action == BackupAuthorization.RESTORE) (context as? Activity)?.recreate()
                        },
                        onFailure = { message = it.message ?: "Google Drive operation failed. Check your connection and Drive API access." }
                    )
                    busy = false
                    revision++
                }
            }
        }
    )
    val email = remember(revision) { BackupManager.email(context) }
    val auto = remember(revision) { BackupManager.autoEnabled(context) }
    val last = remember(revision) { BackupManager.lastBackupAt(context) }
    val status = remember(revision) { BackupManager.lastStatus(context) }
    val lastError = remember(revision) { BackupManager.lastError(context) }

    fun withToken(action: BackupAuthorization) {
        if (busy || pending != null) return
        if (action == BackupAuthorization.BACKUP || action == BackupAuthorization.CONNECT) {
            if (backupOperation == null) backupOperation = UUID.randomUUID().toString()
        }
        busy = true; message = null
        pending = action
        authorize()
    }

    fun connect() {
        withToken(BackupAuthorization.CONNECT)
    }

    val idle = !busy && pending == null
    var showAccount by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val autoLabel = when {
        email == null -> "Not connected"
        auto -> "On"
        else -> "Paused"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        message?.let { Text(it, color = SecAccent, fontSize = 13.sp) }
        lastError?.takeIf { it != message }?.let { Text("Last backup attempt failed: $it", color = SecAccent, fontSize = 12.sp) }
        if (!idle) Text("Working…", color = SecMuted, fontSize = 13.sp)
        if (email == null) {
            if (idle) SettingsNavRow(Icons.Outlined.AccountCircle, "Connect Google account", "Sign in to back up to Google Drive") { connect() }
        } else if (idle) {
            SettingsNavRow(Icons.Outlined.AccountCircle, "Google account", email) { showAccount = true }
            SettingsNavRow(ClarityIcons.Backup, "Back up now", "Create a new backup snapshot") {
                if (auto) withToken(BackupAuthorization.BACKUP) else confirm = BackupConfirm.BACKUP
            }
            SettingsNavRow(ClarityIcons.History, "Backup history", "View and restore previous snapshots") { withToken(BackupAuthorization.HISTORY) }
            SettingsNavRow(ClarityIcons.Layout, "Backup settings", "Automatic backups and retention") { showSettings = true }
        }
        Surface(color = SecCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Backup status", color = SecMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Last backup: ${formatTime(last)} · Automatic backup: $autoLabel", color = SecMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { showStatus = true }) { Text("Details", color = SecAccent, fontSize = 12.sp) }
            }
        }
        if (email != null && idle) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { confirm = BackupConfirm.DISCONNECT }) { Text("Disconnect Google account", color = SecMuted, fontSize = 13.sp) }
                Box {
                    TextButton(onClick = { showMore = true }) { Text("More", color = SecMuted, fontSize = 13.sp) }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }, containerColor = SecRaised) {
                        DropdownMenuItem(
                            text = { Text("Delete backup history", color = SecAccent) },
                            onClick = { showMore = false; confirm = BackupConfirm.DELETE }
                        )
                    }
                }
            }
        }
    }

    if (showAccount) BackupInfoDialog("Google account", onDismiss = { showAccount = false }) {
        HelpItem("Connected account", email ?: "Not connected")
        HelpItem("Disconnecting", "Disconnect stops backups from this device. Cloud backups are kept, and Google's access grant is not revoked.")
        TextButton(onClick = { showAccount = false; confirm = BackupConfirm.DISCONNECT }) { Text("Disconnect Google account", color = SecAccent) }
    }

    if (showSettings) BackupInfoDialog("Backup settings", onDismiss = { showSettings = false }) {
        HelpItem("Automatic backup: $autoLabel", "Automatic backup turns on after Back up now or a restore. Changes to tasks and settings are then uploaded in the background when the network is available.")
        HelpItem("Retention", "The latest ${BackupManager.RETENTION_LIMIT} verified new snapshots are kept, including Safety points. This limit is fixed. Legacy backups are kept separately.")
    }

    if (showStatus) BackupInfoDialog("Backup status", onDismiss = { showStatus = false }) {
        HelpItem("Connection", email ?: "Not connected")
        HelpItem("Last successful backup", formatTime(last))
        HelpItem("Automatic backup", autoLabel)
        if (status.isNotBlank() && email != null) HelpItem("Latest result", status)
        lastError?.let { HelpItem("Last failed attempt", it) }
        if (email != null && !auto) HelpItem(
            "Why is it paused?",
            "Clarity does not record the reason. Automatic backup is off until you run Back up now or restore a backup. It is also turned off by disconnecting, choosing Start Fresh, or deleting backup history."
        )
    }

    if (showHistory) BackupHistoryDialog(
        points,
        onDismiss = { showHistory = false },
        onRefresh = { showHistory = false; withToken(BackupAuthorization.HISTORY) },
        onRestore = {
            selected = it
            selectedId = it.id
            showHistory = false
            confirm = BackupConfirm.RESTORE
        }
    )

    confirm?.let { action ->
        val (title, body) = when (action) {
            BackupConfirm.BACKUP -> "Back up now?" to "This creates a new restore point and turns automatic backup on. The latest ${BackupManager.RETENTION_LIMIT} new snapshots are kept; legacy backups are kept separately."
            BackupConfirm.RESTORE -> "Restore selected backup?" to
                "${selected?.let(::backupDate) ?: "Date unavailable"}\nID: ${selectedId.orEmpty()}\n\nThis replaces current tasks, notes, settings and any backed-up encrypted vault with this snapshot. If it has no vault, your existing vault is kept. You will need the selected vault's master password. A verified Safety restore point is created first; if that fails, nothing is restored. Other restore points are not changed."
            BackupConfirm.DELETE -> "Delete all cloud backups?" to "All Clarity restore points, including legacy and Safety backups, will be permanently deleted from this account's private app-data folder. Automatic backup stops. Data on this device is not changed."
            BackupConfirm.DISCONNECT -> "Disconnect Google account?" to "Automatic backup stops on this device. Your cloud backup is kept. To also revoke Clarity's access, use your Google Account's security settings."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = SecCard,
            title = { Text(title, color = SecInk) },
            text = { Text(body, color = SecMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        BackupConfirm.BACKUP -> withToken(BackupAuthorization.BACKUP)
                        BackupConfirm.RESTORE -> withToken(BackupAuthorization.RESTORE)
                        BackupConfirm.DELETE -> withToken(BackupAuthorization.DELETE)
                        BackupConfirm.DISCONNECT -> {
                            BackupManager.disconnect(context)
                            com.example.darktodo.backup.BackupScheduler.cancel(context)
                            message = "Disconnected. Your cloud backup was kept."; revision++
                        }
                    }
                }) {
                    Text(
                        when (action) { BackupConfirm.RESTORE -> "Restore"; BackupConfirm.DELETE -> "Delete all backups"; else -> "Continue" },
                        color = SecAccent
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = SecMuted) } }
        )
    }
}

private enum class WelcomeStage { ASK, CHECKING, FOUND, NONE, DONE }

/** First-launch screen: offers to restore a previous Google Drive backup. [onFinished] receives whether data was restored. */
@Composable
internal fun WelcomeBackGate(onFinished: (restored: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(WelcomeStage.ASK) }
    var message by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var hadVault by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var backupTime by remember { mutableStateOf("") }
    var backupId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var checkingAuthorization by rememberSaveable { mutableStateOf(false) }
    val authorize = rememberDriveAuthorizer(
        onError = { checkingAuthorization = false; message = it; notice = null; stage = WelcomeStage.ASK },
        onCancelled = { checkingAuthorization = false; message = null; notice = "Google sign-in was cancelled. Your local data is unchanged; you can try again."; stage = WelcomeStage.ASK },
        onAuthorized = { value, email ->
            if (!checkingAuthorization) {
                message = "The Google connection request was interrupted. Please try again."
                stage = WelcomeStage.ASK
            } else {
                checkingAuthorization = false
                stage = WelcomeStage.CHECKING
                scope.launch {
                    val found = withContext(Dispatchers.IO) { runCatching { BackupManager.findBackup(value) } }
                    found.fold(
                        onSuccess = { remote ->
                            BackupManager.setConnected(context, email)
                            token = value
                            if (remote == null) stage = WelcomeStage.NONE
                            else {
                                backupId = remote.id
                                backupTime = backupDate(remote)
                                stage = WelcomeStage.FOUND
                            }
                        },
                        onFailure = { message = it.message ?: "Could not check for a backup. Confirm Drive API access and try again."; stage = WelcomeStage.ASK }
                    )
                }
            }
        }
    )

    fun check() {
        if (stage == WelcomeStage.CHECKING || checkingAuthorization) return
        stage = WelcomeStage.CHECKING; message = null; notice = null
        checkingAuthorization = true
        authorize()
    }

    fun restoreBackup() {
        val value = token ?: return
        if (restoring) return
        confirmRestore = false
        restoring = true
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BackupManager.restore(context, value, backupId ?: error("Select a backup before restoring.")) }
            }
            result.fold(
                onSuccess = { hadVault = it; stage = WelcomeStage.DONE },
                onFailure = { message = it.message ?: "Restore failed. Your local data was not replaced."; restoring = false }
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = SecCard, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (stage) {
                    WelcomeStage.ASK -> {
                        Text("Welcome to Clarity", color = SecInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Used Clarity before? Sign in with Google to look for your backup. Checking changes nothing.", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        message?.let { Text(it, color = SecAccent, fontSize = 13.sp, textAlign = TextAlign.Center) }
                        notice?.let { Text(it, color = SecMuted, fontSize = 13.sp, textAlign = TextAlign.Center) }
                        if (checkingAuthorization) Text("Waiting for Google authorization…", color = SecMuted, fontSize = 13.sp)
                        else {
                            ActionRow("Look for my backup") { check() }
                            ActionRow("Start fresh") { BackupManager.markOnboarded(context); onFinished(false) }
                        }
                    }
                    WelcomeStage.CHECKING -> Text("Looking for your backup…", color = SecInk, fontSize = 16.sp)
                    WelcomeStage.FOUND -> {
                        Text("Welcome Back to Clarity", color = SecInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("A previous backup was found" + if (backupTime.isNotBlank()) " (saved $backupTime)." else ".", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Text("Restoring uses this specific backup and replaces this device's tasks, notes, settings, and any backed-up vault. A Safety restore point is created first. Other restore points are kept. You can choose older backups later in Settings > Data > Backup history.", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        message?.let { Text(it, color = SecAccent, fontSize = 13.sp, textAlign = TextAlign.Center) }
                        if (restoring) Text("Restoring…", color = SecMuted, fontSize = 13.sp) else {
                            ActionRow("Restore Backup") { confirmRestore = true }
                            // The cloud backup is never deleted; automatic backup stays off so it is not overwritten.
                            ActionRow("Start Fresh") { BackupManager.setAutoEnabled(context, false); BackupManager.markOnboarded(context); onFinished(false) }
                        }
                    }
                    WelcomeStage.NONE -> {
                        Text("No backup found", color = SecInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("No backup was found in this Google account. No local data was changed.", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        ActionRow("Start fresh") {
                            BackupManager.setAutoEnabled(context, true)
                            BackupManager.markOnboarded(context)
                            onFinished(false)
                        }
                        ActionRow("Back") { stage = WelcomeStage.ASK; message = null; notice = null }
                    }
                    WelcomeStage.DONE -> {
                        Text(if (hadVault) "Vault Restored Successfully" else "Backup Restored", color = SecInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            if (hadVault) "Unlock your Vault with your previous master password. Fingerprint / Face unlock can be turned on again right after your first unlock, because it is tied to this device's secure hardware."
                            else "Your tasks, calendar history and settings are back.",
                            color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                        ActionRow("Continue") { BackupManager.markOnboarded(context); onFinished(true) }
                    }
                }
            }
        }
    }
    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        containerColor = SecCard,
        title = { Text("Replace local Clarity data?", color = SecInk) },
        text = { Text("Selected: $backupTime\nID: ${backupId.orEmpty()}\n\nThis replaces local tasks, notes, settings, and any backed-up encrypted vault. An existing vault is kept if the backup has no vault. A Safety restore point is created first; if that fails, local data is unchanged. Nothing changes unless you confirm.", color = SecMuted) },
        confirmButton = { TextButton(onClick = { restoreBackup() }) { Text("Restore", color = SecAccent) } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel", color = SecMuted) } }
    )
}
