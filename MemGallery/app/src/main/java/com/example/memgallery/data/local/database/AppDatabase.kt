package com.example.memgallery.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.memgallery.data.local.converters.TagListConverter
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity

@Database(entities = [MemoryEntity::class, TaskEntity::class], version = 9, exportSchema = false)
@TypeConverters(TagListConverter::class, com.example.memgallery.data.local.converters.ActionListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao

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
    }
}
