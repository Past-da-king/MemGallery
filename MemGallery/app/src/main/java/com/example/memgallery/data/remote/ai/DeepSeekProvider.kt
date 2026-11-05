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
 * Reads text and pictures (deepseek-flash takes image_url parts in user messages); takes no audio, so voice notes
 * reach it as a transcript from the Voice provider (see [com.example.memgallery.data.remote.MediaPreprocessor]).
 * Web search is a tool the model calls.
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

    private suspend fun config(withImages: Boolean = false): OpenAiConfig {
        val key = apiKey ?: settingsRepository.deepSeekApiKeyFlow.first().orEmpty()
        val maxCalls = try { settingsRepository.maxToolCallsFlow.first() } catch (e: Exception) { 5 }
        return DeepSeekConfig.chat(
            apiKey = key,
            // Pictures need the vision-capable model whatever text model is picked (Pro is text only).
            model = if (withImages) DeepSeekConfig.VISION_MODEL else settingsRepository.deepSeekModelIdFlow.first(),
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
            if (imageDataList.isNotEmpty()) appendLine("(${imageDataList.size} image(s) are attached above.)")
            appendLine()
            appendLine("Return ONLY one JSON object that follows this schema:")
            appendLine(responseSchemaJson)
        }
        return loop.completeJson(config(imageDataList.isNotEmpty()), systemPrompt, prompt, imageDataList)
    }

    private fun messagesFor(history: String, user: String, systemPrompt: String, images: List<ImageData> = emptyList()): JSONArray {
        val m = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        if (history.isNotBlank()) {
            m.put(JSONObject().put("role", "user").put("content", "Earlier in this chat:\n$history"))
            m.put(JSONObject().put("role", "assistant").put("content", "Understood."))
        }
        m.put(JSONObject().put("role", "user").put("content", DeepSeekConfig.userContent(user, images)))
        return m
    }

    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = loop.run(config(), messagesFor(conversationHistory, message, systemPrompt), toolExecutor)

    /** Pictures go straight in as image_url parts. Audio never reaches here: the Voice provider has turned it into text. */
    override suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> {
        if (audioData != null) Log.w(TAG, "Raw audio reached DeepSeek; it has no audio input, dropping it")
        val images = listOfNotNull(imageData)
        val text = message ?: if (images.isNotEmpty()) "Describe this picture." else "(The user sent media that could not be read as text.)"
        return loop.run(config(images.isNotEmpty()), messagesFor(conversationHistory, text, systemPrompt, images), toolExecutor)
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

    /** The Images job: all pictures in one request, described and OCR'd. */
    override suspend fun describeImages(images: List<ImageData>): Result<String> =
        loop.run(
            config(true),
            messagesFor("", "Describe each image in detail for someone who cannot see it. Then write out, exactly, any text visible in it (OCR). Plain text only.",
                "You describe images accurately and read text in them.", images),
            null
        )
}
