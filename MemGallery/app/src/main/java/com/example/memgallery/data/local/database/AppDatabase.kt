package com.example.memgallery.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.memgallery.data.local.converters.TagListConverter
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity

@Database(entities = [MemoryEntity::class], version = 4, exportSchema = false)
@TypeConverters(TagListConverter::class, com.example.memgallery.data.local.converters.ActionListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
