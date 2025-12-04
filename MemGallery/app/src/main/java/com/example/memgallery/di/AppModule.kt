package com.example.memgallery.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.database.AppDatabase
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "memgallery_db"
        )
        .addMigrations(
            AppDatabase.MIGRATION_8_9, 
            AppDatabase.MIGRATION_9_10, 
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideMemoryDao(appDatabase: AppDatabase): MemoryDao {
        return appDatabase.memoryDao()
    }

    @Provides
    @Singleton
    fun provideCollectionDao(appDatabase: AppDatabase): com.example.memgallery.data.local.dao.CollectionDao {
        return appDatabase.collectionDao()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }


    @Provides
    @Singleton
    fun provideTaskDao(appDatabase: AppDatabase): com.example.memgallery.data.local.dao.TaskDao {
        return appDatabase.taskDao()
    }
}
