package com.srik.shortstracker.tracker

import kotlin.random.Random

data class Nudge(val text: String, val tone: Tone) {
    enum class Tone { CHEER, TEASE, WARN }
}

/** Everything a nudge can react to, at the moment a new Short starts. */
data class NudgeContext(
    /** Shorts today, including this one. */
    val today: Int,
    /** Shorts in this sitting, including this one. */
    val session: Int,
    /** 0 means no limit. */
    val limit: Int,
    val yesterday: Int,
    val hour: Int,
    val minute: Int,
    /** How long since the last Short ended, or null if there wasn't one today. */
    val sinceLastMs: Long?,
    /** Shorts in a row skipped within a couple of seconds, before this one. */
    val quickSkips: Int = 0,
)

/**
 * The cheeky messages in the pop-up. At most one per Short, and only at moments worth mentioning:
 * the daily limit, a long break, late nights, skipping sprees, milestones, and passing yesterday.
 */
object Nudges {
    private const val HOUR_MS = 60 * 60_000L

    fun pick(c: NudgeContext, random: Random = Random.Default): Nudge? {
        fun cheer(vararg lines: String) = Nudge(lines.random(random), Nudge.Tone.CHEER)
        fun tease(vararg lines: String) = Nudge(lines.random(random), Nudge.Tone.TEASE)
        fun warn(vararg lines: String) = Nudge(lines.random(random), Nudge.Tone.WARN)
        val over = c.today - c.limit

        return when {
            c.limit > 0 && over == 0 -> warn(
                "That's your ${c.limit}. Still going?",
                "Limit reached. Are you sure you want to keep going?",
                "${c.limit} done. This is where past-you asked you to stop.",
            )
            c.limit > 0 && over > 0 && over % 10 == 0 -> warn(
                "$over over your limit. Are you sure about this?",
                "$over past the line. The line is very far behind you.",
                "Your limit was ${c.limit}. This is ${c.today}. Just saying.",
            )
            c.limit >= 30 && over == -10 -> tease(
                "10 left before your limit. Choose wisely.",
                "10 to go. Make them count.",
            )
            c.session == 1 && c.sinceLastMs != null && c.sinceLastMs >= 3 * HOUR_MS -> cheer(
                "Welcome back. ${c.sinceLastMs / HOUR_MS} hours without Shorts. Respect.",
                "${c.sinceLastMs / HOUR_MS} hours clean. Look at you.",
            )
            c.today == 1 && c.hour >= 14 -> cheer(
                "First one today, and it's already afternoon. Nice restraint.",
                "First Short at ${formatTime(c.hour, c.minute)}. Impressive.",
            )
            c.session == 1 && c.hour in 0..4 -> tease(
                "It's ${formatTime(c.hour, c.minute)}. The Shorts will still be here tomorrow.",
                "${formatTime(c.hour, c.minute)}? Bold.",
                "Sleep is also a great piece of content.",
            )
            c.quickSkips == 8 || (c.quickSkips > 8 && c.quickSkips % 10 == 0) -> tease(
                "${c.quickSkips} skips in a row. Nothing good on tonight?",
                "Skip, skip, skip. The algorithm is sweating.",
                "${c.quickSkips} in under 2 seconds each. Speedrunning boredom?",
            )
            c.today in TODAY_MILESTONES || (c.today > 200 && c.today % 100 == 0) -> tease(*milestoneLines(c.today))
            c.session in SESSION_MILESTONES || (c.session > 60 && c.session % 30 == 0) -> tease(
                "${c.session} in one sitting. Stretch break?",
                "${c.session} in a row. Your thumb has entered beast mode.",
                "${c.session} without looking up. The ceiling misses you.",
            )
            c.yesterday >= 10 && c.today == c.yesterday + 1 -> tease(
                "You just passed yesterday's ${c.yesterday}. New personal worst?",
                "That's more than all of yesterday (${c.yesterday}).",
            )
            else -> null
        }
    }

    /** One of each kind, for trying the pop-up from Settings. */
    fun samples(): List<Nudge> = listOf(
        NudgeContext(today = 20, session = 5, limit = 0, yesterday = 0, hour = 15, minute = 0, sinceLastMs = 60_000),
        NudgeContext(today = 4, session = 1, limit = 0, yesterday = 0, hour = 20, minute = 0, sinceLastMs = 4 * HOUR_MS),
        NudgeContext(today = 12, session = 9, limit = 0, yesterday = 0, hour = 21, minute = 0, sinceLastMs = 1_000, quickSkips = 8),
        NudgeContext(today = 50, session = 12, limit = 50, yesterday = 0, hour = 22, minute = 0, sinceLastMs = 1_000),
        NudgeContext(today = 3, session = 1, limit = 0, yesterday = 0, hour = 1, minute = 12, sinceLastMs = 20 * 60_000L),
    ).mapNotNull { pick(it) }

    private val TODAY_MILESTONES = setOf(10, 20, 30, 50, 75, 100, 150, 200)
    private val SESSION_MILESTONES = setOf(20, 40, 60)

    private fun milestoneLines(n: Int): Array<String> = when (n) {
        10 -> arrayOf("Congrats on hitting 10. Warm-up complete.", "10 today. The algorithm says hi.")
        20 -> arrayOf("Congrats on hitting 20! Your thumb is officially warmed up.", "20 today. That's a whole playlist.")
        30 -> arrayOf("30 today. Achievement unlocked: Thumb cardio.", "30! The algorithm is starting to know you.")
        50 -> arrayOf("50 today. Half a century of scrolling.", "50. Your thumb deserves a medal. Your brain, a nap.")
        75 -> arrayOf("75 today. Is this still fun, or just a habit?", "75. Somewhere a book is feeling ignored.")
        100 -> arrayOf("100 today. Triple digits!", "💯 Shorts today. Not sure that's the good kind of 100.")
        else -> arrayOf("$n today. Legendary. Concerning, but legendary.", "$n. The algorithm thanks you for your service.")
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(h, minute, if (hour < 12) "AM" else "PM")
    }
}
