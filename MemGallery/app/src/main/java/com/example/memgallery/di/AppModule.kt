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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.memgallery.data.remote.github.GitHubService


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
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
            AppDatabase.MIGRATION_13_14,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17
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

    @Provides
    @Singleton
    fun provideChatDao(appDatabase: AppDatabase): com.example.memgallery.data.local.dao.ChatDao {
        return appDatabase.chatDao()
    }
    @Provides
    @Singleton
    fun provideGitHubService(): GitHubService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.example.memgallery.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
            // Important: Redact the auth token so it never appears in logs
            redactHeader("Authorization")
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }
}

