package com.srik.shortstracker.tracker

import com.srik.shortstracker.data.BadgeTone
import com.srik.shortstracker.data.Badges
import com.srik.shortstracker.data.Stats
import com.srik.shortstracker.data.TodayLines
import com.srik.shortstracker.data.ViewEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BadgesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 11, 2)

    private fun short(day: LocalDate, hour: Int, minute: Int, creator: String, app: ShortsApp = ShortsApp.YOUTUBE, ms: Long = 20_000) =
        ViewEvent(
            app = app.packageName,
            startedAt = day.atTime(hour, 0).plusMinutes(minute.toLong()).atZone(zone).toInstant().toEpochMilli(),
            durationMs = ms, creator = creator, caption = null, kind = ViewKind.SHORT.key, videoKey = "$creator|$minute",
        )

    private fun earned(shorts: List<ViewEvent>) =
        Badges.evaluate(shorts.sortedBy { it.startedAt }, Stats.sessions(shorts.sortedBy { it.startedAt }), zone)
            .filter { it.earned }.map { it.badge.id }.toSet()

    @Test fun `every badge is listed, locked ones show how to get them`() {
        val states = Badges.evaluate(emptyList(), emptyList(), zone)
        assertEquals(Badges.all.size, states.size)
        assertTrue(states.none { it.earned })
        states.forEach { assertEquals(it.badge.howTo, it.detail) }
        assertTrue(Badges.all.any { it.tone == BadgeTone.GOOD } && Badges.all.any { it.tone == BadgeTone.CHEEKY })
    }

    @Test fun `daytime scrolling over days with a day off earns the good ones`() {
        // Mon, Tue, Thu (Wed off): 8 shorts each at lunch, short sittings, three apps.
        val shorts = listOf(0L, 1L, 3L).flatMap { d ->
            (0 until 8).map { i ->
                val app = ShortsApp.entries[i % 3]
                short(monday.plusDays(d), 13, i * 10, "@c${d}_$i", app)
            }
        }
        val got = earned(shorts)
        assertTrue("daylight" in got)
        assertTrue("touched_grass" in got)
        assertTrue("triple_threat" in got)
        assertTrue("snack_sized" in got)
        assertFalse("night_owl" in got)
    }

    @Test fun `a long late sitting earns the cheeky ones`() {
        val shorts = (0 until 70).map { short(monday, 23, 0, "@same").copy(startedAt = monday.atTime(23, 0).atZone(zone).toInstant().toEpochMilli() + it * 60_000L) }
        val got = earned(shorts)
        assertTrue("night_owl" in got)
        assertTrue("binge_mode" in got)
        assertTrue("marathon" in got)
        assertTrue("loyal_fan" in got)
        assertFalse("daylight" in got)
    }

    @Test fun `today line follows the situation and stays put through the day`() {
        val base = TodayLines.Context(today = 0, yesterday = 0, hour = 10, limit = 0, seed = 7)
        assertEquals(TodayLines.pick(base), TodayLines.pick(base))
        assertTrue(TodayLines.pick(base.copy(today = 60, limit = 50)).let { "limit" in it.lowercase() })
        assertTrue(TodayLines.pick(base.copy(today = 5, hour = 1)) in TodayLines.allLines)
        assertTrue(TodayLines.pick(base.copy(today = 3, yesterday = 30, hour = 21)).contains("yesterday"))
    }

    @Test fun `no line or badge text is mean`() {
        val banned = listOf("loser", "pathetic", "shame", "addict", "stupid", "lazy")
        (TodayLines.allLines + Badges.all.map { it.howTo }).forEach { line ->
            banned.forEach { word -> assertFalse("\"$line\" uses \"$word\"", word in line.lowercase()) }
        }
    }
}
