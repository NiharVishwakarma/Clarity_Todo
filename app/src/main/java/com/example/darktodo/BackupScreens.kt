package com.example.darktodo

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.backup.BackupManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Returns a function that obtains a Drive access token (showing Google's consent screen only when needed)
 * and then calls [onAuthorized] with the token and account email.
 */
@Composable
internal fun rememberDriveAuthorizer(onError: (String) -> Unit, onAuthorized: (token: String, email: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val authorized by rememberUpdatedState(onAuthorized)
    val failed by rememberUpdatedState(onError)
    fun handle(result: AuthorizationResult) {
        val token = result.accessToken
        if (token == null) failed("Google did not return access. Please try again.")
        else authorized(token, runCatching { result.toGoogleSignInAccount()?.email }.getOrNull())
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
                .fold({ handle(it) }, { failed(it.message ?: "Google sign-in failed.") })
        } else failed("Google sign-in was cancelled.")
    }
    return {
        Identity.getAuthorizationClient(context).authorize(BackupManager.authorizationRequest())
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                else handle(result)
            }
            .addOnFailureListener { failed(it.message ?: "Could not reach Google Play services.") }
    }
}

private fun formatTime(millis: Long): String = if (millis <= 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

private enum class BackupConfirm { BACKUP, RESTORE, DELETE, DISCONNECT }

/** Settings > Backup & Restore. */
@Composable
internal fun BackupSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<BackupConfirm?>(null) }
    var pending by remember { mutableStateOf<((String, String?) -> Unit)?>(null) }
    val authorize = rememberDriveAuthorizer(
        onError = { message = it; busy = false },
        onAuthorized = { token, email -> pending?.invoke(token, email) }
    )
    val email = remember(revision) { BackupManager.email(context) }
    val auto = remember(revision) { BackupManager.autoEnabled(context) }
    val last = remember(revision) { BackupManager.lastBackupAt(context) }
    val status = remember(revision) { BackupManager.lastStatus(context) }

    fun withToken(work: (token: String) -> String) {
        busy = true; message = null
        pending = { token, _ ->
            pending = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { work(token) }.getOrElse { it.message ?: "Something went wrong." } }
                message = result; busy = false; revision++
            }
        }
        authorize()
    }

    fun connect() {
        busy = true; message = null
        pending = { token, accountEmail ->
            pending = null
            BackupManager.setConnected(context, accountEmail)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (BackupManager.findBackup(token) == null) {
                            BackupManager.setAutoEnabled(context, true)
                            BackupManager.backupNow(context, token).message
                        } else "Connected. A backup already exists in this account: use Restore backup, or Backup now to replace it."
                    }.getOrElse { it.message ?: "Could not check for an existing backup." }
                }
                message = result; busy = false; revision++
            }
        }
        authorize()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoRow("Google account", email ?: "Not connected")
        InfoRow("Backup status", when {
            email == null -> "Not connected"
            !auto -> "Automatic backup paused"
            status.isNotBlank() -> status
            else -> "Automatic"
        })
        InfoRow("Last backup", formatTime(last))
        Text(
            "Backups go to a private app folder in your Google Drive that other apps cannot read. Your vault is uploaded only as its encrypted file: the master password and biometric keys never leave this device. Tasks and settings are stored as ordinary data in that private folder.",
            color = SecMuted, fontSize = 12.sp
        )
        message?.let { Text(it, color = SecAccent, fontSize = 13.sp) }
        if (busy) Text("Working…", color = SecMuted, fontSize = 13.sp)
        if (email == null) {
            if (!busy) ActionRow("Connect Google account") { connect() }
        } else if (!busy) {
            ActionRow("Backup now") { if (auto) withToken { BackupManager.backupNow(context, it).message } else confirm = BackupConfirm.BACKUP }
            ActionRow("Restore backup") { confirm = BackupConfirm.RESTORE }
            ActionRow("Delete backup") { confirm = BackupConfirm.DELETE }
            ActionRow("Disconnect Google account") { confirm = BackupConfirm.DISCONNECT }
        }
    }

    confirm?.let { action ->
        val (title, body) = when (action) {
            BackupConfirm.BACKUP -> "Back up now?" to "This replaces any existing cloud backup with this device's data and turns automatic backup on."
            BackupConfirm.RESTORE -> "Restore backup?" to "This replaces the tasks, settings and vault on this device with your cloud backup."
            BackupConfirm.DELETE -> "Delete cloud backup?" to "The backup in your Google Drive is permanently removed and automatic backup stops. Data on this device is not changed."
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
                        BackupConfirm.BACKUP -> withToken { BackupManager.setAutoEnabled(context, true); BackupManager.backupNow(context, it).message }
                        BackupConfirm.RESTORE -> {
                            busy = true; message = null
                            pending = { token, _ ->
                                pending = null
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) { runCatching { BackupManager.restore(context, token) } }
                                    outcome.fold(
                                        onSuccess = { (context as? Activity)?.recreate() },
                                        onFailure = { message = it.message ?: "Restore failed."; busy = false; revision++ }
                                    )
                                }
                            }
                            authorize()
                        }
                        BackupConfirm.DELETE -> withToken { if (BackupManager.deleteBackup(context, it)) "Cloud backup deleted." else "No cloud backup was found." }
                        BackupConfirm.DISCONNECT -> {
                            BackupManager.disconnect(context)
                            com.example.darktodo.backup.BackupScheduler.cancel(context)
                            message = "Disconnected. Your cloud backup was kept."; revision++
                        }
                    }
                }) { Text(if (action == BackupConfirm.RESTORE) "Restore" else "Continue", color = SecAccent) }
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
    var hadVault by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var backupTime by remember { mutableStateOf("") }
    var restoring by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<((String, String?) -> Unit)?>(null) }
    val authorize = rememberDriveAuthorizer(
        onError = { message = it; stage = WelcomeStage.ASK },
        onAuthorized = { value, email -> pending?.invoke(value, email) }
    )

    fun check() {
        stage = WelcomeStage.CHECKING; message = null
        pending = { value, email ->
            pending = null
            scope.launch {
                val found = withContext(Dispatchers.IO) { runCatching { BackupManager.findBackup(value) } }
                found.fold(
                    onSuccess = { remote ->
                        BackupManager.setConnected(context, email)
                        token = value
                        if (remote == null) { BackupManager.setAutoEnabled(context, true); stage = WelcomeStage.NONE }
                        else { backupTime = remote.modifiedTime.replace('T', ' ').substringBefore('.').removeSuffix("Z") + " UTC"; stage = WelcomeStage.FOUND }
                    },
                    onFailure = { message = it.message ?: "Could not check for a backup."; stage = WelcomeStage.ASK }
                )
            }
        }
        authorize()
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
                        ActionRow("Look for my backup") { check() }
                        ActionRow("Start fresh") { BackupManager.markOnboarded(context); onFinished(false) }
                    }
                    WelcomeStage.CHECKING -> Text("Looking for your backup…", color = SecInk, fontSize = 16.sp)
                    WelcomeStage.FOUND -> {
                        Text("Welcome Back to Clarity", color = SecInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("A previous backup was found" + if (backupTime.isNotBlank()) " (saved $backupTime)." else ".", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Text("Restore your data?", color = SecInk, fontSize = 16.sp)
                        message?.let { Text(it, color = SecAccent, fontSize = 13.sp, textAlign = TextAlign.Center) }
                        if (restoring) Text("Restoring…", color = SecMuted, fontSize = 13.sp) else {
                            ActionRow("Restore Backup") {
                                val value = token ?: return@ActionRow
                                restoring = true; message = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { BackupManager.restore(context, value) } }
                                    result.fold(
                                        onSuccess = { hadVault = it; stage = WelcomeStage.DONE },
                                        onFailure = { message = it.message ?: "Restore failed."; restoring = false }
                                    )
                                }
                            }
                            // The cloud backup is never deleted; automatic backup stays off so it is not overwritten.
                            ActionRow("Start Fresh") { BackupManager.setAutoEnabled(context, false); BackupManager.markOnboarded(context); onFinished(false) }
                        }
                    }
                    WelcomeStage.NONE -> {
                        Text("No backup found", color = SecInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Your data will be backed up automatically to this Google account from now on.", color = SecMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
                        ActionRow("Continue") { BackupManager.markOnboarded(context); onFinished(false) }
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
}
