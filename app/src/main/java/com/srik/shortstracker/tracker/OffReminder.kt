package com.srik.shortstracker.tracker

/**
 * Pure decision for the reminder check that runs every few minutes, and only while tracking is off.
 * The "paused" notification is ongoing, so it stays in the shade anyway; this only decides when to alert again.
 * (0.4 also used Usage access to remind only when a watched app opened; removed in 0.4.1 to drop a permission.)
 */
object OffReminder {
    /** At most one alert this often. */
    const val GAP_MS = 3 * 60 * 60_000L
    /** Right after a payment pause, give the payment time to finish before nagging. */
    const val AFTER_PAUSE_MS = 3 * 60_000L

    fun shouldRemind(now: Long, trackingOn: Boolean, remindersOn: Boolean, lastReminderAt: Long, pausedAt: Long): Boolean =
        !trackingOn && remindersOn && now - pausedAt >= AFTER_PAUSE_MS && now - lastReminderAt >= GAP_MS
}
