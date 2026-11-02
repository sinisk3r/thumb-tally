package com.srik.shortstracker.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Good habits are worth showing off; cheeky ones are worth a laugh (never a lecture). */
enum class BadgeTone { GOOD, CHEEKY }

/**
 * One badge in the collection. [howTo] is shown while it's locked, [earned] builds the line shown once it's yours.
 * Tone rule (CLAUDE.md): playful, never shaming. Tease the habit, not the person.
 */
data class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val tone: BadgeTone,
    val howTo: String,
)

/** A badge plus whether you have it right now, and the line that explains why. */
data class BadgeState(val badge: Badge, val earned: Boolean, val detail: String)

/**
 * Every badge, in the order they're shown. Badges describe the chosen Patterns range (7 or 30 days), so they can
 * come and go as habits change. Each needs enough data to mean something.
 */
object Badges {
    val SPEED_SKIPPER = Badge("speed_skipper", "⚡", "Speed skipper", BadgeTone.CHEEKY,
        "Skip a third of your Shorts in under 2 seconds. Picky is a personality.")
    val DEEP_DIVER = Badge("deep_diver", "🤿", "Deep diver", BadgeTone.GOOD,
        "Give your Shorts 25 seconds each, and hardly skip. Actually watching? Wild.")
    val NIGHT_OWL = Badge("night_owl", "🦉", "Night owl", BadgeTone.CHEEKY,
        "Watch a quarter of your Shorts after 11 PM. Sleep is overrated, apparently.")
    val LOYAL_FAN = Badge("loyal_fan", "💛", "Loyal fan", BadgeTone.CHEEKY,
        "Get 15% of your Shorts from one creator. They should know your name by now.")
    val BINGE_MODE = Badge("binge_mode", "🍿", "Binge mode", BadgeTone.CHEEKY,
        "Watch 50 Shorts in one sitting. We'd rather you didn't, but here we are.")
    val ON_REPEAT = Badge("on_repeat", "🔁", "On repeat", BadgeTone.CHEEKY,
        "Watch the same video 3 times. No judgement. Some judgement.")
    val MARATHON = Badge("marathon", "🏃", "Marathon thumb", BadgeTone.CHEEKY,
        "Keep one sitting going for an hour. Your thumb deserves a medal and a nap.")
    val TRIPLE_THREAT = Badge("triple_threat", "🎪", "Triple threat", BadgeTone.CHEEKY,
        "Watch Shorts, Reels and Spotlight in the same stretch. Commitment to the bit.")
    val WEEKEND_WARRIOR = Badge("weekend_warrior", "🛋️", "Weekend warrior", BadgeTone.CHEEKY,
        "Do most of your scrolling on Saturday and Sunday. Weekdays are for pretending.")
    val SNACK_SIZED = Badge("snack_sized", "🥨", "Snack sized", BadgeTone.GOOD,
        "Keep your typical sitting under 5 minutes. In, out, done. Respect.")
    val DAYLIGHT = Badge("daylight", "☀️", "Daylight scroller", BadgeTone.GOOD,
        "Watch 20 Shorts with none after 11 PM. Your sleep schedule thanks you.")
    val TOUCHED_GRASS = Badge("touched_grass", "🌱", "Touched grass", BadgeTone.GOOD,
        "Take a whole day off Shorts between scrolling days. Outside has great graphics.")
    val EXPLORER = Badge("explorer", "🧭", "Explorer", BadgeTone.GOOD,
        "Watch 40 different creators. Variety, the spice of the feed.")

    val all = listOf(
        SPEED_SKIPPER, DEEP_DIVER, NIGHT_OWL, LOYAL_FAN, BINGE_MODE, ON_REPEAT,
        MARATHON, TRIPLE_THREAT, WEEKEND_WARRIOR, SNACK_SIZED, DAYLIGHT, TOUCHED_GRASS, EXPLORER,
    )

    /** [shorts] sorted oldest first, all inside the chosen range; [sessions] from [Stats.sessions]. */
    fun evaluate(shorts: List<ViewEvent>, sessions: List<Session>, zone: ZoneId): List<BadgeState> {
        val earned = mutableMapOf<Badge, String>()
        val times = shorts.map { Instant.ofEpochMilli(it.startedAt).atZone(zone) }
        val activeDays = times.map { it.toLocalDate() }.toSortedSet()

        val timed = shorts.filter { it.durationMs > 0 }
        if (timed.size >= 20) {
            val skip = timed.count { it.durationMs < Stats.QUICK_SKIP_MS }.toFloat() / timed.size
            val avg = timed.sumOf { it.durationMs } / timed.size
            if (skip >= 0.35f) {
                earned[SPEED_SKIPPER] = "${(skip * 100).toInt()}% of Shorts gone in under 2 seconds. Picky, or bored?"
            } else if (avg >= 25_000 && skip < 0.15f) {
                earned[DEEP_DIVER] = "You give each Short about ${avg / 1000} seconds. You actually watch them."
            }
        }
        if (shorts.size >= 20) {
            val late = times.count { it.hour >= 23 || it.hour < 5 }
            if (late >= shorts.size / 4) earned[NIGHT_OWL] = "$late of your Shorts were after 11 PM."
            if (late == 0) earned[DAYLIGHT] = "${shorts.size} Shorts and not one after 11 PM. Look at you."
            val top = shorts.mapNotNull { it.creator }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (top != null && top.value >= shorts.size * 0.15) {
                earned[LOYAL_FAN] = "${top.value} from ${top.key}. Maybe just subscribe?"
            }
            if (activeDays.size >= 3) {
                val weekend = times.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
                if (weekend >= shorts.size * 0.6) {
                    earned[WEEKEND_WARRIOR] = "${weekend * 100 / shorts.size}% of your Shorts landed on a weekend."
                }
            }
        }
        sessions.maxByOrNull { it.views }?.takeIf { it.views >= 50 }?.let {
            earned[BINGE_MODE] = "${it.views} Shorts in one sitting."
        }
        shorts.mapNotNull { it.videoKey }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= 3 }
            ?.let { (key, count) ->
                val who = key.split('|').getOrNull(1)?.takeIf { it.isNotBlank() }
                earned[ON_REPEAT] = "One video $count times${who?.let { " (from $it)" } ?: ""}. It must be good."
            }
        sessions.maxByOrNull { it.durationMs }?.takeIf { it.durationMs >= 60 * 60_000L }?.let {
            earned[MARATHON] = "One sitting lasted ${Stats.formatDuration(it.durationMs)}. Hydrate."
        }
        if (shorts.map { it.app }.toSet().size >= 3) {
            earned[TRIPLE_THREAT] = "YouTube, Instagram and Snapchat, all in one stretch."
        }
        if (sessions.size >= 5) {
            val avg = sessions.sumOf { it.durationMs } / sessions.size
            if (avg < 5 * 60_000L) earned[SNACK_SIZED] = "Your typical sitting is ${Stats.formatDuration(avg)}. Quick bites only."
        }
        if (activeDays.size >= 3) {
            val first = activeDays.first()
            val last = activeDays.last()
            val span = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
            val off = span - activeDays.size
            if (off > 0) earned[TOUCHED_GRASS] = "$off day${if (off == 1L) "" else "s"} without a single Short. Grass: touched."
        }
        val creators = shorts.mapNotNull { it.creator }.toSet().size
        if (creators >= 40) earned[EXPLORER] = "$creators different creators. Your feed gets around."

        return all.map { badge ->
            val line = earned[badge]
            BadgeState(badge, earned = line != null, detail = line ?: badge.howTo)
        }
    }
}
