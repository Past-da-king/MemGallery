package com.example.memgallery.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.memgallery.data.local.converters.TagListConverter
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity

import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef

@Database(
    entities = [
        MemoryEntity::class, 
        TaskEntity::class, 
        CollectionEntity::class, 
        MemoryCollectionCrossRef::class
    ], 
    version = 12, 
    exportSchema = false
)
@TypeConverters(TagListConverter::class, com.example.memgallery.data.local.converters.ActionListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkUrl TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkTitle TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkDescription TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkImageUrl TEXT")
                database.execSQL("ALTER TABLE memories ADD COLUMN bookmarkFaviconUrl TEXT")
            }
        }
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE tasks ADD COLUMN creationTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (e: android.database.sqlite.SQLiteException) {
                    if (e.message?.contains("duplicate column name") == true) {
                        android.util.Log.w("Migration_9_10", "Column 'creationTimestamp' already exists. Skipping.")
                    } else {
                        throw e
                    }
                }
            }
        }
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isApproved column to tasks, default to 1 (true) for existing tasks
                database.execSQL("ALTER TABLE tasks ADD COLUMN isApproved INTEGER NOT NULL DEFAULT 1")
                
                // Create collections table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `creationTimestamp` INTEGER NOT NULL
                    )
                """)
                
                // Create memory_collection_cross_ref table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `memory_collection_cross_ref` (
                        `memoryId` INTEGER NOT NULL, 
                        `collectionId` INTEGER NOT NULL, 
                        PRIMARY KEY(`memoryId`, `collectionId`),
                        FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """)
                
                // Create indices for cross ref
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_collection_cross_ref_memoryId` ON `memory_collection_cross_ref` (`memoryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_collection_cross_ref_collectionId` ON `memory_collection_cross_ref` (`collectionId`)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add type, isRecurring, recurrenceRule to tasks if they don't exist
                // We use try-catch for each column to handle "duplicate column name" error 
                // which happens if user did a fresh install on v11 (which created table with these columns)
                // but DB version was 11, so now going to 12 checks this migration.

                val columns = listOf(
                    "ALTER TABLE tasks ADD COLUMN type TEXT NOT NULL DEFAULT 'TODO'",
                    "ALTER TABLE tasks ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE tasks ADD COLUMN recurrenceRule TEXT",
                    "ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'",
                    "ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'"
                )

                for (sql in columns) {
                    try {
                        database.execSQL(sql)
                    } catch (e: android.database.sqlite.SQLiteException) {
                        // Check for duplicate column error (message varies by SQLite version/device, but usually contains "duplicate column name")
                        if (e.message?.contains("duplicate column name") == true) {
                            android.util.Log.w("Migration_11_12", "Column already exists, skipping: ${e.message}")
                        } else {
                            // Re-throw other errors
                            throw e
                        }
                    }
                }
            }
        }
    }
}
