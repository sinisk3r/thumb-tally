package com.srik.shortstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ViewDao {
    @Insert
    suspend fun insert(event: ViewEvent): Long

    @Query("UPDATE views SET durationMs = durationMs + :ms WHERE id = :id")
    suspend fun addDuration(id: Long, ms: Long)

    @Query("UPDATE views SET creator = :creator, caption = :caption WHERE id = :id")
    suspend fun updateDetails(id: Long, creator: String?, caption: String?)

    @Query("UPDATE views SET screenshotPath = :path WHERE id = :id")
    suspend fun setScreenshot(id: Long, path: String?)

    @Query("SELECT * FROM views WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<ViewEvent>>

    @Query("SELECT COUNT(*) FROM views WHERE startedAt >= :since AND kind = :kind")
    suspend fun countSince(since: Long, kind: String): Int

    @Query("SELECT COUNT(*) FROM views WHERE startedAt >= :from AND startedAt < :until AND kind = :kind")
    suspend fun countBetween(from: Long, until: Long, kind: String): Int

    @Query("SELECT * FROM views WHERE screenshotPath IS NOT NULL AND startedAt < :before")
    suspend fun withScreenshotsBefore(before: Long): List<ViewEvent>

    @Query("DELETE FROM views")
    suspend fun deleteAll()
}
