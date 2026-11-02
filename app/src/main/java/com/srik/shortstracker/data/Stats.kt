package com.srik.shortstracker.data

import com.srik.shortstracker.tracker.ShortsApp
import com.srik.shortstracker.tracker.ViewKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class AppTotals(val views: Int = 0, val watchedMs: Long = 0)
data class DayBar(val date: LocalDate, val label: String, val perApp: Map<ShortsApp, Int>) {
    val total: Int get() = perApp.values.sum()
}
data class CreatorCount(val app: ShortsApp, val name: String, val views: Int)

/** Today at a glance. Shorts and Reels only, except [longVideos]. */
data class Summary(
    val views: Int,
    val watchedMs: Long,
    val perApp: Map<ShortsApp, AppTotals>,
    val week: List<DayBar>,
    val topCreators: List<CreatorCount>,
    /** Regular YouTube videos watched today. */
    val longVideos: AppTotals,
)

/** A run of shorts with less than [Stats.SESSION_GAP_MS] between them. */
data class Session(val startedAt: Long, val durationMs: Long, val views: Int)

/** When you watch, over the last [days] days. */
data class Patterns(
    val days: Int,
    val shorts: Int,
    /** Shorts per [weekday Monday=0][hour 0-23]. */
    val heat: List<List<Int>>,
    val hourly: List<Int>,
    val peakHour: Int?,
    val busiestDay: DayOfWeek?,
    val sessions: Int,
    val avgSessionMs: Long,
    val longestSession: Session?,
    /** Share of shorts watched between 11 PM and 5 AM. */
    val lateNightShare: Float,
    val longVideoMs: Long,
    /** Share of shorts left within [Stats.QUICK_SKIP_MS]. */
    val quickSkipShare: Float = 0f,
    /** Average time on one short. */
    val avgShortMs: Long = 0,
    val habits: List<Habit> = emptyList(),
)

/** A badge for a viewing habit, good or cheeky. */
data class Habit(val emoji: String, val title: String, val detail: String)

object Stats {
    /** Shorts watched for less than this count as skipped. */
    const val QUICK_SKIP_MS = 2_000L

    /** Shorts closer together than this count as one sitting. */
    const val SESSION_GAP_MS = 3 * 60_000L

    fun summarize(events: List<ViewEvent>, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): Summary {
        val byDay = events.groupBy { it.localDate(zone) }
        val todays = byDay[today].orEmpty()
        val todaysShorts = todays.filter { it.isShort }
        val perApp = ShortsApp.entries.associateWith { app ->
            val mine = todaysShorts.filter { it.app == app.packageName }
            AppTotals(mine.size, mine.sumOf { it.durationMs })
        }
        val week = (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val day = byDay[date].orEmpty().filter { it.isShort }
            DayBar(
                date = date,
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                perApp = ShortsApp.entries.associateWith { app -> day.count { it.app == app.packageName } },
            )
        }
        val topCreators = todaysShorts
            .mapNotNull { event -> ShortsApp.fromPackage(event.app)?.let { app -> event.creator?.let { app to it } } }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) -> CreatorCount(key.first, key.second, count) }
            .sortedWith(compareByDescending<CreatorCount> { it.views }.thenBy { it.name.lowercase() })
            .take(5)
        val longVideos = todays.filter { !it.isShort }
        return Summary(
            views = todaysShorts.size,
            watchedMs = todaysShorts.sumOf { it.durationMs },
            perApp = perApp,
            week = week,
            topCreators = topCreators,
            longVideos = AppTotals(longVideos.size, longVideos.sumOf { it.durationMs }),
        )
    }

    fun patterns(events: List<ViewEvent>, today: LocalDate, zone: ZoneId, days: Int): Patterns {
        val since = today.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val inRange = events.filter { it.startedAt >= since }
        val shorts = inRange.filter { it.isShort }.sortedBy { it.startedAt }
        val heat = List(7) { IntArray(24) }
        var lateNight = 0
        shorts.forEach { event ->
            val time = Instant.ofEpochMilli(event.startedAt).atZone(zone)
            heat[time.dayOfWeek.value - 1][time.hour]++
            if (time.hour >= 23 || time.hour < 5) lateNight++
        }
        val hourly = (0 until 24).map { hour -> heat.sumOf { it[hour] } }
        val perWeekday = heat.map { it.sum() }
        val sessions = sessions(shorts)
        return Patterns(
            days = days,
            shorts = shorts.size,
            heat = heat.map { it.toList() },
            hourly = hourly,
            peakHour = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 },
            busiestDay = perWeekday.indices.maxByOrNull { perWeekday[it] }?.takeIf { perWeekday[it] > 0 }
                ?.let { DayOfWeek.of(it + 1) },
            sessions = sessions.size,
            avgSessionMs = if (sessions.isEmpty()) 0 else sessions.sumOf { it.durationMs } / sessions.size,
            longestSession = sessions.maxByOrNull { it.durationMs },
            lateNightShare = if (shorts.isEmpty()) 0f else lateNight.toFloat() / shorts.size,
            longVideoMs = inRange.filter { !it.isShort }.sumOf { it.durationMs },
            quickSkipShare = quickSkipShare(shorts),
            avgShortMs = shorts.filter { it.durationMs > 0 }.map { it.durationMs }.average().takeIf { !it.isNaN() }?.toLong() ?: 0,
            habits = habits(shorts, sessions, zone),
        )
    }

    private fun quickSkipShare(shorts: List<ViewEvent>): Float {
        // Rows still being watched have no time yet; leave them out.
        val timed = shorts.filter { it.durationMs > 0 }
        return if (timed.isEmpty()) 0f else timed.count { it.durationMs < QUICK_SKIP_MS }.toFloat() / timed.size
    }

    /** Badges for how you watch. Each needs enough data to mean something. */
    fun habits(shorts: List<ViewEvent>, sessions: List<Session>, zone: ZoneId): List<Habit> = buildList {
        val timed = shorts.filter { it.durationMs > 0 }
        if (timed.size >= 20) {
            val skip = quickSkipShare(shorts)
            val avg = timed.sumOf { it.durationMs } / timed.size
            if (skip >= 0.35f) {
                add(Habit("⚡", "Speed skipper", "${(skip * 100).toInt()}% of Shorts gone in under 2 seconds. Picky, or bored?"))
            } else if (avg >= 25_000 && skip < 0.15f) {
                add(Habit("🤿", "Deep diver", "You give each Short about ${avg / 1000} seconds. You actually watch them."))
            }
        }
        if (shorts.size >= 20) {
            val late = shorts.count { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour.let { h -> h >= 23 || h < 5 } }
            if (late >= shorts.size / 4) add(Habit("🦉", "Night owl", "$late of your Shorts were after 11 PM."))
            val top = shorts.mapNotNull { it.creator }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (top != null && top.value >= shorts.size * 0.15) {
                add(Habit("💛", "Loyal fan", "${top.value} from ${top.key}. Maybe just subscribe?"))
            }
        }
        sessions.maxByOrNull { it.views }?.takeIf { it.views >= 50 }?.let {
            add(Habit("🍿", "Binge mode", "${it.views} Shorts in one sitting."))
        }
        shorts.mapNotNull { it.videoKey }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= 3 }
            ?.let { (key, times) ->
                val who = key.split('|').getOrNull(1)?.takeIf { it.isNotBlank() }
                add(Habit("🔁", "On repeat", "One video ${times} times${who?.let { " (from $it)" } ?: ""}. It must be good."))
            }
    }

    /** Groups shorts (sorted oldest first) into sittings. */
    fun sessions(shorts: List<ViewEvent>, gapMs: Long = SESSION_GAP_MS): List<Session> {
        val result = mutableListOf<Session>()
        var start = 0L
        var end = 0L
        var count = 0
        for (event in shorts) {
            val eventEnd = event.startedAt + event.durationMs
            if (count > 0 && event.startedAt - end <= gapMs) {
                end = maxOf(end, eventEnd)
                count++
            } else {
                if (count > 0) result += Session(start, end - start, count)
                start = event.startedAt
                end = eventEnd
                count = 1
            }
        }
        if (count > 0) result += Session(start, end - start, count)
        return result
    }

    /** "<1 min", "12 min", "1 h 5 min". */
    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes < 1 -> "<1 min"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    /** "0:23", "2:05" or "1:02:05" for a single video. */
    fun formatClip(ms: Long): String {
        val seconds = ms / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** "10 PM", "12 AM". */
    fun formatHour(hour: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "$h ${if (hour < 12) "AM" else "PM"}"
    }

    private val ViewEvent.isShort get() = kind == ViewKind.SHORT.key
    private fun ViewEvent.localDate(zone: ZoneId) = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
}
