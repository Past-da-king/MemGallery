package com.example.memgallery

import android.app.Application
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.service.MemoryProcessingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MemGalleryApplication : Application() {

    @Inject
    lateinit var geminiService: GeminiService

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var memoryProcessingService: MemoryProcessingService

    override fun onCreate() {
        super.onCreate()
        initializeGeminiService()
        memoryProcessingService.startProcessing()
    }

    private fun initializeGeminiService() {
        val apiKey = settingsRepository.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            geminiService.initialize(apiKey)
        }
    }
}
