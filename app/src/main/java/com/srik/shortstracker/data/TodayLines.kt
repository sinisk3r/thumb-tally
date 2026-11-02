package com.srik.shortstracker.data

/**
 * The one-liner under "Today". People remember the line, not the number, so this is where the number gets its
 * meaning. Playful, never shaming (CLAUDE.md tone rule): tease the scrolling, not the person.
 *
 * The line stays the same for a situation through the day ([seed] is the date), so it doesn't flicker on every
 * recomposition, but a new day or a new situation picks a different one.
 */
object TodayLines {
    data class Context(
        val today: Int,
        val yesterday: Int,
        val hour: Int,
        /** 0 means no limit. */
        val limit: Int,
        val seed: Int,
    )

    private val none = listOf(
        "Clean slate. Suspiciously clean.",
        "Zero so far. The algorithm misses you.",
        "Nothing yet. Who even are you?",
    )
    private val light = listOf(
        "A light snack. Nobody's judging.",
        "Just warming up the thumb.",
        "A few here and there. Totally under control.",
    )
    private val medium = listOf(
        "Your thumb's getting its cardio in.",
        "That's a healthy… let's call it interest.",
        "Respectable. For now.",
    )
    private val heavy = listOf(
        "Your thumb would like to speak to a union.",
        "The algorithm knows your middle name by now.",
        "That's a lot of \"just one more\".",
    )
    private val huge = listOf(
        "Your thumb has filed for overtime.",
        "Legend says they're still scrolling.",
        "The algorithm sends its warmest thanks.",
    )
    private val overLimit = listOf(
        "Past your limit. We won't tell anyone.",
        "The limit was more of a suggestion, apparently.",
        "Limit? We hardly knew her.",
    )
    private val lateNight = listOf(
        "It's late. The videos will still be there tomorrow.",
        "Night shift, huh?",
        "Your pillow called. It's worried.",
    )
    private val belowYesterday = listOf(
        "Fewer than yesterday so far. Look at you.",
        "Behind yesterday's pace. That's the good kind of losing.",
    )

    fun pick(c: Context): String {
        val pool = when {
            c.limit > 0 && c.today > c.limit -> overLimit
            c.today > 0 && (c.hour >= 23 || c.hour < 5) -> lateNight
            c.hour >= 20 && c.yesterday > 0 && c.today in 1 until c.yesterday -> belowYesterday
            c.today == 0 -> none
            c.today <= 10 -> light
            c.today <= 40 -> medium
            c.today <= 100 -> heavy
            else -> huge
        }
        return pool[Math.floorMod(c.seed, pool.size)]
    }

    /** Every line, for tests and for checking the tone in one place. */
    val allLines get() = none + light + medium + heavy + huge + overLimit + lateNight + belowYesterday
}
