package com.srik.shortstracker.tracker

/** What kind of video a row is. [key] is what's stored in the database. */
enum class ViewKind(val key: String) {
    SHORT("short"),
    VIDEO("video");

    companion object {
        fun fromKey(key: String?): ViewKind = entries.firstOrNull { it.key == key } ?: SHORT
    }
}
