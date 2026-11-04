package com.srik.shortstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ViewEvent::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun views(): ViewDao

    companion object {
        /** v2 adds regular YouTube videos; everything recorded before was a short. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE views ADD COLUMN kind TEXT NOT NULL DEFAULT 'short'")
            }
        }

        /** v3 remembers which video each row was, for spotting rewatches. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE views ADD COLUMN videoKey TEXT")
            }
        }

        /** v4 remembers the song a video used, so it can be found again on Spotify or YouTube Music. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE views ADD COLUMN song TEXT")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shorts.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
