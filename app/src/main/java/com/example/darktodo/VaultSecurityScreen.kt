package com.example.darktodo

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.darktodo.data.LocalVaultStore
import com.example.darktodo.data.VaultAudit
import com.example.darktodo.data.VaultAuditReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val SecAccent = Color(0xFFF20D24)
internal val SecInk = Color(0xFFF4F3F5)
internal val SecMuted = Color(0xFFAAA8B0)
internal val SecCard = Color(0xFF19191E)
internal val SecRaised = Color(0xFF222228)

private val autoLockOptions = listOf("1 min" to 60, "2 min" to 120, "5 min" to 300, "15 min" to 900, "Never" to 0)

/** Vault security preferences. Anything that touches vault contents asks for the master password. */
@Composable
internal fun VaultSecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasVault = LocalVaultStore.exists(context)
    var revision by remember { mutableIntStateOf(0) }
    var autoLock by remember { mutableIntStateOf(LocalVaultStore.autoLockSeconds(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var showChange by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<VaultAuditReport?>(null) }
    val authMode = remember(revision) { LocalVaultStore.primaryAuth(context) }
    val biometricStatus = remember(revision) { LocalVaultStore.canAuthenticateBiometric(context) }
    val biometricOn = authMode == LocalVaultStore.AUTH_BIOMETRIC

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vault Security", color = SecInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Protect your encrypted vault", color = SecMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onBack) { Text("Done", color = SecAccent) }
        }
        message?.let { Text(it, color = SecAccent, fontSize = 13.sp) }

        InfoRow("Master password", if (hasVault) "Set" else "No vault yet")
        if (hasVault) ActionRow("Change master password") { showChange = true; message = null }

        InfoRow(
            "Fingerprint / Face unlock",
            when {
                biometricOn -> "On"
                biometricStatus == BiometricManager.BIOMETRIC_SUCCESS -> "Off"
                else -> "Unavailable"
            }
        )
        Text(
            when {
                biometricOn -> "Unlocking and sensitive actions can use your strongest enrolled biometric."
                biometricStatus == BiometricManager.BIOMETRIC_SUCCESS -> "Turn it on from Vault, Primary unlock, Fingerprint. Face unlock works when your device offers strong (Class 3) face authentication."
                else -> "No strong biometric is enrolled on this device. Add one in Android settings to use it here."
            }, color = SecMuted, fontSize = 12.sp
        )
        if (biometricOn) ActionRow("Turn off biometric unlock") {
            LocalVaultStore.usePasswordAuthentication(context); revision++; message = "Biometric unlock is off. The master password is now your primary unlock."
        }

        Text("Auto lock timer", color = SecInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        Text("Locks an open vault after this much inactivity. The vault always locks when you leave the app.", color = SecMuted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            autoLockOptions.forEach { (label, seconds) ->
                val selected = autoLock == seconds
                Surface(
                    onClick = { autoLock = seconds; LocalVaultStore.setAutoLockSeconds(context, seconds) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) SecAccent.copy(alpha = .2f) else SecCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SecAccent else Color(0xFF33333A))
                ) { Text(label, color = SecInk, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
        }

        if (hasVault) ActionRow("Security audit") { showAudit = true; message = null; report = null }
    }

    if (showChange) PasswordDialog(
        title = "Change master password",
        fields = listOf("Current password", "New password", "Confirm new password"),
        confirmLabel = "Change",
        onDismiss = { showChange = false },
        onSubmit = { values, fail, done ->
            if (values[1] != values[2]) fail("The new passwords do not match.")
            else scope.launch {
                val current = values[0].toCharArray(); val replacement = values[1].toCharArray()
                val error = withContext(Dispatchers.Default) { LocalVaultStore.changeMasterPassword(context, current, replacement) }
                current.fill('\u0000'); replacement.fill('\u0000')
                if (error != null) fail(error) else {
                    done(); revision++
                    message = "Master password changed. Biometric unlock was turned off because it was tied to the old password; turn it on again from the Vault screen."
                }
            }
        }
    )
    if (showAudit) {
        val current = report
        if (current == null) PasswordDialog(
            title = "Security audit",
            fields = listOf("Master password"),
            confirmLabel = "Run audit",
            onDismiss = { showAudit = false },
            onSubmit = { values, fail, done ->
                scope.launch {
                    val chars = values[0].toCharArray()
                    val entries = withContext(Dispatchers.Default) { LocalVaultStore.unlock(context, chars) }
                    chars.fill('\u0000')
                    if (entries == null) fail("That password could not unlock the vault.") else { report = VaultAudit.analyze(entries); done() }
                }
            },
            keepOpenOnDone = true
        ) else AlertDialog(
            onDismissRequest = { showAudit = false; report = null },
            containerColor = SecCard,
            title = { Text("Security audit", color = SecInk) },
            text = { AuditSummary(current) },
            confirmButton = { TextButton(onClick = { showAudit = false; report = null }) { Text("Close", color = SecAccent) } }
        )
    }
}

@Composable
private fun AuditSummary(report: VaultAuditReport) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (report.total == 0) "Your vault is empty." else if (report.issueCount == 0) "No issues found in ${report.total} saved passwords." else "${report.issueCount} of ${report.total} saved passwords need attention.",
            color = SecInk, fontSize = 14.sp
        )
        AuditList("Weak (under 10 characters or low variety)", report.weak)
        AuditList("Reused across entries", report.reused)
        AuditList("Missing password", report.missing)
        Text("Checked on this device only. Passwords are never shown or sent anywhere.", color = SecMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AuditList(label: String, titles: List<String>) {
    if (titles.isEmpty()) return
    Text("$label: ${titles.size}", color = SecAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    titles.distinct().forEach { Text("• $it", color = SecInk, fontSize = 13.sp) }
}

@Composable
private fun PasswordDialog(
    title: String,
    fields: List<String>,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (values: List<String>, fail: (String) -> Unit, done: () -> Unit) -> Unit,
    keepOpenOnDone: Boolean = false
) {
    val values = remember { mutableStateOf(List(fields.size) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SecCard,
        title = { Text(title, color = SecInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { index, label ->
                    BasicTextField(
                        value = values.value[index],
                        onValueChange = { next -> values.value = values.value.toMutableList().also { it[index] = next } },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = TextStyle(color = SecInk, fontSize = 15.sp),
                        cursorBrush = SolidColor(SecAccent),
                        modifier = Modifier.fillMaxWidth().background(SecRaised, RoundedCornerShape(12.dp)).padding(12.dp),
                        decorationBox = { inner -> if (values.value[index].isEmpty()) Text(label, color = Color(0xFF77757D)); inner() }
                    )
                }
                error?.let { Text(it, color = SecAccent, fontSize = 13.sp) }
                if (busy) Text("Working…", color = SecMuted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && values.value.all { it.isNotEmpty() }, onClick = {
                busy = true; error = null
                onSubmit(values.value, { message -> busy = false; error = message }, { busy = false; if (!keepOpenOnDone) onDismiss() })
            }) { Text(confirmLabel, color = SecAccent) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = SecMuted) } }
    )
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Surface(color = SecRaised, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SecInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(value, color = SecMuted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun ActionRow(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SecRaised, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SecInk, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("›", color = SecMuted, fontSize = 22.sp)
        }
    }
}
