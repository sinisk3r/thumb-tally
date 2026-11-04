package com.srik.shortstracker.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OffReminderTest {
    private val now = 1_000_000_000L

    private fun remind(
        trackingOn: Boolean = false,
        remindersOn: Boolean = true,
        lastReminderAt: Long = 0,
        pausedAt: Long = 0,
    ) = OffReminder.shouldRemind(now, trackingOn, remindersOn, lastReminderAt, pausedAt)

    @Test fun `tracking off reminds`() = assertTrue(remind())

    @Test fun `nothing when tracking is on or reminders are off`() {
        assertFalse(remind(trackingOn = true))
        assertFalse(remind(remindersOn = false))
    }

    @Test fun `reminders are spaced out`() {
        assertFalse(remind(lastReminderAt = now - OffReminder.GAP_MS + 1))
        assertTrue(remind(lastReminderAt = now - OffReminder.GAP_MS))
    }

    @Test fun `right after a payment pause, stay quiet`() = assertFalse(remind(pausedAt = now - 60_000))

    @Test fun `every payment app is in the accessibility config, or the pause never fires`() {
        val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
        val listed = Regex("""packageNames="([^"]+)"""").find(config)!!.groupValues[1].split(',').toSet()
        PaymentApps.labels.keys.forEach { assertTrue("$it missing from packageNames", it in listed) }
        ShortsApp.entries.forEach { assertTrue("${it.packageName} missing", it.packageName in listed) }
    }
}
