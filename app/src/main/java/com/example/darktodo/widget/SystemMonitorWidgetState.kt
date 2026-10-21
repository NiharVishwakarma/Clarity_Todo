package com.example.darktodo.widget

internal object SystemMonitorWidgetState {
    const val COMPACT_MIN_HEIGHT_DP = 220
    const val STALE_AFTER_MS = 60 * 60 * 1000L

    fun showDetails(height: Int, fontScale: Float) =
        height >= (240 * fontScale.coerceAtLeast(1f)).toInt()

    fun essentialOnly(height: Int, fontScale: Float) =
        height < (180 * fontScale.coerceAtLeast(1f)).toInt()

    fun value(values: Map<String, String>, key: String): String =
        values[key]?.takeIf { it.isNotBlank() } ?: "Unavailable"

    fun stale(capturedAt: Long?, failedAt: Long, now: Long): Boolean =
        capturedAt == null || capturedAt <= 0 || failedAt >= capturedAt ||
            now < capturedAt || now - capturedAt >= STALE_AFTER_MS

    fun status(capturedAt: Long?, failedAt: Long, now: Long, formattedTime: String): String {
        if (capturedAt == null || capturedAt <= 0) {
            return if (failedAt > 0) "Refresh failed · No successful update" else "Not updated · Tap Refresh"
        }
        val prefix = if (failedAt >= capturedAt) "Refresh failed · Stale"
            else if (stale(capturedAt, failedAt, now)) "Stale" else "Updated"
        return "$prefix · $formattedTime"
    }
}
