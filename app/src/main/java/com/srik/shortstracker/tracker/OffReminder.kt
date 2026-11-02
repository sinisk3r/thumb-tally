package com.srik.shortstracker.tracker

/** Why the "tracking is off" notification is shown. */
sealed interface Reminder {
    /** A watched app was opened while tracking was off (needs Usage access to know). */
    data class Missed(val app: ShortsApp) : Reminder
    /** Tracking is off and we can't see which apps are open, so a plain, rarer reminder. */
    data object Off : Reminder
}

/**
 * Pure decision for the reminder check that runs every few minutes, and only while tracking is off.
 * With Usage access it speaks up only when YouTube, Instagram or Snapchat was actually opened; without it,
 * it falls back to a plain reminder at most once every [BLIND_GAP_MS].
 */
object OffReminder {
    /** At most one "you opened X" reminder this often, so a long scroll session isn't a stream of pings. */
    const val MISSED_GAP_MS = 20 * 60_000L
    const val BLIND_GAP_MS = 3 * 60 * 60_000L
    /** Right after a payment pause, give the payment time to finish before nagging. */
    const val AFTER_PAUSE_MS = 3 * 60_000L

    fun decide(
        now: Long,
        trackingOn: Boolean,
        remindersOn: Boolean,
        lastReminderAt: Long,
        pausedAt: Long,
        hasUsageAccess: Boolean,
        /** The watched app most recently brought to the front since the last check, if any. */
        openedSinceLastCheck: ShortsApp?,
    ): Reminder? {
        if (trackingOn || !remindersOn) return null
        if (now - pausedAt < AFTER_PAUSE_MS) return null
        val since = now - lastReminderAt
        return if (hasUsageAccess) {
            openedSinceLastCheck?.takeIf { since >= MISSED_GAP_MS }?.let(Reminder::Missed)
        } else {
            Reminder.Off.takeIf { since >= BLIND_GAP_MS }
        }
    }
}
