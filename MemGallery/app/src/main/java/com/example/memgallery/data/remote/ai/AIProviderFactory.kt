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
    private val openAICompatibleProvider: OpenAICompatibleProvider,
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
            AIProviderType.OPENAI_COMPATIBLE -> openAICompatibleProvider
            else -> geminiProvider
        }
        
        // Initialize if not already
        if (!provider.isEnabled()) {
            val apiKey = getApiKey(providerType)
            // Local provider might not need a key, or uses path as key
            if (!apiKey.isNullOrBlank() || providerType == AIProviderType.LOCAL || providerType == AIProviderType.OPENAI_COMPATIBLE) {
                // OpenAI compatible might rely on internal config fetching, but initialize expects a key.
                // We pass the key if available, or empty string if it relies on repo internal fetch. 
                // Provider implementation handles this.
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
            "OPENAI_COMPATIBLE" -> AIProviderType.OPENAI_COMPATIBLE
            else -> AIProviderType.GEMINI
        }
    }
    
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
            AIProviderType.OPENAI_COMPATIBLE -> settingsRepository.customApiKeyFlow.first()
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
        // Local logic is path-based; OpenAI logic might be URL+Key
        val type = getProviderType()
        if (type == AIProviderType.OPENAI_COMPATIBLE) {
             val url = settingsRepository.customBaseUrlFlow.first()
             return url.isNotBlank() // Key technically optional for some local endpoints?
        }
        val apiKey = getApiKey()
        return !apiKey.isNullOrBlank()
    }
    
    /**
     * Initialize the current provider with the stored API key
     */
    suspend fun initializeCurrentProvider(): Boolean {
        val providerType = getProviderType()
        val apiKey = getApiKey(providerType)
        
        if (apiKey.isNullOrBlank() && providerType != AIProviderType.OPENAI_COMPATIBLE) {
            Log.w(TAG, "No API key configured for $providerType")
            return false
        }
        
        val provider = when (providerType) {
            AIProviderType.GROQ -> groqProvider
            AIProviderType.GEMINI -> geminiProvider
            AIProviderType.LOCAL -> localAIProvider
            AIProviderType.OPENAI_COMPATIBLE -> openAICompatibleProvider
        }
        
        provider.initialize(apiKey ?: "")
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
        openAICompatibleProvider.disable()
        Log.i(TAG, "All providers disabled")
    }
}
