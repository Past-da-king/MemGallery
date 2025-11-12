package com.example.memg.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.memg.model.MemoryObject
import com.example.memg.model.Folder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

@Database(
    entities = [MemoryObject::class, Folder::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memgallery_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}