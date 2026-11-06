package com.example.memgallery.lifeagent.di

import com.example.memgallery.data.local.database.AppDatabase
import com.example.memgallery.lifeagent.data.LifeAgentDao
import com.example.memgallery.lifeagent.composio.ComposioApi
import com.example.memgallery.lifeagent.composio.ComposioClient
import com.example.memgallery.lifeagent.composio.ComposioConnectClient
import com.example.memgallery.lifeagent.composio.RoutingComposioApi
import com.example.memgallery.data.repository.SettingsRepository
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
    fun provideComposioConnect(settings: SettingsRepository): ComposioConnectClient =
        ComposioConnectClient(
            keyProvider = { settings.composioApiKey() },
            http = ComposioConnectClient.defaultHttp(),
        )

    /**
     * One ComposioApi for the whole app: the backend REST door for project, personal and org keys, the Connect MCP
     * door for consumer keys, and a fallback between them so a key is never refused for being in the wrong door.
     */
    @Provides
    @Singleton
    fun provideComposioApi(
        rest: ComposioClient,
        connect: ComposioConnectClient,
        settings: SettingsRepository,
    ): ComposioApi = RoutingComposioApi(
        rest = rest,
        connect = connect,
        keyKindProvider = { settings.composioKeyKind() },
        keyProvider = { settings.composioApiKey() },
    )
}
