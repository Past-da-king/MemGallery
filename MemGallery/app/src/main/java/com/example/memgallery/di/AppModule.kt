package com.example.memgallery.di

import android.content.Context
import androidx.room.Room
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideMemoryDao(appDatabase: AppDatabase): MemoryDao {
        return appDatabase.memoryDao()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}
