package com.example.darktodo.backup

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAutomationContractTest {
    private val main = File("src/main/java/com/example/darktodo").let {
        if (it.isDirectory) it else File("app/src/main/java/com/example/darktodo")
    }
    private fun source(path: String) = File(main, path).readText()

    @Test fun disablingCancelsAndEnablingRequestsTheExistingUniqueDebouncedWork() {
        val manager = source("backup/BackupManager.kt")
        val setter = manager.substringAfter("fun setAutoEnabled").substringBefore("fun enableAutoIfUnset")
        assertTrue(setter.contains("putBoolean(\"auto\", enabled)"))
        assertTrue(setter.contains("if (enabled) BackupScheduler.requestBackup(context) else BackupScheduler.cancel(context)"))
        val scheduler = source("backup/BackupScheduler.kt")
        assertTrue(scheduler.contains("enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE"))
        assertTrue(scheduler.contains("cancelUniqueWork(WORK_NAME)"))
        assertTrue(scheduler.contains("setInitialDelay(20, TimeUnit.SECONDS)"))
        assertTrue(scheduler.contains("NetworkType.CONNECTED"))
        assertTrue(scheduler.contains("isStopped || !BackupManager.autoEnabled(applicationContext)"))
        assertTrue(scheduler.contains("outcome.retry && !isStopped && BackupManager.autoEnabled(applicationContext)"))
    }

    @Test fun manualActionsRespectExplicitOffAndTheSwitchRemainsAvailableDuringUploads() {
        val manager = source("backup/BackupManager.kt")
        val initializer = manager.substringAfter("fun enableAutoIfUnset").substringBefore("fun markOnboarded")
        assertTrue(initializer.contains("!state(context).contains(\"auto\")"))
        val screen = source("BackupScreens.kt")
        assertFalse(screen.contains("setAutoEnabled(context, true)"))
        assertTrue(screen.contains("Switch("))
        assertTrue(screen.contains("enabled = email != null"))
        assertTrue(screen.contains("BackupManager.setAutoEnabled(context, it)"))
        assertTrue(manager.contains("if (automatic && !autoEnabled(context))"))
        val restore = manager.substringAfter("fun restore(").substringBefore("fun deleteBackup")
        assertFalse(restore.contains("putBoolean(\"auto\", true)"))
        assertFalse(restore.contains("setAutoEnabled(context, true)"))
    }

    @Test fun restoredAssetsInvalidateCachesAndRefreshEveryWidgetWithoutExportingBiometricKeys() {
        val manager = source("backup/BackupManager.kt")
        assertTrue(manager.contains("LocalWidgetFonts.invalidateAfterRestore()"))
        assertTrue(manager.contains("WidgetBackgrounds.invalidateAfterRestore()"))
        assertTrue(manager.contains("SystemMonitorWidget.renderAll(context)"))
        assertTrue(manager.contains("MonitorRepository.withRefreshPaused"))
        assertTrue(manager.contains("LocalVaultStore.usePasswordAuthentication(context)"))
        assertTrue(manager.contains("it.startsWith(\"wallpaper_\")"))
        assertTrue(manager.contains("android.graphics.Typeface.Builder(temporary).build()"))
        assertFalse(manager.contains("clarity-vault-biometric.bin"))
        assertFalse(manager.contains("AndroidKeyStore"))
        assertTrue(source("data/LocalWidgetFonts.kt").contains("catalog.invalidate()"))
        assertTrue(source("monitor/MonitorRepository.kt").contains("put(\"restoredFromBackup\", true)"))
    }
}
