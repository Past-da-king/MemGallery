package com.example.memgallery.data.remote.ai

import android.util.Log
import com.example.memgallery.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AIProviderFactory"

/**
 * Factory to select and provide the correct AI provider based on user settings.
 */
@Singleton
class AIProviderFactory @Inject constructor(
    private val geminiProvider: GeminiProvider,
    private val groqProvider: GroqProvider,
    private val localAIProvider: LocalAIProvider,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Get the currently selected AI provider.
     * Initializes the provider with the stored API key if not already initialized.
     */
    suspend fun getProvider(): AIProvider {
        val providerType = getProviderType()
        val provider = when (providerType) {
            AIProviderType.GROQ -> groqProvider
            AIProviderType.LOCAL -> localAIProvider
            else -> geminiProvider
        }
        
        // Initialize if not already
        if (!provider.isEnabled()) {
            val apiKey = getApiKey(providerType)
            if (!apiKey.isNullOrBlank() || providerType == AIProviderType.LOCAL) {
                provider.initialize(apiKey ?: "")
                Log.i(TAG, "Initialized ${provider.providerName} provider")
            }
        }
        
        return provider
    }
    
    /**
     * Get the currently selected provider type from settings.
     */
    suspend fun getProviderType(): AIProviderType {
        val providerString = settingsRepository.aiProviderFlow.first()
        return when (providerString.uppercase()) {
            "GROQ" -> AIProviderType.GROQ
            "LOCAL" -> AIProviderType.LOCAL
            else -> AIProviderType.GEMINI
        }
    }
    
    /**
     * Get the API key for the specified provider type.
     */
    /**
     * Get the API key for the currently selected provider type.
     */
    suspend fun getApiKey(): String? {
        return getApiKey(getProviderType())
    }

    /**
     * Get the API key for the specified provider type.
     */
    suspend fun getApiKey(providerType: AIProviderType): String? {
        return when (providerType) {
            AIProviderType.GROQ -> settingsRepository.groqApiKeyFlow.first()
            AIProviderType.GEMINI -> settingsRepository.apiKeyFlow.first()
            AIProviderType.LOCAL -> settingsRepository.localModelPathFlow.first()
        }
    }
    
    /**
     * Get provider synchronously (for use in non-suspend contexts)
     */
    fun getProviderSync(): AIProvider = runBlocking { getProvider() }
    
    /**
     * Check if the current provider is properly configured
     */
    suspend fun isProviderConfigured(): Boolean {
        val apiKey = getApiKey()
        return !apiKey.isNullOrBlank()
    }
    
    /**
     * Initialize the current provider with the stored API key
     */
    suspend fun initializeCurrentProvider(): Boolean {
        val providerType = getProviderType()
        val apiKey = getApiKey(providerType)
        
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "No API key configured for $providerType")
            return false
        }
        
        val provider = when (providerType) {
            AIProviderType.GROQ -> groqProvider
            AIProviderType.GEMINI -> geminiProvider
            AIProviderType.LOCAL -> localAIProvider
        }
        
        provider.initialize(apiKey)
        Log.i(TAG, "Initialized ${provider.providerName} provider")
        return true
    }
    
    /**
     * Disable all providers (e.g., when clearing API keys)
     */
    fun disableAllProviders() {
        geminiProvider.disable()
        groqProvider.disable()
        localAIProvider.disable()
        Log.i(TAG, "All providers disabled")
    }
}
