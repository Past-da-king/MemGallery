package com.example.memgallery

import android.app.Application
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MemGalleryApplication : Application() {

    @Inject
    lateinit var geminiService: GeminiService

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        initializeGeminiService()
    }

    private fun initializeGeminiService() {
        val apiKey = settingsRepository.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            geminiService.initialize(apiKey)
        }
    }
}
