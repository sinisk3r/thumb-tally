package com.srik.shortstracker.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OffReminderTest {
    private val now = 1_000_000_000L

    private fun decide(
        trackingOn: Boolean = false,
        remindersOn: Boolean = true,
        lastReminderAt: Long = 0,
        pausedAt: Long = 0,
        usage: Boolean = true,
        opened: ShortsApp? = ShortsApp.INSTAGRAM,
    ) = OffReminder.decide(now, trackingOn, remindersOn, lastReminderAt, pausedAt, usage, opened)

    @Test fun `opening a watched app while off reminds`() =
        assertEquals(Reminder.Missed(ShortsApp.INSTAGRAM), decide())

    @Test fun `nothing when tracking is on or reminders are off`() {
        assertNull(decide(trackingOn = true))
        assertNull(decide(remindersOn = false))
    }

    @Test fun `with usage access, no watched app opened means no reminder`() =
        assertNull(decide(opened = null))

    @Test fun `missed reminders are spaced out`() {
        assertNull(decide(lastReminderAt = now - OffReminder.MISSED_GAP_MS + 1))
        assertEquals(Reminder.Missed(ShortsApp.INSTAGRAM), decide(lastReminderAt = now - OffReminder.MISSED_GAP_MS))
    }

    @Test fun `right after a payment pause, stay quiet`() =
        assertNull(decide(pausedAt = now - 60_000))

    @Test fun `without usage access, a rare plain reminder`() {
        assertEquals(Reminder.Off, decide(usage = false, opened = null))
        assertNull(decide(usage = false, lastReminderAt = now - OffReminder.MISSED_GAP_MS))
    }

    @Test fun `every payment app is in the accessibility config, or the pause never fires`() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val listed = Regex("""packageNames="([^"]+)"""").find(config)!!.groupValues[1].split(',').toSet()
        PaymentApps.labels.keys.forEach { assertTrue("$it missing from packageNames", it in listed) }
        ShortsApp.entries.forEach { assertTrue("${it.packageName} missing", it.packageName in listed) }
    }
}
