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
    private val deepSeekProvider: DeepSeekProvider,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Get the currently selected AI provider.
     * Initializes the provider with the stored API key if not already initialized.
     */
    suspend fun getProvider(): AIProvider = providerFor(getProviderType())

    /**
     * Get any provider, initialised with its OWN saved key (every provider keeps its key at the same time).
     * Used for the Main brain, and for the Voice and Images jobs.
     */
    suspend fun providerFor(providerType: AIProviderType): AIProvider {
        val provider = when (providerType) {
            AIProviderType.GROQ -> groqProvider
            AIProviderType.LOCAL -> localAIProvider
            AIProviderType.OPENAI_COMPATIBLE -> openAICompatibleProvider
            AIProviderType.DEEPSEEK -> deepSeekProvider
            else -> geminiProvider
        }
        if (!provider.isEnabled()) {
            val apiKey = getApiKey(providerType)
            if (!apiKey.isNullOrBlank() || providerType == AIProviderType.LOCAL || providerType == AIProviderType.OPENAI_COMPATIBLE) {
                provider.initialize(apiKey ?: "")
                Log.i(TAG, "Initialized ${provider.providerName} provider")
            }
        }
        return provider
    }

    suspend fun voiceProviderType(): AIProviderType? = when (settingsRepository.resolvedVoiceProvider()) {
        JobRouting.GEMINI -> AIProviderType.GEMINI
        JobRouting.GROQ -> AIProviderType.GROQ
        else -> null
    }

    suspend fun imageProviderType(): AIProviderType? = when (settingsRepository.resolvedImageProvider()) {
        JobRouting.GEMINI -> AIProviderType.GEMINI
        JobRouting.GROQ -> AIProviderType.GROQ
        JobRouting.DEEPSEEK -> AIProviderType.DEEPSEEK
        JobRouting.CUSTOM -> AIProviderType.OPENAI_COMPATIBLE
        else -> null
    }

    /**
     * Get the currently selected provider type from settings.
     */
    suspend fun getProviderType(): AIProviderType {
        val providerString = settingsRepository.resolvedMainProvider()
        return when (providerString.uppercase()) {
            "DEEPSEEK" -> AIProviderType.DEEPSEEK
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
            AIProviderType.DEEPSEEK -> settingsRepository.deepSeekApiKeyFlow.first()
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
            AIProviderType.DEEPSEEK -> deepSeekProvider
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
        deepSeekProvider.disable()
        Log.i(TAG, "All providers disabled")
    }
}
