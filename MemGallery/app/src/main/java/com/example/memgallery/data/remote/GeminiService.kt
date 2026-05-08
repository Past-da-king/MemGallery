package com.example.memgallery.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.remote.ai.AIProviderFactory
import com.example.memgallery.data.remote.ai.AudioData
import com.example.memgallery.data.remote.ai.ImageData
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiService"

/**
 * Service for processing memories with AI.
 * Now uses AIProviderFactory to support both Gemini and Groq providers.
 */
@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerFactory: AIProviderFactory,
    private val settingsRepository: SettingsRepository
) {
    private val responseSchemaJson = """
        {
          "type": "object",
          "properties": {
            "title": { "type": "string" },
            "summary": { "type": "string" },
            "tags": {
              "type": "array",
              "items": { "type": "string" }
            },
            "image_analysis": { "type": "string" },
            "audio_transcription": { "type": "string" },
            "actions": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "type": { "type": "string", "enum": ["EVENT", "TODO", "REMINDER"] },
                  "description": { "type": "string" },
                  "date": { "type": "string" },
                  "time": { "type": "string" }
                },
                "required": ["type", "description"]
              }
            },
            "suggested_collections": {
              "type": "array",
              "items": { "type": "string" }
            }
          },
          "required": ["title", "summary", "tags"]
        }
    """.trimIndent()

    /**
     * Initialize the current provider with given API key.
     * Kept for backward compatibility.
     */
    fun initialize(apiKey: String) {
        // Initialization is now handled by the provider factory
        // This is kept for backward compatibility during migration
        kotlinx.coroutines.runBlocking {
            providerFactory.initializeCurrentProvider()
        }
    }

    /**
     * Check if the current provider is enabled.
     */
    suspend fun isEnabled(): Boolean {
        return try {
            val provider = providerFactory.getProvider()
            provider.isEnabled()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Disable all providers.
     */
    fun disable() {
        providerFactory.disableAllProviders()
    }

    /**
     * Validate an API key for the current provider.
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val provider = providerFactory.getProvider()
            provider.validateApiKey(apiKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating API key", e)
            Result.failure(e)
        }
    }

    /**
     * Process a memory with multimodal content using the current AI provider.
     */
    suspend fun processMemory(
        imageUris: List<String> = emptyList(),
        audioUri: String?,
        userText: String?,
        bookmarkUrl: String?,
        bookmarkTitle: String?,
        bookmarkDescription: String?,
        bookmarkImageUrl: String?,
        existingCollections: List<String> = emptyList()
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        Log.d(TAG, "processMemory started")
        
        val provider = try {
            providerFactory.getProvider()
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("AI provider not initialized: ${e.message}"))
        }
        
        if (!provider.isEnabled()) {
            val initialized = providerFactory.initializeCurrentProvider()
            if (!initialized) {
                return@withContext Result.failure(IllegalStateException("AI provider not configured. Please set an API key."))
            }
        }

        try {
            // Build system prompt dynamically
            val baseSystemInstruction = context.resources.openRawResource(com.example.memgallery.R.raw.gemini_system_instructions)
                .bufferedReader().use { it.readText() }
            val userSystemPrompt = settingsRepository.userSystemPromptFlow.first()
            
            val collectionsInstruction = if (existingCollections.isNotEmpty()) {
                "\n\nExisting Collections:\n" + existingCollections.joinToString("\n") + 
                "\n\nIf this memory fits into one or more of these collections, include their names in the 'suggested_collections' array in the JSON response."
            } else ""

            val finalSystemInstruction = if (userSystemPrompt.isNotBlank()) {
                "$baseSystemInstruction\n\n---\n\nThese are user preferences that take precedence over the base instructions in case of conflict:\n\n$userSystemPrompt$collectionsInstruction"
            } else {
                "$baseSystemInstruction$collectionsInstruction"
            }

            // Convert image URIs to ImageData
            val imageDataList = mutableListOf<ImageData>()
            for (uriString in imageUris) {
                try {
                    val imageBytes = getBytesFromUri(uriString)
                    if (imageBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        val resizedBitmap = resizeBitmap(bitmap, 1024)
                        val outputStream = ByteArrayOutputStream()
                        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        val resizedImageBytes = outputStream.toByteArray()
                        
                        val parsedUri = Uri.parse(uriString)
                        val mimeType = if (parsedUri.scheme == "content") {
                            context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                        } else {
                            "image/jpeg"
                        }
                        imageDataList.add(ImageData(resizedImageBytes, mimeType))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image $uriString", e)
                }
            }

            // Convert audio URI to AudioData
            val audioData = if (audioUri != null) {
                Log.d(TAG, "Processing audio URI: $audioUri")
                val audioBytes = getBytesFromUri(audioUri)
                    ?: throw IOException("Could not read audio file from URI: $audioUri")
                val parsedUri = Uri.parse(audioUri)
                val mimeType = if (parsedUri.scheme == "content") {
                    context.contentResolver.getType(parsedUri) ?: "audio/m4a"
                } else {
                    "audio/m4a"
                }
                AudioData(audioBytes, mimeType)
            } else null

            Log.d(TAG, "Sending request to ${provider.providerName} with ${imageDataList.size} images")
            
            provider.processMemory(
                imageDataList = imageDataList,
                audioData = audioData,
                userText = userText,
                bookmarkUrl = bookmarkUrl,
                bookmarkTitle = bookmarkTitle,
                bookmarkDescription = bookmarkDescription,
                bookmarkImageUrl = bookmarkImageUrl,
                existingCollections = existingCollections,
                systemPrompt = finalSystemInstruction,
                responseSchemaJson = responseSchemaJson
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing memory", e)
            Result.failure(e)
        }
    }

    private fun getBytesFromUri(uriString: String): ByteArray? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> uri.path?.let { java.io.File(it).readBytes() }
            else -> throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
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
