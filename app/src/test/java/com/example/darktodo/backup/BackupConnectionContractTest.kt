package com.example.darktodo.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConnectionContractTest {
    private val main = File("src/main").let { if (it.isDirectory) it else File("app/src/main") }
    private fun source(path: String) = File(main, "java/com/example/darktodo/$path").readText()

    @Test fun authorizationIntentSurvivesRecreationWithoutSavingTokensOrCallbacks() {
        val screen = source("BackupScreens.kt")
        assertTrue(screen.contains("var pending by rememberSaveable { mutableStateOf<BackupAuthorization?>(null) }"))
        assertTrue(screen.contains("var checkingAuthorization by rememberSaveable { mutableStateOf(false) }"))
        assertFalse(screen.contains("pending?.invoke"))
        assertFalse(screen.contains("mutableStateOf<((String, String?) -> Unit)?>"))
        assertTrue(source("MainActivity.kt").contains("rememberSaveable(key = \"page:\$openVault:\$openNotes:\$openNoteId:\$openInfo\")"))
        assertTrue(screen.contains("var token by remember { mutableStateOf<String?>(null) }"))
        assertTrue(screen.indexOf("BackupManager.setConnected(context, accountEmail)") < screen.indexOf("BackupManager.findBackup(token)"))
    }

    @Test fun launchFailuresCancellationAndInvalidResponsesAreVisible() {
        val screen = source("BackupScreens.kt")
        listOf(
            "CommonStatusCodes.DEVELOPER_ERROR", "CommonStatusCodes.NETWORK_ERROR",
            "EXTRA_SEND_INTENT_EXCEPTION", "if (pending == null)", "token.isNullOrBlank()",
            "Google closed the authorization flow before access was granted.",
            "verify the Android OAuth client package/SHA-1",
            "catch (error: Exception)",
            "Google Play services", "if (busy || pending != null) return"
        ).forEach { assertTrue(it, screen.contains(it)) }
        assertFalse(screen.contains("statusCode == CommonStatusCodes.CANCELED) cancelled()"))
        assertTrue(screen.contains("authorize(BackupManager.authorizationRequest())"))
        assertTrue(screen.contains("getAuthorizationResultFromIntent(result.data)"))
    }

    @Test fun privateDriveDestinationScopesAndEncryptedVaultRemainUnchanged() {
        val manager = source("backup/BackupManager.kt")
        val drive = source("backup/DriveClient.kt")
        assertTrue(manager.contains("https://www.googleapis.com/auth/drive.appdata"))
        assertTrue(manager.contains("Scope(\"email\")"))
        assertTrue(manager.contains("\"vault\" to LocalVaultStore.file(context)"))
        assertTrue(drive.contains("spaces=appDataFolder"))
        assertTrue(drive.contains("put(\"parents\", org.json.JSONArray().put(\"appDataFolder\"))"))
        assertTrue(drive.contains("connection.setRequestProperty(\"Authorization\", \"Bearer \$token\")"))
    }
}
