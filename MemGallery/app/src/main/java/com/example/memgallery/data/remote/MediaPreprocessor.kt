package com.example.memgallery.data.remote

import android.util.Log
import com.example.memgallery.data.remote.ai.AIProviderFactory
import com.example.memgallery.data.remote.ai.AIProviderType
import com.example.memgallery.data.remote.ai.AudioData
import com.example.memgallery.data.remote.ai.ImageData
import com.example.memgallery.data.remote.ai.JobRouting
import com.example.memgallery.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaPreprocessor"

/**
 * Splits media between the per-job providers. A text-only Main brain (DeepSeek) still gets voice notes and pictures:
 * the Voice provider turns audio into a transcript and the Images provider turns pictures into a description,
 * and both arrive as text next to the user's own words.
 */
@Singleton
class MediaPreprocessor @Inject constructor(
    private val factory: AIProviderFactory,
    private val settings: SettingsRepository,
) {
    data class Prepared(
        val audio: AudioData?,
        val images: List<ImageData>,
        /** Text to add after the user's own words (transcript, picture description). */
        val extraText: String?,
        val transcript: String?,
        /** Set when a needed transcription or description failed, with a message the user can act on. */
        val error: String?,
    )

    suspend fun prepare(audio: AudioData?, images: List<ImageData>): Prepared {
        val main = settings.resolvedMainProvider()
        val plan = JobRouting.plan(main, settings.resolvedVoiceProvider(), settings.resolvedImageProvider(), audio != null, images.isNotEmpty())
        val extra = mutableListOf<String>()
        var transcript: String? = null
        var error: String? = null

        if (audio != null && plan.transcribeVia != null) {
            val p = factory.providerFor(typeOf(plan.transcribeVia))
            p.transcribeAudio(audio)
                .onSuccess { transcript = it.trim(); extra += "Voice note transcript:\n${it.trim()}" }
                .onFailure { error = "Voice transcription (${plan.transcribeVia}) failed: ${it.message}"; Log.e(TAG, "transcribe failed", it) }
        }
        if (images.isNotEmpty() && plan.describeVia != null) {
            val p = factory.providerFor(typeOf(plan.describeVia))
            p.describeImages(images)
                .onSuccess { extra += "Attached image, described by an image model:\n${it.trim()}" }
                .onFailure { if (error == null) error = "Image reading (${plan.describeVia}) failed: ${it.message}"; Log.e(TAG, "describe failed", it) }
        }
        return Prepared(
            audio = if (plan.passAudioToMain) audio else null,
            images = if (plan.passImagesToMain) images else emptyList(),
            extraText = extra.joinToString("\n\n").ifBlank { null },
            transcript = transcript,
            error = error,
        )
    }

    private fun typeOf(id: String) = when (id) {
        JobRouting.GROQ -> AIProviderType.GROQ
        JobRouting.DEEPSEEK -> AIProviderType.DEEPSEEK
        JobRouting.CUSTOM -> AIProviderType.OPENAI_COMPATIBLE
        else -> AIProviderType.GEMINI
    }
}
