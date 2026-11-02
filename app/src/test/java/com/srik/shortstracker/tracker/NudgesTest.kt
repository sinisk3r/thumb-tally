package com.srik.shortstracker.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NudgesTest {
    private fun ctx(
        today: Int, session: Int = 3, limit: Int = 0, yesterday: Int = 0, hour: Int = 18, minute: Int = 0,
        sinceLastMs: Long? = 30_000, quickSkips: Int = 0,
    ) = NudgeContext(today, session, limit, yesterday, hour, minute, sinceLastMs, quickSkips)

    private fun pick(c: NudgeContext) = Nudges.pick(c, Random(1))

    @Test
    fun `milestones get a note, the shorts in between don't`() {
        listOf(10, 20, 30, 50, 75, 100, 150, 200, 300).forEach { n ->
            val nudge = pick(ctx(today = n))
            assertNotNull("no note at $n", nudge)
            assertEquals(Nudge.Tone.TEASE, nudge!!.tone)
        }
        listOf(2, 11, 19, 21, 49, 51, 250).forEach { n -> assertNull("unexpected note at $n", pick(ctx(today = n))) }
    }

    @Test
    fun `20 says congrats`() {
        val lines = (0 until 20).mapNotNull { Nudges.pick(ctx(today = 20), Random(it))?.text }.toSet()
        assertTrue(lines.any { it.startsWith("Congrats on hitting 20") })
    }

    @Test
    fun `daily limit warns at the limit and every 10 after, and hints 10 before`() {
        assertEquals(Nudge.Tone.WARN, pick(ctx(today = 50, limit = 50))?.tone)
        assertEquals(Nudge.Tone.WARN, pick(ctx(today = 60, limit = 50))?.tone)
        assertNull(pick(ctx(today = 55, limit = 50)))
        assertTrue(pick(ctx(today = 40, limit = 50))!!.text.contains("10"))
    }

    @Test
    fun `a long break is cheered`() {
        val nudge = pick(ctx(today = 4, session = 1, sinceLastMs = 4 * 60 * 60_000L))
        assertEquals(Nudge.Tone.CHEER, nudge?.tone)
        assertTrue(nudge!!.text.contains("4"))
    }

    @Test
    fun `late night start gets teased`() {
        val nudge = pick(ctx(today = 3, session = 1, hour = 1, minute = 12, sinceLastMs = 20 * 60_000L))
        assertNotNull(nudge)
    }

    @Test
    fun `a skipping spree is noticed`() {
        assertNotNull(pick(ctx(today = 13, quickSkips = 8)))
        assertNull(pick(ctx(today = 13, quickSkips = 7)))
    }

    @Test
    fun `passing yesterday is mentioned`() {
        assertTrue(pick(ctx(today = 41, yesterday = 40))!!.text.contains("40"))
    }

    @Test
    fun `there's a sample of each kind for the settings preview`() {
        val tones = Nudges.samples().map { it.tone }.toSet()
        assertEquals(Nudge.Tone.entries.toSet(), tones)
    }
}
