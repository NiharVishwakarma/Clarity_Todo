package com.example.darktodo.widget

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.example.darktodo.monitor.MonitorCache
import com.example.darktodo.monitor.MonitorFormats
import com.example.darktodo.monitor.MonitorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trampoline Activity for System Monitor widget actions requiring explicit user confirmation or dialogs.
 * Ensures destructive actions like cache clearing always prompt the user before execution.
 */
class SystemMonitorActionActivity : Activity() {
    companion object {
        const val ACTION_CLEAR_CACHE_PROMPT = "com.example.darktodo.monitor.CLEAR_CACHE_PROMPT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            ACTION_CLEAR_CACHE_PROMPT -> showClearCacheDialog()
            else -> finish()
        }
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Clear Clarity Cache?")
            .setMessage("This will delete only Clarity's disposable app cache and compiled code cache (cacheDir and codeCacheDir).\n\n" +
                "• Your tasks, notes, vault credentials, backgrounds, and settings are NEVER deleted.\n" +
                "• Other apps' data and caches cannot be cleared directly by third-party apps on Android.\n\n" +
                "Do you want to proceed?")
            .setPositiveButton("Clear Clarity Cache") { _, _ ->
                executeClearCache()
            }
            .setNeutralButton("Android Settings") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setOnDismissListener {
                if (!isFinishing) finish()
            }
            .show()
    }

    private fun executeClearCache() {
        val app = applicationContext
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { MonitorCache.clear(app) }.getOrNull()
            }
            if (result != null) {
                val freed = MonitorFormats.bytes(result.bytes)
                Toast.makeText(app, "Clarity cache cleared ($freed deleted across ${result.files} files)", Toast.LENGTH_SHORT).show()
                // Refresh monitor snapshot and home-screen widgets
                withContext(Dispatchers.IO) {
                    runCatching { MonitorRepository.refresh(app) }
                }
                SystemMonitorWidget.renderAll(app)
            } else {
                Toast.makeText(app, "Could not clear cache", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}
