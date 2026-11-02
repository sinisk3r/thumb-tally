package com.srik.shortstracker.tracker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.srik.shortstracker.BuildConfig
import com.srik.shortstracker.data.AppDatabase
import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.TrackerConfig
import com.srik.shortstracker.data.TrackerHealth
import com.srik.shortstracker.data.TrackerSettings
import com.srik.shortstracker.data.ViewDao
import com.srik.shortstracker.data.ViewEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Watches YouTube and Instagram through Android's accessibility API.
 *
 * The system only wakes this service for those two apps (see accessibility_service_config.xml). While one
 * of them is on screen it reads the view tree after swipes and about once a second otherwise. [ScreenParser]
 * turns that into "which video is playing"; a different video means the previous one gets its watch time
 * and a new row is saved. A cheap two-second check notices when you leave, and turning the screen off
 * closes the video too.
 */
class ShortsAccessibilityService : AccessibilityService() {

    private data class Watching(
        val app: ShortsApp, val kind: ViewKind, val row: Deferred<Long>, val key: String,
        val creator: String?, val caption: String?, val startedAt: Long,
    )
    private data class Recent(val row: Deferred<Long>, val kind: ViewKind, val key: String, val at: Long)
    private data class Candidate(val key: String, val since: Long)

    private val handler = Handler(Looper.getMainLooper())
    // One thread, so a row is always inserted before its duration/screenshot updates run.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private lateinit var dao: ViewDao
    private lateinit var settings: TrackerSettings
    private lateinit var health: TrackerHealth
    private lateinit var screenshots: ScreenshotStore
    private lateinit var overlay: CounterOverlay

    private var watching: Watching? = null
    /** Videos closed in the last few minutes, so going back to one isn't counted again. */
    private val recent = ArrayDeque<Recent>()
    /** A feed video waiting to stay on screen long enough to count. */
    private var candidate: Candidate? = null
    private var pendingApp: ShortsApp? = null
    private var inspectScheduled = false
    private var viewsSinceScreenshot = 0
    private var lastDumpAt = 0L

    // For the pop-up counter and nudges.
    private var sessionCount = 0
    private var lastShortEndedAt = 0L
    private var todayCount = 0
    private var yesterdayCount = 0
    private var todayDate: LocalDate = LocalDate.MIN
    private var quickSkips = 0
    private var previewTaps = 0
    private var lastPreviewAt = 0L

    // For the "battery" numbers in the app.
    private var cpuNanos = 0L
    private var lastActiveAt = 0L
    private var lastFlushAt = 0L
    private var firstMissAt = 0L

    private val inspectRunnable = Runnable {
        inspectScheduled = false
        measured { inspect() }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() = measured { checkStillWatching() }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = leave()
    }

    override fun onServiceConnected() {
        running = true
        instance = this
        dao = AppDatabase.get(this).views()
        settings = TrackerSettings.get(this)
        health = TrackerHealth.get(this)
        screenshots = ScreenshotStore(this)
        overlay = CounterOverlay(this)
        health.markConnected(System.currentTimeMillis())
        TrackingGuard.onTrackingOn(this)
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        io.launch { screenshots.prune(dao, settings.state.value.retentionDays) }
        loadDayCounts()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = measured {
        val pkg = event.packageName?.toString()
        PaymentApps.labelFor(pkg)?.let { payment ->
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && settings.state.value.pauseForPayments) {
                pauseFor(payment)
            }
            return@measured
        }
        val app = ShortsApp.fromPackage(pkg) ?: return@measured
        pendingApp = app
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "event ${AccessibilityEvent.eventTypeToString(event.eventType)} src=${event.source?.viewIdResourceName}")
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isFullScreen(event.source)) {
            // A swipe: wait for the next video to settle, then look.
            scheduleInspect(SETTLE_MS)
        } else if (!inspectScheduled) {
            // Playing videos fire content changes constantly; look at most about once a second.
            scheduleInspect(CONTENT_CHECK_MS)
        }
    }

    /**
     * Many UPI and bank apps refuse to work while any third-party accessibility service is on, and they check
     * the list of switched-on services, so pausing inside the service isn't enough: it has to switch itself
     * off. Android doesn't let it switch itself back on, so the notification takes the person to settings.
     */
    private fun pauseFor(paymentApp: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "pausing for $paymentApp")
        leave()
        TrackingGuard.notifyPaused(this, paymentApp)
        disableSelf()
    }

    private fun scheduleInspect(delayMs: Long) {
        handler.removeCallbacks(inspectRunnable)
        handler.postDelayed(inspectRunnable, delayMs)
        inspectScheduled = true
    }

    private fun inspect() {
        val app = pendingApp ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != app.packageName) return

        val snap = root.snapshot(intArrayOf(MAX_NODES), 0)
        val info = ScreenParser.parse(app, snap, resources.displayMetrics.heightPixels)
        val config = settings.state.value
        val now = System.currentTimeMillis()
        lastActiveAt = now
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "inspect -> $info")
            File(filesDir, "trace").apply { mkdirs() }.resolve("$now.txt").writeText("$info\n\n${snap.dump()}")
        }
        if (config.debugDumps) saveDump(app, snap, info)
        if (now - lastFlushAt > FLUSH_EVERY_MS) flushHealth(now)

        val kind = info.kind?.takeIf { it == ViewKind.SHORT || config.trackLongVideos }
        if (kind == null) {
            leave(now)
            return
        }
        val current = watching
        val key = info.key
        if (key == null) {
            // The player is covered (comments, description, full screen...): whatever was playing still is.
            if (current != null && (current.app != app || current.kind != kind)) stop(now)
            return
        }
        if (current != null && current.key == key) {
            candidate = null
            return
        }
        if (current != null && isLateTitle(current, info, now)) {
            // Same video, its title just finished loading.
            watching = current.copy(key = key, caption = info.caption)
            io.launch { dao.updateDetails(current.row.await(), info.creator, info.caption) }
            return
        }

        var startedAt = now
        if (info.settleMs > 0) {
            // Feed videos autoplay as you scroll past; only count one that stays on screen.
            val waiting = candidate?.takeIf { it.key == key }
            if (waiting == null) {
                stop(now)
                candidate = Candidate(key, now)
                scheduleInspect(info.settleMs + 100)
                return
            }
            if (now - waiting.since < info.settleMs) {
                scheduleInspect(info.settleMs - (now - waiting.since) + 100)
                return
            }
            startedAt = waiting.since
        }
        candidate = null
        stop(startedAt)
        start(app, kind, info, key, startedAt, config)
    }

    private fun isLateTitle(current: Watching, info: ScreenInfo, now: Long) =
        current.caption == null && info.caption != null && current.creator == info.creator &&
            now - current.startedAt < LATE_DETAILS_MS

    private fun start(app: ShortsApp, kind: ViewKind, info: ScreenInfo, key: String, now: Long, config: TrackerConfig) {
        val back = recent.lastOrNull { it.key == key && it.kind == kind && now - it.at < RESUME_MS }
        if (back != null) {
            // Swiped back to it, or back from a profile or search: keep counting it, not a new view.
            recent.remove(back)
            watching = Watching(app, kind, back.row, key, info.creator, info.caption, now)
        } else {
            val row = io.async {
                dao.insert(
                    ViewEvent(
                        app = app.packageName, startedAt = now, creator = info.creator, caption = info.caption,
                        kind = kind.key, videoKey = key,
                    ),
                )
            }
            watching = Watching(app, kind, row, key, info.creator, info.caption, now)
            if (kind == ViewKind.SHORT) countShort(app, now, config)
            if (config.screenshotsEnabled && ++viewsSinceScreenshot >= config.screenshotEvery) {
                viewsSinceScreenshot = 0
                screenshots.capture(this) { bitmap ->
                    io.launch {
                        val id = row.await()
                        dao.setScreenshot(id, screenshots.save(id, bitmap))
                    }
                }
            }
        }
        firstMissAt = 0
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_MS)
    }

    private fun countShort(app: ShortsApp, now: Long, config: TrackerConfig) {
        val today = LocalDate.now()
        if (today != todayDate) {
            yesterdayCount = if (todayDate == today.minusDays(1)) todayCount else 0
            todayDate = today
            todayCount = 0
            lastShortEndedAt = 0
        }
        val sinceLast = lastShortEndedAt.takeIf { it > 0 }?.let { now - it }
        if (sinceLast == null || sinceLast > Stats.SESSION_GAP_MS) sessionCount = 0
        sessionCount++
        todayCount++

        val time = LocalTime.now()
        val nudge = if (!config.nudgesEnabled) null else Nudges.pick(
            NudgeContext(
                today = todayCount, session = sessionCount, limit = config.dailyLimit, yesterday = yesterdayCount,
                hour = time.hour, minute = time.minute, sinceLastMs = sinceLast, quickSkips = quickSkips,
            ),
        )
        overlay.pop(app, sessionCount, todayCount, nudge, config, showCount = config.overlayEnabled)
    }

    /** Closes the current video: it gets its watch time, and is remembered for a few minutes. */
    private fun stop(now: Long = System.currentTimeMillis()) {
        val current = watching ?: return
        watching = null
        handler.removeCallbacks(watchdogRunnable)
        val cap = if (current.kind == ViewKind.SHORT) MAX_SHORT_MS else MAX_VIDEO_MS
        val watchedMs = (now - current.startedAt).coerceIn(0, cap)
        io.launch { dao.addDuration(current.row.await(), watchedMs) }
        recent.addLast(Recent(current.row, current.kind, current.key, now))
        while (recent.size > MAX_RECENT) recent.removeFirst()
        if (current.kind == ViewKind.SHORT) {
            lastShortEndedAt = now
            quickSkips = if (watchedMs < Stats.QUICK_SKIP_MS) quickSkips + 1 else 0
        }
        flushHealth(now)
    }

    /** You left Shorts/Reels (or the app, or turned the screen off). */
    private fun leave(now: Long = System.currentTimeMillis()) {
        candidate = null
        stop(now)
        overlay.hide()
    }

    /**
     * Since only YouTube/Instagram events reach us, leaving them is silent. Every couple of seconds while a
     * video is open, check which app is in front. Two misses in a row means you left at the first miss.
     */
    private fun checkStillWatching() {
        if (watching == null) return
        val pkg = rootInActiveWindow?.packageName?.toString()
        val now = System.currentTimeMillis()
        if (ShortsApp.fromPackage(pkg) != null) {
            firstMissAt = 0
        } else if (firstMissAt == 0L) {
            firstMissAt = now
        } else {
            leave(firstMissAt)
            return
        }
        handler.postDelayed(watchdogRunnable, WATCHDOG_MS)
    }

    private fun loadDayCounts() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val yesterdayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        io.launch {
            val count = dao.countSince(todayStart, ViewKind.SHORT.key)
            val yesterday = dao.countBetween(yesterdayStart, todayStart, ViewKind.SHORT.key)
            handler.post {
                if (todayDate != today) {
                    todayDate = today
                    todayCount = count
                    yesterdayCount = yesterday
                }
            }
        }
    }

    private inline fun measured(block: () -> Unit) {
        val started = Debug.threadCpuTimeNanos()
        try {
            block()
        } finally {
            cpuNanos += Debug.threadCpuTimeNanos() - started
        }
    }

    private fun flushHealth(now: Long) {
        if (!::health.isInitialized) return
        lastFlushAt = now
        val nanos = cpuNanos
        cpuNanos = 0
        val active = lastActiveAt
        io.launch { health.record(active, nanos) }
    }

    private fun isFullScreen(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val bounds = Rect().also(node::getBoundsInScreen)
        return bounds.height() >= resources.displayMetrics.heightPixels * 0.6
    }

    private fun saveDump(app: ShortsApp, snap: NodeSnap, info: ScreenInfo) {
        val now = System.currentTimeMillis()
        if (now - lastDumpAt < DUMP_EVERY_MS) return
        lastDumpAt = now
        val text = "${app.label} at ${java.util.Date(now)}\nparsed: $info\n\n${snap.dump()}"
        io.launch {
            val dir = File(filesDir, DUMP_DIR).apply { mkdirs() }
            File(dir, "${app.name.lowercase()}.txt").writeText(text)
        }
    }

    /** Shows the pop-up over whatever is on screen, so styles and colours can be tried from Settings. */
    fun preview(withNudge: Boolean) = handler.post {
        val now = System.currentTimeMillis()
        if (now - lastPreviewAt > 30_000) previewTaps = 0
        lastPreviewAt = now
        previewTaps++
        val samples = Nudges.samples()
        val nudge = if (withNudge) samples[(previewTaps - 1) % samples.size] else null
        overlay.pop(
            ShortsApp.entries[(previewTaps - 1) % ShortsApp.entries.size], previewTaps, todayCount + previewTaps,
            nudge, settings.state.value, showCount = true,
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        instance = null
        leave()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOffReceiver) }
        // Switched off (by the person, Android, or a payment pause): start the "tracking is off" reminders.
        TrackingGuard.startChecks(this)
        super.onDestroy()
    }

    companion object {
        /** True while Android has the tracker running. Same process as the app, so the UI can read it. */
        @Volatile var running = false
            private set

        /** The running tracker, for the Settings preview. */
        @Volatile var instance: ShortsAccessibilityService? = null
            private set

        const val DUMP_DIR = "debug"
        private const val TAG = "ShortsTracker"
        private const val SETTLE_MS = 700L
        private const val CONTENT_CHECK_MS = 1_200L
        private const val WATCHDOG_MS = 2_000L
        private const val LATE_DETAILS_MS = 4_000L
        /** Going back to a video within this long continues it rather than counting it again. */
        private const val RESUME_MS = 10 * 60_000L
        private const val MAX_RECENT = 40
        // A paused video left on screen shouldn't count as an hour of watching.
        private const val MAX_SHORT_MS = 5 * 60_000L
        private const val MAX_VIDEO_MS = 3 * 60 * 60_000L
        private const val FLUSH_EVERY_MS = 30_000L
        private const val DUMP_EVERY_MS = 4_000L
        private const val MAX_NODES = 800
        private const val MAX_DEPTH = 40
    }

    private fun AccessibilityNodeInfo.snapshot(budget: IntArray, depth: Int): NodeSnap {
        val bounds = Rect().also(::getBoundsInScreen)
        val kids = if (depth >= MAX_DEPTH) emptyList() else (0 until childCount).mapNotNull { i ->
            if (budget[0]-- <= 0) null else getChild(i)?.snapshot(budget, depth + 1)
        }
        return NodeSnap(
            viewId = viewIdResourceName,
            className = className?.toString(),
            text = text?.toString(),
            desc = contentDescription?.toString(),
            visible = isVisibleToUser,
            scrollable = isScrollable,
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            children = kids,
        )
    }
}
