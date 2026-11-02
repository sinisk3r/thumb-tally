package com.srik.shortstracker.ui

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.srik.shortstracker.data.AppDatabase
import com.srik.shortstracker.data.Patterns
import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.Summary
import com.srik.shortstracker.data.TrackerConfig
import com.srik.shortstracker.data.TrackerHealth
import com.srik.shortstracker.data.TrackerSettings
import com.srik.shortstracker.data.ViewEvent
import com.srik.shortstracker.tracker.ScreenshotStore
import com.srik.shortstracker.tracker.ShortsAccessibilityService
import com.srik.shortstracker.tracker.TrackingGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).views()
    private val settings = TrackerSettings.get(app)
    private val trackerHealth = TrackerHealth.get(app)
    private val screenshots = ScreenshotStore(app)
    private val zone = ZoneId.systemDefault()
    // Refreshed while the app is open, so it rolls over to the new day at midnight.
    private val today = MutableStateFlow(LocalDate.now())
    private val _health = MutableStateFlow(Health())
    private val _patternDays = MutableStateFlow(7)

    val health: StateFlow<Health> = _health
    val config: StateFlow<TrackerConfig> = settings.state
    val patternDays: StateFlow<Int> = _patternDays

    val events: StateFlow<List<ViewEvent>> = today
        .flatMapLatest { day -> dao.observeSince(day.minusDays(FEED_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<Summary> = combine(events, today) { events, day -> Stats.summarize(events, day, zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stats.summarize(emptyList(), LocalDate.now(), zone))

    val patterns: StateFlow<Patterns> = combine(events, today, _patternDays) { events, day, days ->
        Stats.patterns(events, day, zone, days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stats.patterns(emptyList(), LocalDate.now(), zone, 7))

    fun refresh() {
        today.value = LocalDate.now()
        val app = getApplication<Application>()
        _health.value = Health(
            enabled = TrackingGuard.isTrackingOn(app),
            running = ShortsAccessibilityService.running,
            batteryUnrestricted = app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName),
            connectedAt = trackerHealth.connectedAt(),
            lastActiveAt = trackerHealth.lastActiveAt(),
            cpuMsToday = trackerHealth.cpuMsToday(),
            usageAccess = TrackingGuard.hasUsageAccess(app),
            canNotify = TrackingGuard.canNotify(app),
        )
        // Tracking off (and not just because the app was opened for the first time): keep reminders running.
        TrackingGuard.startChecks(app)
    }

    fun setPatternDays(days: Int) {
        _patternDays.value = days
    }

    fun updateConfig(change: (TrackerConfig) -> TrackerConfig) {
        settings.update(change)
        viewModelScope.launch(Dispatchers.IO) { screenshots.prune(dao, settings.state.value.retentionDays) }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
            screenshots.deleteAll()
        }
    }

    /** The last screen dumps (one per app), or null if there are none yet. */
    fun readDumps(): String? {
        val dir = File(getApplication<Application>().filesDir, ShortsAccessibilityService.DUMP_DIR)
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) return null
        return files.joinToString("\n\n") { it.readText() }.take(MAX_DUMP_CHARS)
    }

    private companion object {
        const val FEED_DAYS = 30L
        // Share intents carry text in a binder transaction capped around 1 MB.
        const val MAX_DUMP_CHARS = 200_000
    }
}
