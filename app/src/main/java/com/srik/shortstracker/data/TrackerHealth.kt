package com.srik.shortstracker.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * What the tracker reports about itself, so the app can show it's alive and how little it costs.
 * Written by the service in small batches, never on every event.
 */
class TrackerHealth private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("health", Context.MODE_PRIVATE)

    fun markConnected(at: Long) = prefs.edit { putLong(KEY_CONNECTED, at) }

    @Synchronized
    fun record(activeAt: Long, cpuNanos: Long) {
        val today = LocalDate.now().toEpochDay()
        val soFar = if (prefs.getLong(KEY_DAY, -1) == today) prefs.getLong(KEY_CPU, 0) else 0
        prefs.edit {
            putLong(KEY_ACTIVE, activeAt)
            putLong(KEY_DAY, today)
            putLong(KEY_CPU, soFar + cpuNanos)
        }
    }

    /** When the tracker last started (e.g. after a reboot or being switched on). */
    fun connectedAt(): Long = prefs.getLong(KEY_CONNECTED, 0)

    /** Last time YouTube or Instagram was on screen while the tracker was running. */
    fun lastActiveAt(): Long = prefs.getLong(KEY_ACTIVE, 0)

    /** Processor time the tracker used today. */
    fun cpuMsToday(): Long =
        if (prefs.getLong(KEY_DAY, -1) == LocalDate.now().toEpochDay()) prefs.getLong(KEY_CPU, 0) / 1_000_000 else 0

    companion object {
        private const val KEY_CONNECTED = "connected_at"
        private const val KEY_ACTIVE = "active_at"
        private const val KEY_DAY = "cpu_day"
        private const val KEY_CPU = "cpu_nanos"

        @Volatile private var instance: TrackerHealth? = null

        fun get(context: Context): TrackerHealth = instance ?: synchronized(this) {
            instance ?: TrackerHealth(context.applicationContext).also { instance = it }
        }
    }
}
