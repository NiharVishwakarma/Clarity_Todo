package com.example.darktodo.monitor

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.Process
import android.os.storage.StorageManager
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

object MonitorRepository {
    private val refreshLock = Mutex()
    private val diskLock = Any()

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "monitor-snapshot.json"))

    internal fun <T> withRefreshPaused(block: () -> T): T = runBlocking {
        refreshLock.withLock { block() }
    }

    internal fun backupBytes(context: Context): ByteArray? = synchronized(diskLock) {
        val snapshot = file(context)
        if (!snapshot.baseFile.exists() && !File(snapshot.baseFile.path + ".bak").exists()) null
        else snapshot.readFully()
    }

    internal fun restoreBytes(context: Context, bytes: ByteArray?) = synchronized(diskLock) {
        val snapshot = file(context)
        if (bytes == null) snapshot.delete() else {
            val output = snapshot.startWrite()
            try {
                val restored = JSONObject(bytes.toString(Charsets.UTF_8)).put("restoredFromBackup", true)
                output.write(restored.toString().toByteArray(Charsets.UTF_8))
                snapshot.finishWrite(output)
            } catch (error: Exception) {
                snapshot.failWrite(output)
                throw error
            }
        }
    }

    fun last(context: Context): MonitorSnapshot? = synchronized(diskLock) {
        try {
            val json = file(context).openRead().bufferedReader().use { JSONObject(it.readText()) }
            val values = json.getJSONObject("values")
            MonitorSnapshot(json.getLong("capturedAt"), values.keys().asSequence().associateWith { values.getString(it) },
                json.optBoolean("restoredFromBackup", false))
        } catch (_: Exception) { null }
    }

    suspend fun refresh(context: Context): MonitorSnapshot = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val snapshot = collect(context.applicationContext)
            currentCoroutineContext().ensureActive()
            synchronized(diskLock) {
                val atomic = file(context)
                val stream = atomic.startWrite()
                try {
                    val json = JSONObject().put("capturedAt", snapshot.capturedAt)
                        .put("values", JSONObject(snapshot.values))
                    stream.write(json.toString().toByteArray(Charsets.UTF_8))
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
                }
            }
            snapshot
        }
    }

    private fun collect(context: Context): MonitorSnapshot {
        val values = linkedMapOf<String, String>()
        fun value(key: String, block: () -> String?) {
            values[key] = try { block()?.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: UNAVAILABLE } catch (_: Exception) { UNAVAILABLE }
        }
        value("Manufacturer") { Build.MANUFACTURER }
        value("Brand") { Build.BRAND }
        value("Model") { Build.MODEL }
        value("Device") { Build.DEVICE }
        value("Hardware") { Build.HARDWARE }
        value("Supported ABIs") { Build.SUPPORTED_ABIS.joinToString() }
        value("Available processor cores") { Runtime.getRuntime().availableProcessors().toString() }
        value("Display pixels (app resource metrics)") {
            context.resources.displayMetrics.let { "${it.widthPixels} × ${it.heightPixels}" }
        }
        value("Display density") { "${context.resources.displayMetrics.densityDpi} dpi" }
        value("Android") { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
        value("Build") { Build.DISPLAY }
        value("Build fingerprint") { Build.FINGERPRINT }
        value("Security patch") { Build.VERSION.SECURITY_PATCH }
        value("Device lock configured") {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure?.let {
                if (it) "Yes — PIN, password or pattern configured" else "No secure device lock configured"
            }
        }
        value("Storage encryption status") {
            val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            when (policy?.storageEncryptionStatus) {
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "Active"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "Active (per-user keys)"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "Active (default key)"
                DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "Inactive"
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING -> "Activating"
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "Unsupported"
                else -> null
            }
        }
        value("Android base release") { Build.VERSION.BASE_OS }
        value("Bootloader") { Build.BOOTLOADER.takeUnless { it == Build.UNKNOWN } }
        value("OS build time (not install date)") {
            Build.TIME.takeIf { it > 0 }?.let(MonitorFormats::timestamp)
        }
        value("OS install date") { "Unavailable — Android does not expose a reliable public API" }
        value("OS update availability") { "Unavailable — check system settings" }
        value("Uptime including sleep") { "${SystemClock.elapsedRealtime() / 1000 / 60} minutes" }
        val memory = try {
            ActivityManager.MemoryInfo().also {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
            }
        } catch (_: Exception) { null }
        value("Total RAM") { MonitorFormats.bytes(memory?.totalMem) }
        value("Available RAM") { MonitorFormats.bytes(memory?.availMem) }
        value("Used RAM") { MonitorFormats.bytes(memory?.let { MonitorFormats.used(it.totalMem, it.availMem) }) }
        value("RAM utilization") { memory?.let {
            MonitorFormats.used(it.totalMem, it.availMem)?.let { used -> MonitorFormats.percent(used, it.totalMem) }
        } }
        value("Low RAM condition") { memory?.lowMemory?.let { if (it) "Yes" else "No" } }
        value("Low RAM threshold") { MonitorFormats.bytes(memory?.threshold) }
        value("RAM accounting") { "System-wide total minus available; includes reclaimable/cache memory, not just apps" }
        fun storage(prefix: String, path: File?) {
            val stat = try { path?.let { StatFs(it.path) } } catch (_: Exception) { null }
            value("Total $prefix storage") { MonitorFormats.bytes(stat?.totalBytes) }
            value("Available $prefix storage") { MonitorFormats.bytes(stat?.availableBytes) }
            value("Free $prefix storage (including reserved)") { MonitorFormats.bytes(stat?.freeBytes) }
            value("Used $prefix storage") { MonitorFormats.bytes(stat?.let { MonitorFormats.used(it.totalBytes, it.availableBytes) }) }
            value("$prefix storage utilization") { stat?.let {
                MonitorFormats.used(it.totalBytes, it.availableBytes)?.let { used -> MonitorFormats.percent(used, it.totalBytes) }
            } }
        }
        storage("internal", context.filesDir)
        storage("app external", context.getExternalFilesDir(null)?.takeIf {
            Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        })
        value("Storage accounting") { "Used = filesystem total minus bytes available to apps, including reserved space. Free includes reserved bytes unavailable to normal apps. App external may share internal storage; do not add totals." }
        var ownStorageStatus = "Available — Android statistics for this app's own UID; no Usage Access requested"
        val ownStorage = try {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                ?: throw UnsupportedOperationException()
            val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                ?: throw UnsupportedOperationException()
            stats.queryStatsForUid(manager.getUuidForPath(context.filesDir), Process.myUid())
        } catch (error: Exception) {
            ownStorageStatus = MonitorFormats.storageFailureStatus(error)
            null
        }
        value("Clarity app storage status") { ownStorageStatus }
        value("Clarity app storage: code") { MonitorFormats.bytes(ownStorage?.appBytes) }
        value("Clarity app storage: data (includes cache)") { MonitorFormats.bytes(ownStorage?.dataBytes) }
        value("Clarity app storage: cache (subset of data)") { MonitorFormats.bytes(ownStorage?.cacheBytes) }
        value("Clarity app storage source") { "Android StorageStatsManager for this app's own UID and data volume; no Usage Access needed. Code plus data is the app total; do not add cache again. Values are system estimates and may update asynchronously." }
        val battery = try { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
        fun extra(key: String) = battery?.getIntExtra(key, -1) ?: -1
        value("Battery") { MonitorFormats.batteryPercent(extra(BatteryManager.EXTRA_LEVEL), extra(BatteryManager.EXTRA_SCALE)) }
        value("Charging") {
            when (extra(BatteryManager.EXTRA_STATUS)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> null
            }
        }
        value("Battery power source") {
            val source = extra(BatteryManager.EXTRA_PLUGGED)
            when {
                source < 0 -> null
                source == 0 -> "Battery"
                else -> buildList {
                    if (source and BatteryManager.BATTERY_PLUGGED_AC != 0) add("AC")
                    if (source and BatteryManager.BATTERY_PLUGGED_USB != 0) add("USB")
                    if (source and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) add("Wireless")
                    if (Build.VERSION.SDK_INT >= 33 && source and BatteryManager.BATTERY_PLUGGED_DOCK != 0) add("Dock")
                }.joinToString().ifEmpty { UNAVAILABLE }
            }
        }
        value("Battery health") {
            when (extra(BatteryManager.EXTRA_HEALTH)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> null
            }
        }
        value("Battery temperature") {
            MonitorFormats.batteryTemperature(
                if (battery?.hasExtra(BatteryManager.EXTRA_TEMPERATURE) == true)
                    battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) else null
            )
        }
        value("Battery voltage") { extra(BatteryManager.EXTRA_VOLTAGE).takeIf { it > 0 }?.let { "$it mV" } }
        value("Battery technology") { battery?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) }
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        value("Battery remaining capacity (property)") {
            manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }?.let { "$it%" }
        }
        value("Battery charge counter") {
            manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?.takeIf { it != Long.MIN_VALUE && it in 1..Int.MAX_VALUE.toLong() }?.let { "$it µAh" }
        }
        value("Battery current (raw, signed)") {
            manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?.takeIf { it != Long.MIN_VALUE && it != Int.MIN_VALUE.toLong() }?.let { "$it µA (positive into battery)" }
        }
        value("Battery data source") { "Android sticky ACTION_BATTERY_CHANGED and BatteryManager; values depend on device reporting" }
        value("Battery wear / design capacity") { "Unavailable — no reliable public API across devices" }
        value("Per-app battery ranking") { "Unavailable to ordinary apps — use Android Battery settings" }
        return MonitorSnapshot(System.currentTimeMillis(), values.toMap())
    }
}
