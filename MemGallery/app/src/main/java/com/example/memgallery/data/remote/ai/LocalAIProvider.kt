package com.example.memgallery.data.remote.ai

import android.content.Context
import android.util.Log
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalAIProvider"

@Singleton
class LocalAIProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson
) : AIProvider {

    override val providerName: String = "Local (Gemma)"

    private var llmInference: LlmInference? = null
    private var currentModelPath: String? = null

    // Initialize with a path, not just a key
    override fun initialize(apiKey: String) {
        // For Local provider, 'apiKey' might be the model path or ignored if we fetch from settings
        // We'll rely on settings to get the path
        Log.d(TAG, "Initialize called. Waiting for model path from settings...")
    }

    // Helper to ensure model is loaded
    private suspend fun ensureModelLoaded(): Boolean {
        val modelPath = settingsRepository.localModelPathFlow.first()
        if (modelPath.isNullOrBlank()) {
            Log.e(TAG, "No local model path set")
            return false
        }

        // Check if model path changed
        if (llmInference != null && currentModelPath != modelPath) {
             try {
                 llmInference?.close()
             } catch (e: Exception) {
                 Log.e(TAG, "Error closing previous model", e)
             }
             llmInference = null
        }

        if (llmInference != null && currentModelPath == modelPath) {
            return true
        }

        return try {
            val file = File(modelPath)
            if (!file.exists()) {
                Log.e(TAG, "Model file not found at $modelPath")
                return false
            }
            
            // Validate file extension
            val validExtensions = listOf("task", "tflite", "literlm")
            val extension = file.extension.lowercase()
            if (extension !in validExtensions) {
                Log.w(TAG, "Model file has extension '.$extension'. Expected: .task, .tflite, or .literlm. " +
                        "This may cause loading errors. File size: ${file.length() / 1024 / 1024}MB")
            } else {
                Log.i(TAG, "Model file: $modelPath (${file.length() / 1024 / 1024}MB, .$extension format)")
            }

            Log.i(TAG, "Loading local model from $modelPath...")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(8192) // Increased from 1024 to support larger context + output
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelPath
            Log.i(TAG, "Local model loaded successfully")
            true
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("Flatbuffer") == true -> 
                    "Model format error: The file is not a valid MediaPipe model. " +
                    "Download a .task file from huggingface.co/litert-community/Gemma3-1B-IT"
                e.message?.contains("RET_CHECK") == true ->
                    "Model initialization failed. Ensure you have a compatible .task model file."
                else -> "Failed to load model: ${e.message}"
            }
            Log.e(TAG, errorMsg, e)
            false
        }
    }

    override fun isEnabled(): Boolean {
        // Technically enabled if we can load the model
        return true 
    }

    override fun disable() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing model", e)
        }
        llmInference = null
        currentModelPath = null
    }

    override suspend fun validateApiKey(apiKey: String): Result<Boolean> {
        // For local, we validate the file path existence
        return try {
            if (apiKey.isBlank()) return Result.failure(Exception("Path is empty"))
            val file = File(apiKey)
            val validExtensions = setOf("bin", "literlm", "tflite", "task")
            if (file.exists() && file.extension.lowercase() in validExtensions) {
                Result.success(true)
            } else {
                Result.failure(Exception("Invalid model file. Supported: .bin, .literlm, .tflite, .task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Chat Features ---

    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!ensureModelLoaded()) {
            return@withContext Result.failure(IllegalStateException("Local model not loaded or invalid."))
        }

        try {
            // Construct prompt manually. Gemma style:
            // <start_of_turn>user\nPROMPT<end_of_turn>\n<start_of_turn>model\n
            val fullPrompt = buildGemmaPrompt(conversationHistory, message, systemPrompt)
            
            val response = llmInference?.generateResponse(fullPrompt)
            if (response != null) {
                Result.success(response)
            } else {
                Result.failure(Exception("Empty response from local model"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Simple prompt builder for Gemma
    // Adapt based on specific model instruction format
    // Simple prompt builder for Gemma
    // Adapt based on specific model instruction format
    private fun buildGemmaPrompt(history: String, message: String, system: String): String {
        val sb = StringBuilder()
        // System instruction is often handled by 'user' turn in simple templates if 'system' role isn't supported
        // But Gemma usually follows <start_of_turn>user ... <end_of_turn>
        
        // Inject system prompt into first user message or as context
        val actualSystem = if (system.isNotBlank()) "System Instruction:\n$system\n\n" else ""
        
        // Add history
        if (history.isNotBlank()) {
            sb.append(history).append("\n")
        }
        
        sb.append("<start_of_turn>user\n")
        sb.append(actualSystem)
        sb.append(message)
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        
        return sb.toString()
    }

    override fun sendMessageStream(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = callbackFlow {
        if (!ensureModelLoaded()) {
            trySend(ChatStreamEvent.Error(IllegalStateException("Local model not loaded")))
            close()
            return@callbackFlow
        }

        val fullPrompt = buildGemmaPrompt(conversationHistory, message, systemPrompt)
        
        try {
            // Note: True streaming with ProgressListener isn't available in this version.
            // Fall back to synchronous generation and emit full response.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = llmInference?.generateResponse(fullPrompt)
                if (response != null) {
                    trySend(ChatStreamEvent.Content(response))
                    trySend(ChatStreamEvent.Done)
                } else {
                    trySend(ChatStreamEvent.Error(Exception("Empty response from local model")))
                }
            }
            close()
        } catch (e: Exception) {
            trySend(ChatStreamEvent.Error(e))
            close()
        }
        
        awaitClose { /* cleanup if needed */ }
    }

    // --- Multimodal (Stubbed / Text-Only fallback) ---
    // Tasks-GenAI 0.10.14 is primarily Text-to-Text. 
    // Image support is in `ImageSegmenter` or other tasks, BUT `LlmInference` is strictly text generation from text prompt.
    // unless using a VLM model which accepts image embeddings? 
    // For now, we fall back to text-only mode (processing text parts).
    
    override suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> {
        // WARN: Local LlmInference (0.10.14) typically does NOT support direct Image inputs unless using a specialized VLM api.
        // We will pass text only.
        return sendMessage(
            conversationHistory, 
            message ?: "[Media message - Image/Audio ignored in local execution]", 
            systemPrompt, 
            toolExecutor
        )
    }

    override fun sendMessageWithMediaStream(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> {
        return sendMessageStream(
            conversationHistory, 
            message ?: "[Media message ignored]", 
            systemPrompt, 
            toolExecutor
        )
    }

    // --- Analysis ---

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
        if (!ensureModelLoaded()) {
            return@withContext Result.failure(IllegalStateException("Local model not loaded"))
        }

        // Build a pure text prompt for analysis
        val sb = StringBuilder()
        
        // IGNORE generic systemPrompt (too large for local model).
        // Use a Specific Memory Analysis Prompt for Local AI:
        // Concisely mirrors the full cloud system prompt requirements to ensure JSON parity.
        sb.append("You are an expert memory assistant. Analyze the content and generate a structured analysis.\n")
        sb.append("Guidelines:\n")
        sb.append("- Title: Concise, descriptive, max 6 words.\n")
        sb.append("- Summary: Capture key details and context.\n")
        sb.append("- Tags: 3-5 relevant categorical tags.\n")
        sb.append("- Image Analysis: (Empty for text-only input)\n")
        sb.append("- Audio Transcription: (Empty for text-only input)\n")
        sb.append("- Actions: List any actionable tasks (TODO, EVENT, REMINDER) found.\n")
        sb.append("- Suggested Collections: specific collection names.\n")
        sb.append("\nRespond ONLY with valid JSON matching this schema:\n$responseSchemaJson\n\n")
        
        if (userText != null) sb.append("User Text: $userText\n")
        if (bookmarkUrl != null) sb.append("Bookmark: $bookmarkUrl ($bookmarkTitle)\n")
        if (audioData != null) sb.append("[Audio attached - ignored in local mode]\n")
        if (imageDataList.isNotEmpty()) sb.append("[Images attached - ignored in local mode]\n")
        
        // Gemma Prompt Wrapper
        val fullPrompt = "<start_of_turn>user\n${sb.toString()}<end_of_turn>\n<start_of_turn>model\n"
        
        try {
            Log.d(TAG, "Applying Prompt: $fullPrompt")
            val startTime = System.currentTimeMillis()
            val response = llmInference?.generateResponse(fullPrompt) ?: ""
            val duration = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "AI Generation took ${duration}ms")
            Log.d(TAG, "Raw AI Output:\n$response")

            // Clean markdown
            val json = response.replace("```json", "").replace("```", "").trim()
            val dto = gson.fromJson(json, AiAnalysisDto::class.java)
            Result.success(dto)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateUserContext(memoryText: String): Result<String> {
         if (!ensureModelLoaded()) return Result.failure(IllegalStateException("Local model not loaded"))
         
         val prompt = "Summarize these memories into a profile:\n$memoryText"
         val fullPrompt = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
         
         return try {
             val response = llmInference?.generateResponse(fullPrompt) ?: ""
             Result.success(response)
         } catch (e: Exception) {
             Result.failure(e)
         }
    }
}
