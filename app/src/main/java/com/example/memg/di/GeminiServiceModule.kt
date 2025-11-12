package com.example.memg.di

import android.content.Context
import com.example.memg.ai.GeminiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeminiServiceModule {
    
    @Provides
    @Singleton
    fun provideGeminiService(@ApplicationContext context: Context): GeminiService {
        return GeminiService(context)
    }
}