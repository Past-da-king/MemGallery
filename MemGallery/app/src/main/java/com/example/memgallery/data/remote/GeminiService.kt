package com.example.memgallery.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.GenerateContentConfig
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiService"

@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val settingsRepository: SettingsRepository
) {
    private var client: Client? = null

    private val responseSchema = Schema.fromJson("""
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
    """)

    fun initialize(apiKey: String) {
        client = Client.builder().apiKey(apiKey).build()
    }

    fun isEnabled(): Boolean = client != null

    fun disable() {
        client = null
    }

    suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val tempClient = Client.builder().apiKey(apiKey).build()
            tempClient.models.generateContent("gemini-2.5-flash", "test", com.google.genai.types.GenerateContentConfig.builder().build())
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun processMemory(
        imageUri: String?,
        audioUri: String?,
        userText: String?,
        bookmarkUrl: String?,
        bookmarkTitle: String?,
        bookmarkDescription: String?,
        bookmarkImageUrl: String?,
        existingCollections: List<String> = emptyList()
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        Log.d(TAG, "processMemory started")
        val localClient = client ?: return@withContext Result.failure(IllegalStateException("Gemini client is not initialized."))

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

            val systemInstructionContent = Content.fromParts(Part.fromText(finalSystemInstruction))

            val promptBuilder = StringBuilder()

            val config = com.google.genai.types.GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .systemInstruction(systemInstructionContent)
                .build()

            val parts = mutableListOf<Part>()

            if (imageUri != null) {
                Log.d(TAG, "Processing image URI: $imageUri")
                val imageBytes = getBytesFromUri(imageUri)
                    ?: throw IOException("Could not read image file from URI: $imageUri")

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val resizedBitmap = resizeBitmap(bitmap, 1024)
                val outputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val resizedImageBytes = outputStream.toByteArray()
                Log.d(TAG, "Image resized from ${imageBytes.size} to ${resizedImageBytes.size} bytes")


                val parsedUri = Uri.parse(imageUri)
                val mimeType = if (parsedUri.scheme == "content") {
                    context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                } else {
                    "image/jpeg"
                }
                parts.add(Part.fromBytes(resizedImageBytes, mimeType))
                promptBuilder.append("Analyze the provided image in detail. ")
            }

            // Handle Bookmark Image (Remote URL)
            if (bookmarkImageUrl != null && imageUri == null) {
                 // Note: For now, we are not downloading the remote image to send to Gemini as bytes.
                 // We are relying on the text description and metadata.
                 // Future improvement: Download the image from bookmarkImageUrl and send it as bytes.
                 promptBuilder.append("The user bookmarked a page with this preview image URL: $bookmarkImageUrl. ")
            }
            
            if (bookmarkUrl != null) {
                promptBuilder.append("Analyze this bookmarked webpage: $bookmarkUrl. ")
                if (!bookmarkTitle.isNullOrBlank()) promptBuilder.append("Title: $bookmarkTitle. ")
                if (!bookmarkDescription.isNullOrBlank()) promptBuilder.append("Description: $bookmarkDescription. ")
            }

            if (audioUri != null) {
                Log.d(TAG, "Processing audio URI: $audioUri")
                val audioBytes = getBytesFromUri(audioUri)
                    ?: throw IOException("Could not read audio file from URI: $audioUri")
                val parsedUri = Uri.parse(audioUri)
                val mimeType = if (parsedUri.scheme == "content") {
                    context.contentResolver.getType(parsedUri) ?: "audio/m4a"
                } else {
                    "audio/m4a"
                }
                parts.add(Part.fromBytes(audioBytes, mimeType))
                promptBuilder.append("Transcribe the provided audio recording verbatim and analyze its content. ")
            }

            if (!userText.isNullOrBlank()) {
                promptBuilder.append("Incorporate the following user's note into your analysis: \"$userText\". ")
            }

            val currentDateTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            promptBuilder.append("The current date and time is $currentDateTime. Use this context to determine if events are past or future. ")

            promptBuilder.append("\n\nBased on all the provided content, generate the final JSON response.")
            parts.add(0, Part.fromText(promptBuilder.toString()))

            val multimodalContent = Content.fromParts(*parts.toTypedArray())
            Log.d(TAG, "Sending request to Gemini API with ${parts.size} parts")

            val response: GenerateContentResponse? = localClient.models.generateContent(
                "gemini-2.5-flash",
                multimodalContent,
                config
            )

            val textResponse = response?.text() ?: throw Exception("Received an empty or null response from the API.")
            Log.d(TAG, "Received response from Gemini API")
            parseJsonResponse(textResponse)

        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun parseJsonResponse(responseText: String): Result<AiAnalysisDto> {
        Log.d(TAG, "Raw JSON response: $responseText")
        val cleanedResponseText = responseText
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        return try {
            Result.success(gson.fromJson(cleanedResponseText, AiAnalysisDto::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(IllegalStateException("Failed to parse AI analysis JSON: ${e.message}. Raw response: $cleanedResponseText", e))
        }
    }
}


