package com.example.memgallery.data.remote.ai

/** The three jobs the user can give a different provider. */
enum class AiJob { MAIN, VOICE, IMAGES }

/**
 * What the settings store knows, as plain values, so routing and migration are pure and unit-testable.
 * [legacyProvider] is the single global `ai_provider` older versions saved; it stays the "Main brain".
 */
data class ProviderSetup(
    val legacyProvider: String,
    val mainProvider: String?,
    val voiceProvider: String?,
    val imageProvider: String?,
    val hasGeminiKey: Boolean,
    val hasGroqKey: Boolean,
    val hasDeepSeekKey: Boolean,
    val hasCustomKey: Boolean,
    val hasLocalModel: Boolean,
)

/** How one memory or chat message with media is split across the per-job providers. */
data class MediaPlan(
    val transcribeVia: String?,
    val describeVia: String?,
    val passAudioToMain: Boolean,
    val passImagesToMain: Boolean,
)

object JobRouting {
    const val GEMINI = "GEMINI"
    const val GROQ = "GROQ"
    const val DEEPSEEK = "DEEPSEEK"
    const val LOCAL = "LOCAL"
    const val CUSTOM = "OPENAI_COMPATIBLE"
    const val NONE = "NONE"

    val MAIN_CHOICES = listOf(GEMINI, GROQ, DEEPSEEK, LOCAL, CUSTOM)
    val VOICE_CHOICES = listOf(GEMINI, GROQ, NONE)
    val IMAGE_CHOICES = listOf(GEMINI, GROQ, CUSTOM, NONE)

    private fun clean(s: String?): String? = s?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    private fun normaliseMain(s: String?): String = when (clean(s)) {
        GROQ -> GROQ
        DEEPSEEK -> DEEPSEEK
        LOCAL -> LOCAL
        CUSTOM -> CUSTOM
        else -> GEMINI
    }

    fun main(s: ProviderSetup): String = normaliseMain(clean(s.mainProvider) ?: s.legacyProvider)

    fun voice(s: ProviderSetup): String {
        clean(s.voiceProvider)?.let { if (it in VOICE_CHOICES) return it }
        return when (main(s)) {
            GEMINI -> GEMINI
            GROQ -> GROQ
            else -> firstWithKey(s) ?: NONE
        }
    }

    fun images(s: ProviderSetup): String {
        clean(s.imageProvider)?.let { if (it in IMAGE_CHOICES) return it }
        return when (val m = main(s)) {
            GEMINI, GROQ, CUSTOM -> m
            else -> firstWithKey(s) ?: NONE
        }
    }

    private fun firstWithKey(s: ProviderSetup): String? = when {
        s.hasGeminiKey -> GEMINI
        s.hasGroqKey -> GROQ
        else -> null
    }

    /** Providers that take images straight into the same call as the text. */
    fun seesImagesNatively(provider: String) = provider == GEMINI || provider == GROQ || provider == CUSTOM

    /** Providers that take raw audio straight into the same call (Groq transcribes with Whisper inside its own call). */
    fun hearsAudioNatively(provider: String) = provider == GEMINI || provider == GROQ

    fun plan(main: String, voice: String, images: String, hasAudio: Boolean, hasImages: Boolean): MediaPlan {
        var transcribeVia: String? = null
        var passAudio = false
        if (hasAudio) {
            if (voice == main && hearsAudioNatively(main)) passAudio = true
            else if (voice == GEMINI || voice == GROQ) transcribeVia = voice
        }
        var describeVia: String? = null
        var passImages = false
        if (hasImages) {
            if (images == main && seesImagesNatively(main)) passImages = true
            else if (images != NONE) describeVia = images
        }
        return MediaPlan(transcribeVia, describeVia, passAudio, passImages)
    }
}

/** First-run migration to per-job providers. Never overwrites a choice the user already made. */
object SettingsMigration {
    data class PerJobWrites(val main: String?, val voice: String?, val images: String?)

    fun perJobWrites(s: ProviderSetup): PerJobWrites = PerJobWrites(
        main = if (s.mainProvider.isNullOrBlank()) JobRouting.main(s) else null,
        voice = if (s.voiceProvider.isNullOrBlank()) JobRouting.voice(s) else null,
        images = if (s.imageProvider.isNullOrBlank()) JobRouting.images(s) else null,
    )

    /**
     * Installs from before the v4 redesign had wallpaper colours on by default (and some a custom seed colour),
     * which showed up as an unrequested maroon palette. Reset those once; the user can pick them again in Settings.
     */
    fun shouldResetTheme(themeVersion: Int, dynamicStored: Boolean, colorStored: Int): Boolean =
        themeVersion < 1 && (dynamicStored || colorStored != -1)
}
