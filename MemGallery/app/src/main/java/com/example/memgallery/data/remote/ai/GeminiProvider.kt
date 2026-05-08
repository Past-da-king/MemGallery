package com.example.memgallery.data.remote.ai

import android.content.Context
import android.util.Log
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import com.google.genai.Client
import com.google.genai.types.AutomaticFunctionCallingConfig
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.HttpOptions
import com.google.genai.types.HttpRetryOptions
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.Tool
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiProvider"
private const val FALLBACK_MODEL_NAME = "gemini-3.1-flash-lite"

/**
 * Google Gemini AI provider implementation.
 * This extracts the existing Gemini logic into the AIProvider interface.
 */
@Singleton
class GeminiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val settingsRepository: SettingsRepository
) : AIProvider {

    override val providerName: String = "Gemini"

    private var client: Client? = null

    private suspend fun currentModel(): String =
        settingsRepository.geminiModelIdFlow.first().ifBlank { FALLBACK_MODEL_NAME }

    private suspend fun currentMaxToolCalls(): Int =
        settingsRepository.maxToolCallsFlow.first()

    override fun initialize(apiKey: String) {
        val httpOptions = HttpOptions.builder()
            .retryOptions(
                HttpRetryOptions.builder()
                    .attempts(3)
                    .httpStatusCodes(408, 429)
                    .build()
            )
            .build()
        
        client = Client.builder()
            .apiKey(apiKey)
            .httpOptions(httpOptions)
            .build()
        
        Log.i(TAG, "Gemini client initialized with retry options")
    }
    
    override fun isEnabled(): Boolean = client != null
    
    override fun disable() {
        client = null
    }
    
    override suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val tempClient = Client.builder().apiKey(apiKey).build()
            tempClient.models.generateContent(currentModel(), "test", GenerateContentConfig.builder().build())
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch the list of Gemini models available to the given API key, filtered to those
     * that support generateContent. Strips the `models/` prefix from each name.
     */
    suspend fun listAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).build()
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))
                val parsed = gson.fromJson(body, ModelsListResponse::class.java)
                val names = parsed.models
                    .orEmpty()
                    .filter { it.supportedGenerationMethods.orEmpty().contains("generateContent") }
                    .mapNotNull { it.name?.removePrefix("models/") }
                    .filter { it.startsWith("gemini-") }
                    .sorted()
                Result.success(names)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list Gemini models", e)
            Result.failure(e)
        }
    }

    private data class ModelsListResponse(
        val models: List<ModelInfo>?
    )

    private data class ModelInfo(
        val name: String?,
        @SerializedName("supportedGenerationMethods")
        val supportedGenerationMethods: List<String>?
    )
    
    override suspend fun processMemory(
        imageDataList: List<ImageData>,
        audioData: AudioData?,
        userText: String?,
        bookmarkUrl: String?,
        bookmarkTitle: String?,
        bookmarkDescription: String?,
        bookmarkImageUrl: String?,
        existingCollections: List<String>,
        systemPrompt: String,
        responseSchemaJson: String
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Gemini client is not initialized.")
        )
        
        try {
            val responseSchema = Schema.fromJson(responseSchemaJson)
            val systemInstructionContent = Content.fromParts(Part.fromText(systemPrompt))
            
            val config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .systemInstruction(systemInstructionContent)
                .build()
            
            val parts = mutableListOf<Part>()
            val promptBuilder = StringBuilder()
            
            // Add images
            if (imageDataList.isNotEmpty()) {
                Log.d(TAG, "Processing ${imageDataList.size} images")
                imageDataList.forEach { imageData ->
                    parts.add(Part.fromBytes(imageData.bytes, imageData.mimeType))
                }
                promptBuilder.append("Analyze the provided images in detail. ")
            }
            
            // Add bookmark image reference if no local images
            if (bookmarkImageUrl != null && imageDataList.isEmpty()) {
                promptBuilder.append("The user bookmarked a page with this preview image URL: $bookmarkImageUrl. ")
            }
            
            // Add bookmark info
            if (bookmarkUrl != null) {
                promptBuilder.append("Analyze this bookmarked webpage: $bookmarkUrl. ")
                if (!bookmarkTitle.isNullOrBlank()) promptBuilder.append("Title: $bookmarkTitle. ")
                if (!bookmarkDescription.isNullOrBlank()) promptBuilder.append("Description: $bookmarkDescription. ")
            }
            
            // Add audio
            if (audioData != null) {
                Log.d(TAG, "Processing audio")
                parts.add(Part.fromBytes(audioData.bytes, audioData.mimeType))
                promptBuilder.append("Transcribe the provided audio recording verbatim and analyze its content. ")
            }
            
            // Add user text
            if (!userText.isNullOrBlank()) {
                promptBuilder.append("Incorporate the following user's note into your analysis: \"$userText\". ")
            }
            
            // Add timestamp
            val currentDateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            promptBuilder.append("The current date and time is $currentDateTime. Use this context to determine if events are past or future. ")
            promptBuilder.append("\n\nBased on all the provided content, generate the final JSON response.")
            
            parts.add(0, Part.fromText(promptBuilder.toString()))
            
            val multimodalContent = Content.fromParts(*parts.toTypedArray())
            Log.d(TAG, "Sending request to Gemini API with ${parts.size} parts")
            
            val response = localClient.models.generateContent(currentModel(), multimodalContent, config)
            val textResponse = response.text() ?: throw Exception("Empty response from API")

            parseJsonResponse(textResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing memory", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Gemini client not initialized")
        )
        
        try {
            val tools = buildGeminiTools(toolExecutor)
            val afcConfig = AutomaticFunctionCallingConfig.builder()
                .maximumRemoteCalls(currentMaxToolCalls())
                .build()
            
            val configBuilder = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            
            if (tools != null) {
                configBuilder.tools(tools).automaticFunctionCalling(afcConfig)
            }
            
            val config = configBuilder.build()
            
            val fullPrompt = if (conversationHistory.isNotEmpty()) {
                "Previous conversation:\n$conversationHistory\n\nUser: $message"
            } else {
                message
            }
            
            val response = localClient.models.generateContent(currentModel(), fullPrompt, config)
            val responseText = response.text() ?: "I'm sorry, I couldn't generate a response."

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Gemini client not initialized")
        )
        
        try {
            val tools = buildGeminiTools(toolExecutor)
            val afcConfig = AutomaticFunctionCallingConfig.builder()
                .maximumRemoteCalls(currentMaxToolCalls())
                .build()
            
            val configBuilder = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            
            if (tools != null) {
                configBuilder.tools(tools).automaticFunctionCalling(afcConfig)
            }
            
            val config = configBuilder.build()
            
            val parts = mutableListOf<Part>()
            
            // Add history
            if (conversationHistory.isNotEmpty()) {
                parts.add(Part.fromText("Previous conversation:\n$conversationHistory\n\n"))
            }
            
            // Add audio
            if (audioData != null) {
                parts.add(Part.fromBytes(audioData.bytes, audioData.mimeType))
                parts.add(Part.fromText("The user sent an audio message. Please transcribe and respond to it."))
            }
            
            // Add image
            if (imageData != null) {
                parts.add(Part.fromBytes(imageData.bytes, imageData.mimeType))
                parts.add(Part.fromText("The user sent an attachment. Please analyze it."))
            }
            
            // Add message
            if (!message.isNullOrBlank()) {
                parts.add(Part.fromText("User: $message"))
            }
            
            val multimodalContent = Content.fromParts(*parts.toTypedArray())
            val response = localClient.models.generateContent(currentModel(), multimodalContent, config)
            val responseText = response.text() ?: "I'm sorry, I couldn't generate a response."

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message with media", e)
            Result.failure(e)
        }
    }
    
    override fun sendMessageStream(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
        val localClient = client
        if (localClient == null) {
            emit(ChatStreamEvent.Error(IllegalStateException("Gemini client not initialized")))
            return@flow
        }
        
        try {
            val tools = buildGeminiTools(toolExecutor)
            val afcConfig = AutomaticFunctionCallingConfig.builder()
                .maximumRemoteCalls(currentMaxToolCalls())
                .build()
            
            val thinkingConfig = ThinkingConfig.builder()
                .thinkingBudget(21888)
                .build()
            
            val configBuilder = GenerateContentConfig.builder()
                .thinkingConfig(thinkingConfig)
                .maxOutputTokens(65536)
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            
            if (tools != null) {
                configBuilder.tools(tools).automaticFunctionCalling(afcConfig)
            }
            
            val config = configBuilder.build()
            
            val fullPrompt = if (conversationHistory.isNotEmpty()) {
                "Previous conversation:\n$conversationHistory\n\nUser: $message"
            } else {
                message
            }
            
            val responseStream = localClient.models.generateContentStream(currentModel(), fullPrompt, config)

            var chunkCount = 0
            var emittedChars = 0
            for (res in responseStream) {
                chunkCount++
                val candidates = res.candidates().orElse(null)
                if (candidates.isNullOrEmpty()) continue

                val content = candidates[0].content().orElse(null) ?: continue
                val parts = content.parts().orElse(null) ?: continue

                for (part in parts) {
                    val textValue = part.text().orElse(null)
                    if (textValue != null && textValue.isNotEmpty()) {
                        emittedChars += textValue.length
                        emit(ChatStreamEvent.Content(textValue))
                    }
                }
            }
            Log.i(TAG, "Stream complete: chunks=$chunkCount, chars=$emittedChars")

            emit(ChatStreamEvent.Done)
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming", e)
            emit(ChatStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun sendMessageWithMediaStream(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
        val localClient = client
        if (localClient == null) {
            emit(ChatStreamEvent.Error(IllegalStateException("Gemini client not initialized")))
            return@flow
        }
        
        try {
            val tools = buildGeminiTools(toolExecutor)
            val afcConfig = AutomaticFunctionCallingConfig.builder()
                .maximumRemoteCalls(currentMaxToolCalls())
                .build()
            
            val thinkingConfig = ThinkingConfig.builder()
                .thinkingBudget(21888)
                .build()
            
            val configBuilder = GenerateContentConfig.builder()
                .thinkingConfig(thinkingConfig)
                .maxOutputTokens(65536)
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            
            if (tools != null) {
                configBuilder.tools(tools).automaticFunctionCalling(afcConfig)
            }
            
            val config = configBuilder.build()
            
            val parts = mutableListOf<Part>()
            
            if (conversationHistory.isNotEmpty()) {
                parts.add(Part.fromText("Previous conversation:\n$conversationHistory\n\n"))
            }
            
            if (audioData != null) {
                parts.add(Part.fromBytes(audioData.bytes, audioData.mimeType))
                parts.add(Part.fromText("The user sent an audio message. Please transcribe and respond to it."))
            }
            
            if (imageData != null) {
                parts.add(Part.fromBytes(imageData.bytes, imageData.mimeType))
                parts.add(Part.fromText("The user sent an attachment. Please analyze it."))
            }
            
            if (!message.isNullOrBlank()) {
                parts.add(Part.fromText("User: $message"))
            }
            
            val multimodalContent = Content.fromParts(*parts.toTypedArray())
            val responseStream = localClient.models.generateContentStream(currentModel(), multimodalContent, config)
            
            for (res in responseStream) {
                val candidates = res.candidates().orElse(null)
                if (candidates.isNullOrEmpty()) continue
                
                val content = candidates[0].content().orElse(null) ?: continue
                val resParts = content.parts().orElse(null) ?: continue
                
                for (part in resParts) {
                    val textValue = part.text().orElse(null)
                    if (textValue != null && textValue.isNotEmpty()) {
                        emit(ChatStreamEvent.Content(textValue))
                    }
                }
            }
            
            emit(ChatStreamEvent.Done)
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming with media", e)
            emit(ChatStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateUserContext(memoryText: String): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Gemini client not initialized")
        )
        
        try {
            val prompt = "Analyze the following user memories and create a detailed user context profile. " +
                    "Summarize their interests, habits, important events, and preferences based on these memories. " +
                    "This profile will be used to personalize future interactions.\n\nMemories:\n$memoryText"
            
            val response = localClient.models.generateContent(currentModel(), prompt, null)
            val summary = response.text() ?: "Could not generate summary."
            
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating user context", e)
            Result.failure(e)
        }
    }
    
    // Helper: Build Gemini tools from ToolExecutor
    private fun buildGeminiTools(toolExecutor: ToolExecutor?): Tool? {
        if (toolExecutor == null) return null

        return try {
            // Get the tool methods from ChatTools via reflection
            val queryDatabaseMethod = com.example.memgallery.data.remote.ChatTools::class.java.getMethod(
                "queryDatabase", String::class.java, String::class.java, String::class.java
            )
            val webSearchMethod = com.example.memgallery.data.remote.ChatTools::class.java.getMethod(
                "webSearch", String::class.java
            )
            val displayMemoriesMethod = com.example.memgallery.data.remote.ChatTools::class.java.getMethod(
                "displayMemoriesById", String::class.java
            )
            val getDatabaseSchemaMethod = com.example.memgallery.data.remote.ChatTools::class.java.getMethod(
                "getDatabaseSchema"
            )

            Tool.builder()
                .functions(queryDatabaseMethod, webSearchMethod, displayMemoriesMethod, getDatabaseSchemaMethod)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error building tools", e)
            null
        }
    }
    
    private fun parseJsonResponse(responseText: String): Result<AiAnalysisDto> {
        val cleanedResponseText = responseText
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        return try {
            Result.success(gson.fromJson(cleanedResponseText, AiAnalysisDto::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response", e)
            Result.failure(IllegalStateException(
                "Failed to parse AI analysis JSON: ${e.message}. Raw response: $cleanedResponseText", e
            ))
        }
    }
}
