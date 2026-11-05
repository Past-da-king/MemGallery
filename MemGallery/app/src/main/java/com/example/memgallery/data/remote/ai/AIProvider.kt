package com.example.memgallery.data.remote.ai

import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.remote.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for all AI providers (Gemini, Groq, etc.)
 * This abstraction allows switching between providers seamlessly.
 */
interface AIProvider {
    
    /**
     * Provider identifier for logging and debugging
     */
    val providerName: String
    
    /**
     * Initialize the provider with an API key
     */
    fun initialize(apiKey: String)
    
    /**
     * Check if the provider is initialized and ready
     */
    fun isEnabled(): Boolean
    
    /**
     * Disable the provider
     */
    fun disable()
    
    /**
     * Validate an API key before saving
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean>
    
    /**
     * Process a memory with multimodal content (images, audio, text)
     * Returns structured AI analysis
     */
    suspend fun processMemory(
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
    ): Result<AiAnalysisDto>
    
    /**
     * Send a text-only chat message
     */
    suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String>
    
    /**
     * Send a message with media attachments
     */
    suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String>
    
    /**
     * Stream a chat response
     */
    fun sendMessageStream(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent>
    
    /**
     * Stream a chat response with media
     */
    fun sendMessageWithMediaStream(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent>
    
    /**
     * Generate a summary of user memories for context
     */
    suspend fun generateUserContext(memoryText: String): Result<String>

    /**
     * Turn a voice recording into text (the "Voice transcription" job). The default asks a multimodal model;
     * providers with a real speech endpoint override it.
     */
    suspend fun transcribeAudio(audio: AudioData): Result<String> =
        sendMessageWithMedia(
            conversationHistory = "",
            message = "Transcribe this audio recording verbatim. Output only the transcript, nothing else.",
            audioData = audio,
            imageData = null,
            systemPrompt = "You are a precise speech transcription engine.",
            toolExecutor = null
        )

    /** Describe pictures and read any text in them (the "Images" job), so a text-only main brain can use them. */
    suspend fun describeImages(images: List<ImageData>): Result<String> {
        val parts = mutableListOf<String>()
        for ((i, img) in images.withIndex()) {
            val r = sendMessageWithMedia(
                conversationHistory = "",
                message = "Describe this image in detail for someone who cannot see it. Then write out, exactly, any text visible in it (OCR). Plain text only.",
                audioData = null,
                imageData = img,
                systemPrompt = "You describe images accurately and read text in them.",
                toolExecutor = null
            )
            r.onSuccess { parts += if (images.size > 1) "Image ${i + 1}: $it" else it }
            if (r.isFailure && parts.isEmpty()) return Result.failure(r.exceptionOrNull() ?: Exception("Image description failed"))
        }
        return Result.success(parts.joinToString("\n\n"))
    }
}

/**
 * Represents image data for AI processing
 */
data class ImageData(
    val bytes: ByteArray,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageData
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * Represents audio data for AI processing
 */
data class AudioData(
    val bytes: ByteArray,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioData
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * Interface for tool execution in chat
 * Allows provider-agnostic tool calling
 */
interface ToolExecutor {
    /**
     * Get the list of available tool definitions
     */
    fun getToolDefinitions(): List<ToolDefinition>
    
    /**
     * Execute a tool by name with given arguments
     */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String
}

/**
 * Definition of a tool for AI function calling
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterDef>
)

/**
 * Parameter definition for tools
 */
data class ParameterDef(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null
)

/**
 * Enum for available AI providers
 */
enum class AIProviderType {
    GEMINI,
    GROQ,
    LOCAL,
    OPENAI_COMPATIBLE,
    DEEPSEEK
}
