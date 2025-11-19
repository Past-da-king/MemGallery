package com.example.memgallery.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * A type-safe data class to represent the structured JSON response from the Gemini API.
 * This ensures predictable and safe handling of the AI's output.
 */
data class AiAnalysisDto(
    @SerializedName("title")
    val title: String,

    @SerializedName("summary")
    val summary: String,

    @SerializedName("tags")
    val tags: List<String>,

    @SerializedName("image_analysis")
    val imageAnalysis: String?, // Nullable as it's only present for image inputs

    @SerializedName("audio_transcription")
    val audioTranscription: String?, // Nullable as it's only present for audio inputs

    @SerializedName("actions")
    val actions: List<ActionDto>? = null
)
