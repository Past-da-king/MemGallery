package com.example.memgallery.data.remote.ai

import android.util.Log
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeepSeekProvider"

/**
 * DeepSeek through its official API (https://api.deepseek.com, OpenAI format) with the user's own key.
 * Text only: DeepSeek takes no audio, and pictures reach it as text from the "Images" provider
 * (see [com.example.memgallery.data.remote.MediaPreprocessor]). Web search is a tool the model calls.
 */
@Singleton
class DeepSeekProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : AIProvider {

    override val providerName: String = "DeepSeek"

    private val loop = OpenAiChatLoop(
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // thinking mode can be slow
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    )

    private var apiKey: String? = null

    override fun initialize(apiKey: String) { this.apiKey = apiKey }
    override fun isEnabled(): Boolean = !apiKey.isNullOrBlank()
    override fun disable() { apiKey = null }

    private suspend fun config(): OpenAiConfig {
        val key = apiKey ?: settingsRepository.deepSeekApiKeyFlow.first().orEmpty()
        val maxCalls = try { settingsRepository.maxToolCallsFlow.first() } catch (e: Exception) { 5 }
        return DeepSeekConfig.chat(
            apiKey = key,
            model = settingsRepository.deepSeekModelIdFlow.first(),
            thinking = settingsRepository.deepSeekThinkingFlow.first(),
            maxToolCalls = maxCalls
        )
    }

    /** Live model list from GET /models; also the key check. */
    suspend fun fetchModels(apiKey: String): Result<List<String>> =
        loop.listModels(DeepSeekConfig.chat(apiKey = apiKey, model = DeepSeekConfig.DEFAULT_MODEL, thinking = false, maxToolCalls = 1))

    override suspend fun validateApiKey(apiKey: String): Result<Boolean> =
        fetchModels(apiKey).map { true }

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
    ): Result<AiAnalysisDto> {
        val prompt = buildString {
            if (bookmarkUrl != null) {
                appendLine("Analyze this bookmark: $bookmarkUrl")
                if (!bookmarkTitle.isNullOrBlank()) appendLine("Title: $bookmarkTitle")
                if (!bookmarkDescription.isNullOrBlank()) appendLine("Description: $bookmarkDescription")
            }
            if (!userText.isNullOrBlank()) appendLine("User note: $userText")
            if (imageDataList.isNotEmpty()) appendLine("(${imageDataList.size} image(s) were attached but this model cannot see them.)")
            appendLine()
            appendLine("Return ONLY one JSON object that follows this schema:")
            appendLine(responseSchemaJson)
        }
        return loop.completeJson(config(), systemPrompt, prompt)
    }

    private fun messagesFor(history: String, user: String, systemPrompt: String): JSONArray {
        val m = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        if (history.isNotBlank()) {
            m.put(JSONObject().put("role", "user").put("content", "Earlier in this chat:\n$history"))
            m.put(JSONObject().put("role", "assistant").put("content", "Understood."))
        }
        m.put(JSONObject().put("role", "user").put("content", user))
        return m
    }

    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = loop.run(config(), messagesFor(conversationHistory, message, systemPrompt), toolExecutor)

    /** Text only. By the time this runs, media has been turned into text by the voice and image providers. */
    override suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> {
        if (audioData != null || imageData != null) Log.w(TAG, "Raw media reached DeepSeek; sending the text only")
        return sendMessage(conversationHistory, message ?: "(The user sent media that could not be read as text.)", systemPrompt, toolExecutor)
    }

    // The tool loop needs whole responses, so "streaming" is one block (same as the other tool-calling providers here).
    override fun sendMessageStream(conversationHistory: String, message: String, systemPrompt: String, toolExecutor: ToolExecutor?): Flow<ChatStreamEvent> =
        flow {
            emit(ChatStreamEvent.Thinking("Thinking..."))
            sendMessage(conversationHistory, message, systemPrompt, toolExecutor)
                .onSuccess { emit(ChatStreamEvent.Content(it)); emit(ChatStreamEvent.Done) }
                .onFailure { emit(ChatStreamEvent.Error(it)) }
        }.flowOn(Dispatchers.IO)

    override fun sendMessageWithMediaStream(conversationHistory: String, message: String?, audioData: AudioData?, imageData: ImageData?, systemPrompt: String, toolExecutor: ToolExecutor?): Flow<ChatStreamEvent> =
        flow {
            emit(ChatStreamEvent.Thinking("Thinking..."))
            sendMessageWithMedia(conversationHistory, message, audioData, imageData, systemPrompt, toolExecutor)
                .onSuccess { emit(ChatStreamEvent.Content(it)); emit(ChatStreamEvent.Done) }
                .onFailure { emit(ChatStreamEvent.Error(it)) }
        }.flowOn(Dispatchers.IO)

    override suspend fun generateUserContext(memoryText: String): Result<String> = withContext(Dispatchers.IO) {
        sendMessage(
            "",
            "Write a short profile (interests, routines, people, projects) of the person these memories belong to:\n\n$memoryText",
            "You write concise, factual user profiles from a person's saved memories.",
            null
        )
    }

    override suspend fun transcribeAudio(audio: AudioData): Result<String> =
        Result.failure(UnsupportedOperationException("DeepSeek has no audio input. Pick Gemini or Groq for Voice transcription."))

    override suspend fun describeImages(images: List<ImageData>): Result<String> =
        Result.failure(UnsupportedOperationException("DeepSeek has no image input. Pick Gemini or Groq for Images."))
}
