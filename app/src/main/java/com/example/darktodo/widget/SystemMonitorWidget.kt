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
    private val renderLock = Any()

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

    private fun render(context: Context) = synchronized(renderLock) {
        val manager = AppWidgetManager.getInstance(context)
        val snapshot = MonitorRepository.last(context)
        val style = SystemMonitorAppearancePrefs.load(context)
        val failedAt = runCatching { failureFile(context).readText().toLongOrNull() }.getOrNull() ?: 0L
        ids(context).forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.system_monitor_widget)
            val options = manager.getAppWidgetOptions(id)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceIn(120, 800)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 260).coerceIn(100, 800)
            val details = SystemMonitorWidgetState.showDetails(height, context.resources.configuration.fontScale)
            val essential = SystemMonitorWidgetState.essentialOnly(height, context.resources.configuration.fontScale)
            val textStyle = if (essential) style.copy(titleSizeSp = 13f, detailSizeSp = 8f) else style
            fun text(viewId: Int, value: String) = bindText(context, views, textStyle, width, viewId, value)
            views.setViewVisibility(R.id.monitor_widget_details, if (details) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.monitor_widget_metrics, if (essential) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.monitor_widget_summary_image, if (essential) View.VISIBLE else View.GONE)
            text(
                R.id.monitor_widget_more,
                if (essential) "All details" else if (details) "More info" else "Android & patch details"
            )
            views.setOnClickPendingIntent(R.id.monitor_widget_more, activity(context, id, "info", null))
            views.setOnClickPendingIntent(R.id.monitor_widget_more_image, activity(context, id, "info", null))

            // Render customized background (including wallpaper support if configured)
            views.setImageViewBitmap(
                R.id.monitor_widget_bg_image,
                SystemMonitorWidgetRendering.background(context, style, width, height)
            )
            val card = SystemMonitorWidgetRendering.cardBackground(
                style, (width - 26) / 2, ((height - if (details) 128 else 96) / 2).coerceAtLeast(48)
            )
            listOf(
                R.id.monitor_widget_ram_bg, R.id.monitor_widget_storage_bg,
                R.id.monitor_widget_battery_bg, R.id.monitor_widget_cache_bg
            ).forEach { views.setImageViewBitmap(it, card) }

            bindMetrics(context, views, snapshot, textStyle, width)
            views.setViewVisibility(R.id.monitor_widget_model_image, if (essential) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.monitor_widget_compact_clear_image, if (essential) View.VISIBLE else View.GONE)
            if (essential) {
                val values = snapshot?.values.orEmpty()
                text(
                    R.id.monitor_widget_summary,
                    "RAM: ${SystemMonitorWidgetState.value(values, "Available RAM")}\n" +
                        "Battery: ${SystemMonitorWidgetState.value(values, "Battery")}"
                )
            }
            val capturedAt = snapshot?.capturedAt
            val time = capturedAt?.takeIf { it > 0 }?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            }.orEmpty()
            text(
                R.id.monitor_widget_updated,
                SystemMonitorWidgetState.status(capturedAt, failedAt, System.currentTimeMillis(), time)
            )
            val refreshAction = PendingIntent.getBroadcast(
                context, id,
                Intent(context, SystemMonitorWidgetReceiver::class.java).setAction(ACTION_REFRESH)
                    .setData(Uri.parse("clarity://system-monitor/refresh/$id")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.monitor_widget_refresh, refreshAction)
            views.setOnClickPendingIntent(R.id.monitor_widget_refresh_image, refreshAction)
            views.setOnClickPendingIntent(R.id.monitor_widget_info, activity(context, id, "info", null))
            views.setOnClickPendingIntent(R.id.monitor_widget_battery, activity(context, id, "battery", Intent("android.intent.action.POWER_USAGE_SUMMARY")))
            views.setOnClickPendingIntent(R.id.monitor_widget_storage, activity(context, id, "storage", Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)))
            views.setOnClickPendingIntent(R.id.monitor_widget_downloads, activity(context, id, "downloads", Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)))
            views.setOnClickPendingIntent(R.id.monitor_widget_downloads_image, activity(context, id, "downloads", Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)))

            // Clear cache action launches confirmation dialog Activity
            val clearCacheIntent = Intent(context, SystemMonitorActionActivity::class.java)
                .setAction(SystemMonitorActionActivity.ACTION_CLEAR_CACHE_PROMPT)
                .setData(Uri.parse("clarity://system-monitor/clear-cache/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val clearCacheAction = PendingIntent.getActivity(
                context, id * 10 + 4, clearCacheIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.monitor_widget_clear_cache, clearCacheAction)
            views.setOnClickPendingIntent(R.id.monitor_widget_compact_clear, clearCacheAction)
            views.setOnClickPendingIntent(R.id.monitor_widget_clear_cache_image, clearCacheAction)
            views.setOnClickPendingIntent(R.id.monitor_widget_compact_clear_image, clearCacheAction)

            manager.updateAppWidget(id, views)
        }
    }

    private fun bindMetrics(
        context: Context,
        views: RemoteViews,
        snapshot: MonitorSnapshot?,
        style: com.example.darktodo.data.SystemMonitorAppearance,
        width: Int
    ) {
        val values = snapshot?.values.orEmpty()
        fun value(key: String) = SystemMonitorWidgetState.value(values, key)
        fun text(id: Int, value: String) = bindText(context, views, style, width, id, value)

        val model = value("Model")
        text(R.id.monitor_widget_title, "System Monitor")
        text(R.id.monitor_widget_refresh, "Refresh")
        text(R.id.monitor_widget_ram_label, "Available RAM")
        text(R.id.monitor_widget_storage_label, "Storage available")
        text(R.id.monitor_widget_battery_label, "Battery")
        text(R.id.monitor_widget_cache_label, "Clarity cache")
        text(R.id.monitor_widget_clear_cache, "Clear cache")
        text(R.id.monitor_widget_downloads, "Downloads")
        text(R.id.monitor_widget_compact_clear, "Clear Clarity cache")
        text(R.id.monitor_widget_model, if (model != "Unavailable") model else "Device overview")

        text(R.id.monitor_widget_ram, value("Available RAM"))
        text(R.id.monitor_widget_storage_val, value("Available internal storage"))

        val batteryPct = value("Battery")
        val charging = value("Charging")
        text(R.id.monitor_widget_battery_val, batteryPct)
        text(R.id.monitor_widget_charging, charging)

        // App cache metric
        val cacheVal = value("Clarity app storage: cache (subset of data)")
        text(R.id.monitor_widget_cache_val, cacheVal)

        text(R.id.monitor_widget_android, "Android: ${value("Android")}")
        text(R.id.monitor_widget_security, "Security patch: ${value("Security patch")}")
    }

    private val textImages = mapOf(
        R.id.monitor_widget_title to R.id.monitor_widget_title_image,
        R.id.monitor_widget_model to R.id.monitor_widget_model_image,
        R.id.monitor_widget_refresh to R.id.monitor_widget_refresh_image,
        R.id.monitor_widget_ram_label to R.id.monitor_widget_ram_label_image,
        R.id.monitor_widget_ram to R.id.monitor_widget_ram_image,
        R.id.monitor_widget_storage_label to R.id.monitor_widget_storage_label_image,
        R.id.monitor_widget_storage_val to R.id.monitor_widget_storage_val_image,
        R.id.monitor_widget_battery_label to R.id.monitor_widget_battery_label_image,
        R.id.monitor_widget_battery_val to R.id.monitor_widget_battery_val_image,
        R.id.monitor_widget_charging to R.id.monitor_widget_charging_image,
        R.id.monitor_widget_cache_label to R.id.monitor_widget_cache_label_image,
        R.id.monitor_widget_cache_val to R.id.monitor_widget_cache_val_image,
        R.id.monitor_widget_clear_cache to R.id.monitor_widget_clear_cache_image,
        R.id.monitor_widget_android to R.id.monitor_widget_android_image,
        R.id.monitor_widget_security to R.id.monitor_widget_security_image,
        R.id.monitor_widget_summary to R.id.monitor_widget_summary_image,
        R.id.monitor_widget_updated to R.id.monitor_widget_updated_image,
        R.id.monitor_widget_compact_clear to R.id.monitor_widget_compact_clear_image,
        R.id.monitor_widget_more to R.id.monitor_widget_more_image,
        R.id.monitor_widget_downloads to R.id.monitor_widget_downloads_image
    )

    private fun bindText(
        context: Context, views: RemoteViews,
        style: com.example.darktodo.data.SystemMonitorAppearance, width: Int,
        id: Int, text: String
    ) {
        val title = id == R.id.monitor_widget_title
        val value = id in setOf(
            R.id.monitor_widget_ram, R.id.monitor_widget_storage_val,
            R.id.monitor_widget_battery_val, R.id.monitor_widget_cache_val, R.id.monitor_widget_summary
        )
        val action = id in setOf(
            R.id.monitor_widget_refresh, R.id.monitor_widget_clear_cache,
            R.id.monitor_widget_compact_clear, R.id.monitor_widget_more, R.id.monitor_widget_downloads
        )
        val secondary = id in setOf(
            R.id.monitor_widget_android, R.id.monitor_widget_security,
            R.id.monitor_widget_charging, R.id.monitor_widget_updated
        )
        val size = when {
            title -> style.titleSizeSp
            value -> style.valueSizeSp
            secondary -> style.detailSizeSp
            else -> style.labelSizeSp
        }
        val color = when {
            title -> style.titleColor
            value -> style.valueColor
            action -> style.accentColor
            secondary -> style.secondaryColor
            else -> style.labelColor
        }
        val weight = if (title) style.titleWeight else if (value) style.valueWeight else 400
        val availableWidth = when (id) {
            R.id.monitor_widget_title, R.id.monitor_widget_model -> width - 100
            R.id.monitor_widget_refresh -> 66
            R.id.monitor_widget_downloads -> 82
            R.id.monitor_widget_more -> width - 114
            R.id.monitor_widget_ram_label, R.id.monitor_widget_ram,
            R.id.monitor_widget_storage_label, R.id.monitor_widget_storage_val,
            R.id.monitor_widget_battery_label, R.id.monitor_widget_battery_val,
            R.id.monitor_widget_charging, R.id.monitor_widget_cache_label,
            R.id.monitor_widget_cache_val, R.id.monitor_widget_clear_cache -> (width - 50) / 2
            else -> width - 24
        }.coerceAtLeast(1)
        val lines = if (id == R.id.monitor_widget_summary) 4
            else if (secondary || value || id == R.id.monitor_widget_more) 2 else 1
        val image = textImages.getValue(id)
        views.setViewVisibility(id, View.GONE)
        views.setViewVisibility(image, View.VISIBLE)
        views.setImageViewBitmap(image, SystemMonitorWidgetRendering.text(
            context, text, size, color, style, weight, availableWidth.toFloat(), lines
        ))
        views.setContentDescription(image, text)
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
