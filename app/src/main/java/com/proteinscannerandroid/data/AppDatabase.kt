package com.proteinscannerandroid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScanHistoryEntity::class, FavoriteEntity::class, PendingScan::class, DailyIntakeEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pendingScanDao(): PendingScanDao
    abstract fun dailyIntakeDao(): DailyIntakeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add pending_scans table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_scans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        barcode TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        errorReason TEXT
                    )
                """.trimIndent())
            }
        }

        // Migration from version 2 to 3: Add daily_intake table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_intake (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        proteinGrams REAL NOT NULL,
                        pdcaasScore REAL,
                        effectiveProteinGrams REAL,
                        barcode TEXT,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "protein_scanner_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
