package com.example.darktodo.widget

import android.app.DownloadManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.darktodo.MainActivity
import com.example.darktodo.R
import com.example.darktodo.data.SystemMonitorAppearancePrefs
import com.example.darktodo.monitor.MonitorRepository
import com.example.darktodo.monitor.MonitorSnapshot
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

object SystemMonitorWidget {
    private const val TAG = "SystemMonitorWidget"
    internal const val ACTION_REFRESH = "com.example.darktodo.SYSTEM_MONITOR_REFRESH"
    private const val WORK_NAME = "clarity-system-monitor-periodic"
    private const val TIMEOUT_MS = 6_500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { refreshAndRender(app) }
                .onFailure { Log.e(TAG, "Unable to refresh System Monitor widgets", it) }
        }
    }

    fun renderAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { render(app) }
                .onFailure { Log.e(TAG, "Unable to render System Monitor widgets", it) }
        }
    }

    internal fun receive(context: Context, finished: () -> Unit, collect: Boolean) {
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        val finish = Runnable { if (completed.compareAndSet(false, true)) finished() }
        // Binder/disk calls may not cooperate with coroutine cancellation. Bound broadcast lifetime too.
        handler.postDelayed(finish, 8_000L)
        scope.launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    reconcileSchedule(app)
                    if (collect) refreshAndRender(app) else render(app)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to process System Monitor widget broadcast", error)
            } finally {
                handler.removeCallbacks(finish)
                finish.run()
            }
        }
    }

    internal suspend fun refreshAndRender(context: Context) {
        if (!refreshLock.tryLock()) return
        try {
            try {
                withTimeout(TIMEOUT_MS) { MonitorRepository.refresh(context) }
                failureFile(context).delete()
            } catch (cancelled: CancellationException) {
                recordFailure(context)
                runCatching { render(context) }
                    .onFailure { Log.e(TAG, "Unable to render stale metrics after refresh cancellation", it) }
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to collect System Monitor metrics; retaining last snapshot", error)
                recordFailure(context)
            }
            render(context)
        } finally {
            refreshLock.unlock()
        }
    }

    private fun failureFile(context: Context) =
        File(context.noBackupFilesDir, "system-monitor-widget-refresh-failed")

    private fun recordFailure(context: Context) {
        runCatching { failureFile(context).writeText(System.currentTimeMillis().toString()) }
    }

    internal fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, SystemMonitorWidgetReceiver::class.java)
        )

    internal fun reconcileSchedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (ids(context).isEmpty()) {
            workManager.cancelUniqueWork(WORK_NAME)
        } else {
            val request = PeriodicWorkRequestBuilder<SystemMonitorWidgetWorker>(30, TimeUnit.MINUTES)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = MonitorRepository.last(context)
        val style = SystemMonitorAppearancePrefs.load(context)
        val failedAt = runCatching { failureFile(context).readText().toLongOrNull() }.getOrNull() ?: 0L
        ids(context).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.system_monitor_widget)
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceIn(120, 800)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260).coerceIn(100, 800)
            val expanded = SystemMonitorWidgetState.expanded(height)
            views.setViewVisibility(R.id.monitor_widget_details, if (expanded) View.VISIBLE else View.GONE)

            // Render customized background (including wallpaper support if configured)
            views.setImageViewBitmap(
                R.id.monitor_widget_bg_image,
                SystemMonitorWidgetRendering.background(context, style, width, height)
            )

            bindMetrics(views, snapshot, style)
            val capturedAt = snapshot?.capturedAt
            val time = capturedAt?.takeIf { it > 0 }?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            }.orEmpty()
            views.setTextViewText(
                R.id.monitor_widget_updated,
                SystemMonitorWidgetState.status(capturedAt, failedAt, System.currentTimeMillis(), time)
            )
            views.setOnClickPendingIntent(R.id.monitor_widget_refresh, PendingIntent.getBroadcast(
                context, id,
                Intent(context, SystemMonitorWidgetReceiver::class.java).setAction(ACTION_REFRESH)
                    .setData(Uri.parse("clarity://system-monitor/refresh/$id")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            views.setOnClickPendingIntent(R.id.monitor_widget_info, activity(context, id, "info", null))
            views.setOnClickPendingIntent(R.id.monitor_widget_battery, activity(context, id, "battery", Intent("android.intent.action.POWER_USAGE_SUMMARY")))
            views.setOnClickPendingIntent(R.id.monitor_widget_storage, activity(context, id, "storage", Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)))
            views.setOnClickPendingIntent(R.id.monitor_widget_downloads, activity(context, id, "downloads", Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)))

            // Clear cache action launches confirmation dialog Activity
            val clearCacheIntent = Intent(context, SystemMonitorActionActivity::class.java)
                .setAction(SystemMonitorActionActivity.ACTION_CLEAR_CACHE_PROMPT)
                .setData(Uri.parse("clarity://system-monitor/clear-cache/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.monitor_widget_clear_cache,
                PendingIntent.getActivity(
                    context,
                    id * 10 + 4,
                    clearCacheIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(id, views)
        }
    }

    private fun bindMetrics(
        views: RemoteViews,
        snapshot: MonitorSnapshot?,
        style: com.example.darktodo.data.SystemMonitorAppearance
    ) {
        val values = snapshot?.values.orEmpty()
        fun value(key: String) = SystemMonitorWidgetState.value(values, key)

        val model = value("Model")
        views.setTextViewText(R.id.monitor_widget_model, if (model != "Unavailable") model else "Device overview")

        views.setTextViewText(R.id.monitor_widget_ram, value("Available RAM"))
        views.setTextViewText(R.id.monitor_widget_storage_val, value("Available internal storage"))

        val batteryPct = value("Battery")
        val charging = value("Charging")
        val batteryText = if (batteryPct != "Unavailable") {
            if (charging != "Unavailable" && charging.isNotBlank()) "$batteryPct · $charging" else batteryPct
        } else "Unavailable"
        views.setTextViewText(R.id.monitor_widget_battery_val, batteryText)

        // App cache metric
        val cacheVal = value("Clarity app storage: cache (subset of data)")
        views.setTextViewText(R.id.monitor_widget_cache_val, cacheVal)

        views.setTextViewText(R.id.monitor_widget_android, "Android: ${value("Android")}")
        views.setTextViewText(R.id.monitor_widget_security, "Security patch: ${value("Security patch")}")

        // Apply appearance styling
        val titleColor = android.graphics.Color.parseColor(style.titleColor)
        val valueColor = android.graphics.Color.parseColor(style.valueColor)
        val labelColor = android.graphics.Color.parseColor(style.labelColor)
        val accentColor = android.graphics.Color.parseColor(style.accentColor)
        val secondaryColor = android.graphics.Color.parseColor(style.secondaryColor)

        views.setInt(R.id.monitor_widget_title, "setTextColor", titleColor)
        views.setInt(R.id.monitor_widget_model, "setTextColor", labelColor)
        views.setInt(R.id.monitor_widget_refresh, "setTextColor", accentColor)
        views.setInt(R.id.monitor_widget_clear_cache, "setTextColor", accentColor)
        views.setInt(R.id.monitor_widget_downloads, "setTextColor", accentColor)

        views.setInt(R.id.monitor_widget_ram_label, "setTextColor", labelColor)
        views.setInt(R.id.monitor_widget_ram, "setTextColor", valueColor)
        views.setInt(R.id.monitor_widget_storage_label, "setTextColor", labelColor)
        views.setInt(R.id.monitor_widget_storage_val, "setTextColor", valueColor)
        views.setInt(R.id.monitor_widget_battery_label, "setTextColor", labelColor)
        views.setInt(R.id.monitor_widget_battery_val, "setTextColor", valueColor)
        views.setInt(R.id.monitor_widget_cache_label, "setTextColor", labelColor)
        views.setInt(R.id.monitor_widget_cache_val, "setTextColor", valueColor)

        views.setInt(R.id.monitor_widget_android, "setTextColor", secondaryColor)
        views.setInt(R.id.monitor_widget_security, "setTextColor", secondaryColor)
        views.setInt(R.id.monitor_widget_updated, "setTextColor", labelColor)
    }

    private fun activity(context: Context, id: Int, action: String, destination: Intent?): PendingIntent {
        val info = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_INFO)
            .setData(Uri.parse("clarity://system-monitor/info/$id/$action"))
        // Resolve first, then pin the component: a widget tap launches an Activity directly.
        val resolved = runCatching {
            destination?.resolveActivityInfo(context.packageManager, 0)
                ?.takeIf {
                    it.enabled && it.exported && (it.permission == null ||
                        context.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
                }?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()
        val intent = if (resolved != null && destination != null) destination.setComponent(resolved) else info
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, id * 10 + when (action) {
            "battery" -> 1
            "storage" -> 2
            "downloads" -> 3
            else -> 0
        }, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class SystemMonitorWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SystemMonitorWidget.ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_ENABLED, AppWidgetManager.ACTION_APPWIDGET_RESTORED,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED, AppWidgetManager.ACTION_APPWIDGET_DELETED,
            AppWidgetManager.ACTION_APPWIDGET_DISABLED -> {
                val pending = goAsync()
                val collect = intent.action == SystemMonitorWidget.ACTION_REFRESH ||
                    intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
                    intent.action == AppWidgetManager.ACTION_APPWIDGET_RESTORED
                SystemMonitorWidget.receive(context, { pending.finish() }, collect)
            }
            else -> super.onReceive(context, intent)
        }
    }
}

class SystemMonitorWidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (SystemMonitorWidget.ids(applicationContext).isEmpty()) {
            SystemMonitorWidget.reconcileSchedule(applicationContext)
            return Result.success()
        }
        return try {
            runBlocking { SystemMonitorWidget.refreshAndRender(applicationContext) }
            Result.success()
        } catch (error: Exception) {
            Log.e("SystemMonitorWidget", "Periodic System Monitor update failed; next attempt is at the scheduled interval", error)
            Result.success()
        }
    }
}
