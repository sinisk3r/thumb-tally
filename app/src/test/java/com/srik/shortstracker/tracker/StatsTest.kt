package com.srik.shortstracker.tracker

import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.ViewEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class StatsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 21) // a Wednesday
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).toInstant(zone).toEpochMilli()
    private fun view(
        date: LocalDate, app: ShortsApp, creator: String?, ms: Long = 30_000, hour: Int = 12, minute: Int = 0,
        kind: ViewKind = ViewKind.SHORT,
    ) = ViewEvent(app = app.packageName, startedAt = at(date, hour, minute), durationMs = ms, creator = creator, kind = kind.key)

    @Test
    fun `summarizes today, week and top creators`() {
        val events = listOf(
            view(today, ShortsApp.YOUTUBE, "@a"),
            view(today, ShortsApp.YOUTUBE, "@a"),
            view(today, ShortsApp.INSTAGRAM, "b", ms = 60_000),
            view(today, ShortsApp.INSTAGRAM, null),
            view(today, ShortsApp.YOUTUBE, "Long channel", ms = 600_000, kind = ViewKind.VIDEO),
            view(today.minusDays(1), ShortsApp.YOUTUBE, "@old"),
            view(today.minusDays(9), ShortsApp.YOUTUBE, "@ancient"),
        )
        val s = Stats.summarize(events, today, zone, Locale.US)

        assertEquals(4, s.views)
        assertEquals(150_000L, s.watchedMs)
        assertEquals(2, s.perApp.getValue(ShortsApp.YOUTUBE).views)
        assertEquals(90_000L, s.perApp.getValue(ShortsApp.INSTAGRAM).watchedMs)
        assertEquals(1, s.longVideos.views)
        assertEquals(600_000L, s.longVideos.watchedMs)

        assertEquals(7, s.week.size)
        assertEquals(today, s.week.last().date)
        assertEquals("Wed", s.week.last().label)
        assertEquals(4, s.week.last().total)
        assertEquals(1, s.week[5].total)

        assertEquals(listOf("@a" to 2, "b" to 1), s.topCreators.map { it.name to it.views })
    }

    @Test
    fun `groups shorts into sittings`() {
        val shorts = listOf(
            view(today, ShortsApp.YOUTUBE, "@a", ms = 30_000, hour = 22, minute = 0),
            view(today, ShortsApp.YOUTUBE, "@b", ms = 30_000, hour = 22, minute = 2), // 1.5 min gap: same sitting
            view(today, ShortsApp.YOUTUBE, "@c", ms = 30_000, hour = 22, minute = 10), // 7.5 min gap: new sitting
        )
        val sessions = Stats.sessions(shorts)
        assertEquals(listOf(2, 1), sessions.map { it.views })
        assertEquals(150_000L, sessions[0].durationMs)
    }

    @Test
    fun `finds peak hour, busiest day and late-night share`() {
        val events = listOf(
            view(today, ShortsApp.YOUTUBE, "@a", hour = 23),
            view(today, ShortsApp.YOUTUBE, "@a", hour = 23, minute = 1),
            view(today, ShortsApp.INSTAGRAM, "b", hour = 9),
            view(today.minusDays(1), ShortsApp.YOUTUBE, "@a", hour = 23),
            view(today.minusDays(20), ShortsApp.YOUTUBE, "@a", hour = 8), // outside 7 days
            view(today, ShortsApp.YOUTUBE, "Long", ms = 1_000_000, hour = 8, kind = ViewKind.VIDEO),
        )
        val p = Stats.patterns(events, today, zone, days = 7)
        assertEquals(4, p.shorts)
        assertEquals(23, p.peakHour)
        assertEquals(DayOfWeek.WEDNESDAY, p.busiestDay)
        assertEquals(0.75f, p.lateNightShare, 0.001f)
        assertEquals(3, p.heat[DayOfWeek.WEDNESDAY.value - 1].sum())
        assertEquals(1_000_000L, p.longVideoMs)
    }

    @Test
    fun `formats durations and hours`() {
        assertEquals("<1 min", Stats.formatDuration(30_000))
        assertEquals("12 min", Stats.formatDuration(12 * 60_000L))
        assertEquals("2 h", Stats.formatDuration(120 * 60_000L))
        assertEquals("1 h 5 min", Stats.formatDuration(65 * 60_000L))
        assertEquals("2:05", Stats.formatClip(125_000))
        assertEquals("1:02:05", Stats.formatClip(3_725_000))
        assertEquals("12 AM", Stats.formatHour(0))
        assertEquals("10 PM", Stats.formatHour(22))
    }

    @Test
    fun `habits spot skipping, night owls and repeats`() {
        // 30 shorts: 20 skipped within a second, all after midnight, one video three times.
        val shorts = (0 until 30).map { i ->
            view(today, ShortsApp.YOUTUBE, "@c$i", ms = if (i < 20) 900 else 20_000, hour = 0, minute = i)
                .copy(videoKey = if (i < 3) "yt-short|@fav|same one" else "k$i")
        }
        val p = Stats.patterns(shorts, today, zone, days = 7)
        val titles = p.habits.map { it.title }
        assertEquals(listOf("Speed skipper", "Night owl", "On repeat"), titles)
        assertEquals(20f / 30, p.quickSkipShare, 0.001f)
    }

    @Test
    fun `no habits from a handful of shorts`() {
        val few = (0 until 5).map { view(today, ShortsApp.YOUTUBE, "@a", ms = 500, hour = 1, minute = it) }
        assertEquals(emptyList<String>(), Stats.patterns(few, today, zone, days = 7).habits.map { it.title })
    }
}
