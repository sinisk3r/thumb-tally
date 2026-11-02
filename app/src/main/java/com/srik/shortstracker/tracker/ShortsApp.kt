package com.srik.shortstracker.tracker

/** [label] names the feature ("Instagram Reels"); [appName] is what people call the app itself. */
enum class ShortsApp(val packageName: String, val label: String, val appName: String) {
    YOUTUBE("com.google.android.youtube", "YouTube Shorts", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram Reels", "Instagram"),
    SNAPCHAT("com.snapchat.android", "Snapchat Spotlight", "Snapchat");

    companion object {
        fun fromPackage(pkg: String?): ShortsApp? = entries.firstOrNull { it.packageName == pkg }
    }
}
