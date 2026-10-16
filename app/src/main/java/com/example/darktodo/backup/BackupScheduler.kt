package com.example.darktodo.backup

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Debounced, network-constrained background backup. Work only runs once the user has enabled automatic backup. */
object BackupScheduler {
    private const val WORK_NAME = "clarity-backup"
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    @Volatile private var suspended = false

    fun install(app: Application) {
        BackupManager.PREF_FILES.forEach { name ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> requestBackup(app) }
            app.getSharedPreferences(name, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
            listeners += listener // SharedPreferences keeps listeners weakly.
        }
    }

    fun requestBackup(context: Context) {
        val app = context.applicationContext
        if (suspended || !BackupManager.autoEnabled(app)) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME) }

    /** Runs [block] without treating its own writes (a restore) as user changes. */
    fun <T> withSuspended(block: () -> T): T {
        suspended = true
        try { return block() } finally { suspended = false }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (!BackupManager.autoEnabled(applicationContext)) return Result.success()
        val outcome = BackupManager.backupNow(applicationContext)
        return when {
            outcome.ok -> Result.success()
            outcome.retry -> Result.retry()
            else -> Result.failure()
        }
    }
}
