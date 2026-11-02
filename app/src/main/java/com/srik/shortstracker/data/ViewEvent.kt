package com.srik.shortstracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One short, reel or regular YouTube video you watched. [durationMs] grows as you keep watching (or come back to) it. */
@Entity(tableName = "views", indices = [Index("startedAt")])
data class ViewEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Package name of the app, see [com.srik.shortstracker.tracker.ShortsApp]. */
    val app: String,
    val startedAt: Long,
    val durationMs: Long = 0,
    val creator: String? = null,
    val caption: String? = null,
    val screenshotPath: String? = null,
    /** [com.srik.shortstracker.tracker.ViewKind.key]: "short" or "video". */
    @ColumnInfo(defaultValue = "short") val kind: String = "short",
    /** Identifies the video (creator + title), so watching it again later can be spotted. Null before v3. */
    val videoKey: String? = null,
)
