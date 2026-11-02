package com.srik.shortstracker.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the floating counter shows while you're in Shorts or Reels. */
enum class OverlayMode(val label: String) {
    SESSION("This session"),
    TODAY("Today"),
    BOTH("Both"),
}

/** How the counter arrives and leaves. */
enum class OverlayStyle(val label: String) {
    /** Grows out of the punch-hole camera and shrinks back into it. Always top centre. */
    CAMERA("From the camera"),
    POP("Pop"),
    DROP("Drop"),
    SLIDE("Slide"),
    GENTLE("Gentle"),
}

/** The pop-up's colour. [APP] uses each app's own colour (YouTube red, Instagram pink, Snapchat yellow). */
enum class OverlayColor(val label: String, val argb: Int) {
    MIDNIGHT("Midnight", 0xEB141219.toInt()),
    VIOLET("Violet", 0xF05B4BD6.toInt()),
    OCEAN("Ocean", 0xF01E6FD9.toInt()),
    MINT("Mint", 0xF0158F6B.toInt()),
    APP("App colour", 0),
}

/** Where the counter pops up. */
enum class OverlaySpot(val label: String) {
    TOP_RIGHT("Top right"),
    TOP("Top"),
    BOTTOM("Bottom"),
}

data class TrackerConfig(
    val screenshotsEnabled: Boolean = false,
    /** Take a screenshot of every Nth video. */
    val screenshotEvery: Int = 1,
    /** Screenshots older than this are deleted; the view rows themselves are kept. */
    val retentionDays: Int = 30,
    /** 0 means no limit. */
    val dailyLimit: Int = 0,
    /** Save what the tracker "sees" so detection can be tuned when the apps change their layout. */
    val debugDumps: Boolean = false,
    val overlayEnabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.BOTH,
    val overlaySpot: OverlaySpot = OverlaySpot.TOP_RIGHT,
    val overlayStyle: OverlayStyle = OverlayStyle.CAMERA,
    val overlayColor: OverlayColor = OverlayColor.MIDNIGHT,
    /** Cheeky messages at milestones, late at night and around the daily limit. */
    val nudgesEnabled: Boolean = true,
    /** Also track regular (long-form) YouTube videos, separately from Shorts. */
    val trackLongVideos: Boolean = true,
    /** Switch tracking off by itself when a payment or bank app opens, since many refuse to work otherwise. */
    val pauseForPayments: Boolean = true,
    /** While tracking is off, a notification every so often (or when a watched app opens) to turn it back on. */
    val remindWhenOff: Boolean = true,
    /** Agreed to the "what this app reads" screen; until then every "turn on" button shows it first. */
    val disclosureAccepted: Boolean = false,
)

/** Shared by the app UI and the accessibility service; both run in the same process. */
class TrackerSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TrackerConfig> = _state

    @Synchronized
    fun update(change: (TrackerConfig) -> TrackerConfig) {
        val next = change(_state.value)
        prefs.edit {
            putBoolean(KEY_SHOTS, next.screenshotsEnabled)
            putInt(KEY_SHOT_EVERY, next.screenshotEvery)
            putInt(KEY_RETENTION, next.retentionDays)
            putInt(KEY_LIMIT, next.dailyLimit)
            putBoolean(KEY_DUMPS, next.debugDumps)
            putBoolean(KEY_OVERLAY, next.overlayEnabled)
            putString(KEY_OVERLAY_MODE, next.overlayMode.name)
            putString(KEY_OVERLAY_SPOT, next.overlaySpot.name)
            putString(KEY_OVERLAY_STYLE, next.overlayStyle.name)
            putString(KEY_OVERLAY_COLOR, next.overlayColor.name)
            putBoolean(KEY_NUDGES, next.nudgesEnabled)
            putBoolean(KEY_LONG_VIDEOS, next.trackLongVideos)
            putBoolean(KEY_PAUSE_PAYMENTS, next.pauseForPayments)
            putBoolean(KEY_REMIND_OFF, next.remindWhenOff)
            putBoolean(KEY_DISCLOSURE, next.disclosureAccepted)
        }
        _state.value = next
    }

    private fun load(): TrackerConfig {
        val defaults = TrackerConfig()
        return TrackerConfig(
            screenshotsEnabled = prefs.getBoolean(KEY_SHOTS, defaults.screenshotsEnabled),
            screenshotEvery = prefs.getInt(KEY_SHOT_EVERY, defaults.screenshotEvery),
            retentionDays = prefs.getInt(KEY_RETENTION, defaults.retentionDays),
            dailyLimit = prefs.getInt(KEY_LIMIT, defaults.dailyLimit),
            debugDumps = prefs.getBoolean(KEY_DUMPS, defaults.debugDumps),
            overlayEnabled = prefs.getBoolean(KEY_OVERLAY, defaults.overlayEnabled),
            overlayMode = OverlayMode.entries.firstOrNull { it.name == prefs.getString(KEY_OVERLAY_MODE, null) }
                ?: defaults.overlayMode,
            overlaySpot = OverlaySpot.entries.firstOrNull { it.name == prefs.getString(KEY_OVERLAY_SPOT, null) }
                ?: defaults.overlaySpot,
            overlayStyle = OverlayStyle.entries.firstOrNull { it.name == prefs.getString(KEY_OVERLAY_STYLE, null) }
                ?: defaults.overlayStyle,
            overlayColor = OverlayColor.entries.firstOrNull { it.name == prefs.getString(KEY_OVERLAY_COLOR, null) }
                ?: defaults.overlayColor,
            nudgesEnabled = prefs.getBoolean(KEY_NUDGES, defaults.nudgesEnabled),
            trackLongVideos = prefs.getBoolean(KEY_LONG_VIDEOS, defaults.trackLongVideos),
            pauseForPayments = prefs.getBoolean(KEY_PAUSE_PAYMENTS, defaults.pauseForPayments),
            remindWhenOff = prefs.getBoolean(KEY_REMIND_OFF, defaults.remindWhenOff),
            disclosureAccepted = prefs.getBoolean(KEY_DISCLOSURE, defaults.disclosureAccepted),
        )
    }

    companion object {
        private const val KEY_SHOTS = "screenshots_enabled"
        private const val KEY_SHOT_EVERY = "screenshot_every"
        private const val KEY_RETENTION = "retention_days"
        private const val KEY_LIMIT = "daily_limit"
        private const val KEY_DUMPS = "debug_dumps"
        private const val KEY_OVERLAY = "overlay_enabled"
        private const val KEY_OVERLAY_MODE = "overlay_mode"
        private const val KEY_OVERLAY_SPOT = "overlay_spot"
        private const val KEY_OVERLAY_STYLE = "overlay_style"
        private const val KEY_OVERLAY_COLOR = "overlay_color"
        private const val KEY_NUDGES = "nudges_enabled"
        private const val KEY_LONG_VIDEOS = "track_long_videos"
        private const val KEY_PAUSE_PAYMENTS = "pause_for_payments"
        private const val KEY_REMIND_OFF = "remind_when_off"
        private const val KEY_DISCLOSURE = "disclosure_accepted"

        @Volatile private var instance: TrackerSettings? = null

        fun get(context: Context): TrackerSettings = instance ?: synchronized(this) {
            instance ?: TrackerSettings(context.applicationContext).also { instance = it }
        }
    }
}
