package com.srik.shortstracker.tracker

/**
 * The song behind a video, read from the app's own audio line ("Lipps Inc. · Funkytown"), never by listening.
 * Instagram shows it under the creator's name, Snapchat in the sound line, YouTube Shorts in a pill next to the
 * title (only when the sound uses real music; plain original sounds have no pill).
 */
object Songs {
    private val ORIGINAL = Regex("^original (audio|sound)\\b", RegexOption.IGNORE_CASE)
    private const val SEPARATOR = " · "
    /** YouTube Shorts: "Original Sound (Contains music from: Intenso boom boom · DJ Guuga)". */
    private val CONTAINS = Regex("contains music from:\\s*(.+?)\\)?$", RegexOption.IGNORE_CASE)

    /** "Artist · Title" → the same, tidied. Null for anything else, and for "Original audio" / "Original sound". */
    fun fromLabel(label: String?): String? {
        val raw = label?.trim() ?: return null
        val text = (CONTAINS.find(raw)?.groupValues?.get(1)?.trim() ?: raw).takeIf { SEPARATOR in it } ?: return null
        val parts = text.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.any { ORIGINAL.containsMatchIn(it) }) return null
        return parts.joinToString(SEPARATOR).take(120)
    }

    /** What to type into Spotify or YouTube Music. */
    fun searchQuery(song: String): String = song.split(SEPARATOR).joinToString(" ")
}
