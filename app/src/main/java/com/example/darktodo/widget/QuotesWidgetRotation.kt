package com.example.darktodo.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.darktodo.data.QuotesWidgetPrefs
import java.util.concurrent.TimeUnit

object QuotesWidgetRotation {
    private const val WORK_NAME = "clarity-quotes-widget-rotation"

    fun reconcile(context: Context) {
        val settings = QuotesWidgetPrefs.load(context)
        val work = WorkManager.getInstance(context)
        if (!settings.rotation.scheduled || StickerWidget.quoteIds(context).isEmpty()) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManager restores work after reboot. Doze can delay it; no exact alarm is needed.
        val request = PeriodicWorkRequestBuilder<QuotesWidgetWorker>(
            settings.intervalMinutes.toLong(), TimeUnit.MINUTES
        ).setInitialDelay(settings.intervalMinutes.toLong(), TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class QuotesWidgetWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!QuotesWidgetPrefs.load(applicationContext).rotation.scheduled) return Result.success()
        return try {
            StickerWidget.refreshQuotes(applicationContext)
            Result.success()
        } catch (error: RuntimeException) {
            Log.e("QuotesWidgetWorker", "Could not rotate offline widget quotes", error)
            Result.retry()
        }
    }
}
