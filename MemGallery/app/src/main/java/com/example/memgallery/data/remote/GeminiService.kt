package com.example.memgallery.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.GenerateContentConfig
import com.google.gson.Gson
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private var client: Client? = null

    private val systemInstructionContent = Content.fromParts(Part.fromText(
        "You are an expert AI assistant for the MemGallery app. Your task is to analyze user-provided content and generate a structured JSON response. " +
        "The JSON object must contain the following fields: 'title' (a short, evocative title for the memory), 'summary' (a single paragraph narrative synthesizing all inputs), " +
        "'tags' (an array of 3-5 relevant string tags), 'image_analysis' (a detailed description of the image, if present), and 'audio_transcription' (a verbatim transcription of the audio, if present)."
    ))

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
            "audio_transcription": { "type": "string" }
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
        userText: String?
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(IllegalStateException("Gemini client is not initialized."))

        try {
            val promptBuilder = StringBuilder()

            val config = com.google.genai.types.GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .systemInstruction(systemInstructionContent)
                .build()

            val parts = mutableListOf<Part>()

            if (imageUri != null) {
                val imageBytes = getBytesFromUri(imageUri)
                    ?: throw IOException("Could not read image file from URI: $imageUri")
                val parsedUri = Uri.parse(imageUri)
                val mimeType = if (parsedUri.scheme == "content") {
                    context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                } else {
                    "image/jpeg"
                }
                parts.add(Part.fromBytes(imageBytes, mimeType))
                promptBuilder.append("Analyze the provided image in detail. ")
            }

            if (audioUri != null) {
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

            promptBuilder.append("\n\nBased on all the provided content, generate the final JSON response.")
            parts.add(0, Part.fromText(promptBuilder.toString()))

            val multimodalContent = Content.fromParts(*parts.toTypedArray())

            val response: GenerateContentResponse? = localClient.models.generateContent(
                "gemini-2.5-flash",
                multimodalContent,
                config
            )

            val textResponse = response?.text() ?: throw Exception("Received an empty or null response from the API.")
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

    private fun parseJsonResponse(responseText: String): Result<AiAnalysisDto> {
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


