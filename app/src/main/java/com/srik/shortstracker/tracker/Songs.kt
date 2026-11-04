package com.srik.shortstracker.tracker

/**
 * The song behind a video, read from the app's own audio line ("Lipps Inc. · Funkytown"), never by listening.
 * Instagram shows it under the creator's name, Snapchat in the sound line. YouTube Shorts only shows a
 * "See more videos using this sound" button with no name, so Shorts have no song.
 */
object Songs {
    private val ORIGINAL = Regex("^original (audio|sound)\\b", RegexOption.IGNORE_CASE)
    private const val SEPARATOR = " · "

    /** "Artist · Title" → the same, tidied. Null for anything else, and for "Original audio" / "Original sound". */
    fun fromLabel(label: String?): String? {
        val text = label?.trim()?.takeIf { SEPARATOR in it } ?: return null
        val parts = text.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.any { ORIGINAL.containsMatchIn(it) }) return null
        return parts.joinToString(SEPARATOR).take(120)
    }

    /** What to type into Spotify or YouTube Music. */
    fun searchQuery(song: String): String = song.split(SEPARATOR).joinToString(" ")
}
