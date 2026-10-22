package com.example.darktodo.widget

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.example.darktodo.R
import com.example.darktodo.data.WallpaperEngine
import com.example.darktodo.data.WallpaperStudioStore

private const val ACTION_GENERATE_APPLY = "com.example.darktodo.wallpaper.GENERATE_APPLY"
private const val UNIQUE_WORK = "wallpaper-widget-generate-apply"
private const val STATUS_PREFS = "wallpaper_widget"
private const val STATUS_KEY = "status"
private const val RUNNING_KEY = "running"

class WallpaperWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WallpaperWidgetReceiver.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WallpaperWidgetReceiver::class.java))
            if (ids.isNotEmpty()) publish(context, manager, ids)
        }

        private fun publish(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            val status = prefs.getString(STATUS_KEY, null) ?: "Tap to create."
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.wallpaper_widget)
                views.setTextViewText(R.id.wallpaper_widget_status, status)
                views.setContentDescription(R.id.wallpaper_widget_action, "Generate and apply a new home-screen wallpaper")
                views.setContentDescription(R.id.wallpaper_widget_root, "Clarity Wallpaper Studio. $status")
                val image = WallpaperStudioStore.widgetPreview(context)
                if (image != null) views.setImageViewBitmap(R.id.wallpaper_widget_preview, image)
                val intent = Intent(context, WallpaperWidgetActionReceiver::class.java)
                    .setAction(ACTION_GENERATE_APPLY)
                    .setData(android.net.Uri.parse("clarity://wallpaper/widget/$id"))
                val pending = android.app.PendingIntent.getBroadcast(
                    context, id, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.wallpaper_widget_action, pending)
                views.setViewVisibility(R.id.wallpaper_widget_loading, if (prefs.getBoolean(RUNNING_KEY, false)) View.VISIBLE else View.GONE)
                manager.updateAppWidget(id, views)
                image?.recycle()
            }
        }
    }
}

class WallpaperWidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GENERATE_APPLY) return
        val prefs = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        synchronized(WallpaperWidgetReceiver::class.java) {
            if (prefs.getBoolean(RUNNING_KEY, false)) return
            prefs.edit().putBoolean(RUNNING_KEY, true).putString(STATUS_KEY, "Creating wallpaper…").apply()
        }
        refreshWidget(context)
        try {
            val request = OneTimeWorkRequestBuilder<WallpaperApplyWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        } catch (error: Exception) {
            Log.e("WallpaperWidget", "Could not start wallpaper generation", error)
            prefs.edit().putBoolean(RUNNING_KEY, false).putString(STATUS_KEY, "Could not start wallpaper generation.").apply()
            refreshWidget(context)
        }
    }

    private fun refreshWidget(context: Context) {
        try {
            WallpaperWidgetReceiver.updateAll(context)
        } catch (error: Exception) {
            Log.e("WallpaperWidget", "Could not refresh widget status", error)
        }
    }
}

class WallpaperApplyWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        return try {
            val config = WallpaperStudioStore.loadConfig(applicationContext).copy(seed = System.currentTimeMillis() xor System.nanoTime())
            WallpaperStudioStore.saveConfig(applicationContext, config)
            val metrics = applicationContext.resources.displayMetrics
            val width = metrics.widthPixels.coerceIn(720, 4096)
            val height = metrics.heightPixels.coerceIn(1280, 4096)
            val bitmap = WallpaperEngine.render(config, width, height)
            try {
                WallpaperStudioStore.saveWidgetPreview(applicationContext, bitmap)
                val flags = when (WallpaperStudioStore.target(applicationContext)) {
                    WallpaperStudioStore.TARGET_LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperStudioStore.TARGET_BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM
                }
                WallpaperManager.getInstance(applicationContext).setBitmap(bitmap, null, true, flags)
            } finally {
                bitmap.recycle()
            }
            prefs.edit().putBoolean(RUNNING_KEY, false).putString(STATUS_KEY, "Wallpaper applied.").apply()
            refreshWidget()
            Result.success()
        } catch (error: Exception) {
            Log.e("WallpaperWidget", "Could not generate and apply wallpaper from widget", error)
            prefs.edit().putBoolean(RUNNING_KEY, false)
                .putString(STATUS_KEY, error.message?.take(100)?.let { "Could not apply: $it" } ?: "Could not apply wallpaper.")
                .apply()
            refreshWidget()
            Result.failure()
        }
    }

    private fun refreshWidget() {
        try {
            WallpaperWidgetReceiver.updateAll(applicationContext)
        } catch (error: Exception) {
            Log.e("WallpaperWidget", "Could not refresh widget after wallpaper work", error)
        }
    }
}
