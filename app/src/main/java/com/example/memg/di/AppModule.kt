package com.example.memg.di

import android.content.Context
import com.example.memg.data.AppDatabase
import com.example.memg.data.MemoryDao
import com.example.memg.data.FolderDao
import com.example.memg.repository.MemGalleryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Singleton
    @Provides
    fun provideMemoryDao(appDatabase: AppDatabase): MemoryDao {
        return appDatabase.memoryDao()
    }
    
    @Singleton
    @Provides
    fun provideFolderDao(appDatabase: AppDatabase): FolderDao {
        return appDatabase.folderDao()
    }
    
    @Singleton
    @Provides
    fun provideMemGalleryRepository(
        memoryDao: MemoryDao,
        folderDao: FolderDao
    ): MemGalleryRepository {
        return MemGalleryRepository(memoryDao, folderDao)
    }
}