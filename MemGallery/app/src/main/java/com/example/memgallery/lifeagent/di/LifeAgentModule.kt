package com.example.memgallery.lifeagent.di

import com.example.memgallery.data.local.database.AppDatabase
import com.example.memgallery.lifeagent.data.LifeAgentDao
import com.example.memgallery.lifeagent.composio.ComposioApi
import com.example.memgallery.lifeagent.composio.ComposioClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LifeAgentModule {
    @Provides
    @Singleton
    fun provideLifeAgentDao(db: AppDatabase): LifeAgentDao = db.lifeAgentDao()

    @Provides
    @Singleton
    fun provideComposioApi(client: ComposioClient): ComposioApi = client
}
