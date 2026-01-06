package com.example.memgallery.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.remote.ai.AIProviderFactory
import com.example.memgallery.data.remote.ai.AudioData
import com.example.memgallery.data.remote.ai.ChatToolsExecutor
import com.example.memgallery.data.remote.ai.ImageData
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton


sealed class ChatStreamEvent {
    data class Thinking(val text: String) : ChatStreamEvent()
    data class Content(val text: String) : ChatStreamEvent()
    data object Done : ChatStreamEvent()
    data class Error(val error: Throwable) : ChatStreamEvent()
}

private const val TAG = "ChatGeminiService"

/**
 * Service for chat interactions with AI.
 * Now uses AIProviderFactory to support both Gemini and Groq providers.
 */
@Singleton
class ChatGeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerFactory: AIProviderFactory,
    private val settingsRepository: SettingsRepository,
    private val memoryDao: MemoryDao,
    private val collectionDao: CollectionDao,
    private val chatDao: ChatDao,
    private val taskDao: TaskDao
) {
    private val toolExecutor = ChatToolsExecutor()

    init {
        // Initialize ChatTools with DAOs
        ChatTools.memoryDao = memoryDao
        ChatTools.collectionDao = collectionDao
        ChatTools.taskDao = taskDao
    }

    /**
     * Initialize the current provider.
     * Kept for backward compatibility.
     */
    fun initialize(apiKey: String) {
        kotlinx.coroutines.runBlocking {
            providerFactory.initializeCurrentProvider()
            // Also initialize search client for ChatTools if using Gemini
            try {
                val provider = providerFactory.getProvider()
                if (provider.providerName == "Gemini") {
                    // Initialize search client for web search tool
                    val searchClient = com.google.genai.Client.builder()
                        .apiKey(apiKey)
                        .build()
                    ChatTools.searchClient = searchClient
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing search client", e)
            }
        }
    }

    /**
     * Generate user context from memories.
     */
    suspend fun generateUserContext(): Result<String> = withContext(Dispatchers.IO) {
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("Provider not initialized"))
        }

        if (!provider.isEnabled()) {
            providerFactory.initializeCurrentProvider()
        }

        try {
            // Get only the most recent 50 memories to avoid token bloat
            val memories = memoryDao.getAllMemories().first().takeLast(50)
            if (memories.isEmpty()) return@withContext Result.success("No memories available yet.")

            val memoryText = memories.joinToString("\n\n") { memory ->
                "Title: ${memory.aiTitle}\nSummary: ${memory.aiSummary}\nTags: ${memory.aiTags?.joinToString()}"
            }

            val result = provider.generateUserContext(memoryText)
            
            result.onSuccess { summary ->
                settingsRepository.saveUserContextSummary(summary)
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error generating user context", e)
            Result.failure(e)
        }
    }

    /**
     * Send a text message to the chat.
     */
    suspend fun sendMessage(chatId: Int, message: String): Result<String> = withContext(Dispatchers.IO) {
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            val apiKey = providerFactory.getApiKey()
            if (!apiKey.isNullOrBlank()) {
                providerFactory.initializeCurrentProvider()
                providerFactory.getProvider()
            } else {
                return@withContext Result.failure(IllegalStateException("Provider not configured"))
            }
        }

        try {
            Log.d(TAG, "Sending message using ${provider.providerName}: $message")
            
            // Get Chat History (last 10 messages to prevent token bloat)
            val history = chatDao.getMessagesForChat(chatId).first().takeLast(10)
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            
            // Build System Instruction
            val systemPrompt = buildSystemPrompt()
            
            // Clear displayed memory IDs before each request
            ChatTools.clearDisplayedMemoryIds()
            
            Log.i(TAG, "Sending message with ${provider.providerName}")
            
            val result = provider.sendMessage(
                conversationHistory = historyText,
                message = message,
                systemPrompt = systemPrompt,
                toolExecutor = toolExecutor
            )
            
            result.onSuccess { responseText ->
                Log.d(TAG, "Received response: $responseText")
                chatDao.insertMessage(ChatMessageEntity(
                    chatId = chatId, 
                    role = "model", 
                    content = responseText,
                    displayedMemoryIds = ChatTools.lastDisplayedMemoryIds
                ))
            }
            
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage", e)
            Result.failure(e)
        }
    }

    /**
     * Send a message with optional media attachments.
     */
    suspend fun sendMessageWithMedia(
        chatId: Int,
        message: String?,
        audioUri: String? = null,
        imageUri: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            val apiKey = providerFactory.getApiKey()
            if (!apiKey.isNullOrBlank()) {
                providerFactory.initializeCurrentProvider()
                providerFactory.getProvider()
            } else {
                return@withContext Result.failure(IllegalStateException("Provider not configured"))
            }
        }

        try {
            Log.d(TAG, "Sending message with media - audio: $audioUri, image: $imageUri")
            
            // Get Chat History (last 20 messages)
            val history = chatDao.getMessagesForChat(chatId).first().takeLast(20)
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            
            // Build System Instruction
            val systemPrompt = buildSystemPrompt()
            
            // Clear displayed memory IDs
            ChatTools.clearDisplayedMemoryIds()
            
            // Convert audio URI to AudioData
            val audioData = if (audioUri != null) {
                val audioBytes = getBytesFromUri(audioUri)
                if (audioBytes != null) {
                    val parsedUri = Uri.parse(audioUri)
                    val mimeType = context.contentResolver.getType(parsedUri) ?: "audio/m4a"
                    AudioData(audioBytes, mimeType)
                } else null
            } else null
            
            // Convert image URI to ImageData
            val imageData = if (imageUri != null) {
                val mediaBytes = getBytesFromUri(imageUri)
                if (mediaBytes != null) {
                    val parsedUri = Uri.parse(imageUri)
                    val mimeType = context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                    
                    if (mimeType.startsWith("image/")) {
                        val bitmap = BitmapFactory.decodeByteArray(mediaBytes, 0, mediaBytes.size)
                        if (bitmap != null) {
                            val resizedBitmap = resizeBitmap(bitmap, 1024)
                            val outputStream = ByteArrayOutputStream()
                            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                            ImageData(outputStream.toByteArray(), "image/jpeg")
                        } else null
                    } else {
                        // For PDFs and other documents
                        ImageData(mediaBytes, mimeType)
                    }
                } else null
            } else null
            
            Log.i(TAG, "Sending message with media using ${provider.providerName}")
            
            val result = provider.sendMessageWithMedia(
                conversationHistory = historyText,
                message = message,
                audioData = audioData,
                imageData = imageData,
                systemPrompt = systemPrompt,
                toolExecutor = toolExecutor
            )
            
            result.onSuccess { responseText ->
                Log.d(TAG, "Received response: $responseText")
                chatDao.insertMessage(ChatMessageEntity(
                    chatId = chatId, 
                    role = "model", 
                    content = responseText,
                    displayedMemoryIds = ChatTools.lastDisplayedMemoryIds
                ))
            }
            
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessageWithMedia", e)
            Result.failure(e)
        }
    }

    /**
     * Stream a chat response.
     */
    fun sendMessageStream(chatId: Int, message: String): Flow<ChatStreamEvent> = flow {
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error(IllegalStateException("Provider not initialized")))
            return@flow
        }
        
        if (!provider.isEnabled()) {
            val initialized = kotlinx.coroutines.runBlocking { providerFactory.initializeCurrentProvider() }
            if (!initialized) {
                emit(ChatStreamEvent.Error(IllegalStateException("Provider not configured")))
                return@flow
            }
        }

        try {
            Log.d(TAG, "Starting streaming message for chat: $chatId using ${provider.providerName}")
            
            // Get Chat History (last 20 messages)
            val history = chatDao.getMessagesForChat(chatId).first().takeLast(20)
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            
            // Build System Instruction
            val systemPrompt = buildSystemPrompt()
            
            // Clear displayed memory IDs
            ChatTools.clearDisplayedMemoryIds()
            
            val fullResponseBuilder = StringBuilder()
            
            provider.sendMessageStream(
                conversationHistory = historyText,
                message = message,
                systemPrompt = systemPrompt,
                toolExecutor = toolExecutor
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Content -> {
                        fullResponseBuilder.append(event.text)
                        emit(event)
                    }
                    is ChatStreamEvent.Done -> {
                        // Save full response to DB
                        val finalResponse = fullResponseBuilder.toString()
                        if (finalResponse.isNotEmpty()) {
                            chatDao.insertMessage(ChatMessageEntity(
                                chatId = chatId, 
                                role = "model", 
                                content = finalResponse,
                                displayedMemoryIds = ChatTools.lastDisplayedMemoryIds
                            ))
                        }
                        emit(event)
                    }
                    else -> emit(event)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessageStream", e)
            emit(ChatStreamEvent.Error(e))
        }
    }

    /**
     * Stream a chat response with media.
     */
    fun sendMessageWithMediaStream(chatId: Int, message: String?, audioUri: String?, imageUri: String?): Flow<ChatStreamEvent> = flow {
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error(IllegalStateException("Provider not initialized")))
            return@flow
        }
        
        if (!provider.isEnabled()) {
            val initialized = kotlinx.coroutines.runBlocking { providerFactory.initializeCurrentProvider() }
            if (!initialized) {
                emit(ChatStreamEvent.Error(IllegalStateException("Provider not configured")))
                return@flow
            }
        }

        try {
            Log.d(TAG, "Starting streaming with media: audio=$audioUri, image=$imageUri")
            
            // Get Chat History (last 20 messages)
            val history = chatDao.getMessagesForChat(chatId).first().takeLast(20)
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            
            // Build System Instruction
            val systemPrompt = buildSystemPrompt()
            
            // Clear displayed memory IDs
            ChatTools.clearDisplayedMemoryIds()
            
            // Convert media
            val audioData = audioUri?.let { uri ->
                getBytesFromUri(uri)?.let { bytes ->
                    val mimeType = context.contentResolver.getType(Uri.parse(uri)) ?: "audio/m4a"
                    AudioData(bytes, mimeType)
                }
            }
            
            val imageData = imageUri?.let { uri ->
                getBytesFromUri(uri)?.let { bytes ->
                    val parsedUri = Uri.parse(uri)
                    val mimeType = context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                    if (mimeType.startsWith("image/")) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                            val resizedBitmap = resizeBitmap(bitmap, 1024)
                            val outputStream = ByteArrayOutputStream()
                            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                            ImageData(outputStream.toByteArray(), "image/jpeg")
                        }
                    } else {
                        ImageData(bytes, mimeType)
                    }
                }
            }
            
            val fullResponseBuilder = StringBuilder()
            
            provider.sendMessageWithMediaStream(
                conversationHistory = historyText,
                message = message,
                audioData = audioData,
                imageData = imageData,
                systemPrompt = systemPrompt,
                toolExecutor = toolExecutor
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Content -> {
                        fullResponseBuilder.append(event.text)
                        emit(event)
                    }
                    is ChatStreamEvent.Done -> {
                        val finalResponse = fullResponseBuilder.toString()
                        if (finalResponse.isNotEmpty()) {
                            chatDao.insertMessage(ChatMessageEntity(
                                chatId = chatId, 
                                role = "model", 
                                content = finalResponse,
                                displayedMemoryIds = ChatTools.lastDisplayedMemoryIds
                            ))
                        }
                        emit(event)
                    }
                    else -> emit(event)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessageWithMediaStream", e)
            emit(ChatStreamEvent.Error(e))
        }
    }

    private suspend fun buildSystemPrompt(): String {
        val userContext = settingsRepository.userContextSummaryFlow.first()
        val provider = providerFactory.getProvider()
        
        // Use compact prompt if the provider is currently using a model with tight limits (like Maverick)
        val baseInstruction = if (provider.providerName == "Groq") {
            ChatSystemPrompt.generateCompact()
        } else {
            ChatSystemPrompt.generate()
        }
        
        return if (userContext.isNotBlank()) {
            "$baseInstruction\n\n## USER CONTEXT\n$userContext"
        } else {
            baseInstruction
        }
    }

    private fun getBytesFromUri(uriString: String): ByteArray? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> uri.path?.let { java.io.File(it).readBytes() }
            else -> null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight = (originalHeight * (maxDimension.toFloat() / originalWidth)).toInt()
            } else {
                resizedHeight = maxDimension
                resizedWidth = (originalWidth * (maxDimension.toFloat() / originalHeight)).toInt()
            }
        }

        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }
}
