package com.example.darktodo.data

/** Pure dialog policy so countdown edit behavior can be regression-tested without Android. */
object CountdownEditPolicy {
    fun displayedMinutes(remainingMillis: Long): Long =
        (((remainingMillis.coerceAtLeast(0L) + 59_999L) / 60_000L).coerceAtLeast(1L))

    fun shouldConfigure(reminderWasEdited: Boolean, isExpiredCountdown: Boolean): Boolean =
        reminderWasEdited || isExpiredCountdown
}
