package com.example.memgallery.ai

import com.example.memgallery.data.remote.ai.JobRouting
import com.example.memgallery.data.remote.ai.ProviderSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobRoutingTest {
    private fun setup(
        legacy: String = "GEMINI", main: String? = null, voice: String? = null, images: String? = null,
        gemini: Boolean = false, groq: Boolean = false, deepseek: Boolean = false, custom: Boolean = false, local: Boolean = false
    ) = ProviderSetup(legacy, main, voice, images, gemini, groq, deepseek, custom, local)

    @Test fun legacyGeminiKeepsEverythingOnGemini() {
        val s = setup(legacy = "GEMINI", gemini = true)
        assertEquals("GEMINI", JobRouting.main(s))
        assertEquals("GEMINI", JobRouting.voice(s))
        assertEquals("GEMINI", JobRouting.images(s))
    }

    @Test fun legacyGroqKeepsEverythingOnGroq() {
        val s = setup(legacy = "GROQ", groq = true)
        assertEquals("GROQ", JobRouting.main(s))
        assertEquals("GROQ", JobRouting.voice(s))
        assertEquals("GROQ", JobRouting.images(s))
    }

    @Test fun mainDeepSeekPicksVoiceAndImagesFromKeysThatExist() {
        val both = setup(legacy = "DEEPSEEK", deepseek = true, gemini = true, groq = true)
        assertEquals("DEEPSEEK", JobRouting.main(both))
        assertEquals("GEMINI", JobRouting.voice(both))
        assertEquals("DEEPSEEK", JobRouting.images(both))
        val groqOnly = setup(legacy = "DEEPSEEK", deepseek = true, groq = true)
        assertEquals("GROQ", JobRouting.voice(groqOnly))
        assertEquals("DEEPSEEK", JobRouting.images(groqOnly))
        val none = setup(legacy = "DEEPSEEK", deepseek = true)
        assertEquals("NONE", JobRouting.voice(none))
        assertEquals("DEEPSEEK", JobRouting.images(none))
    }

    @Test fun explicitChoicesWinOverTheLegacySingleProvider() {
        val s = setup(legacy = "GEMINI", main = "DEEPSEEK", voice = "GROQ", images = "GEMINI", gemini = true, groq = true, deepseek = true)
        assertEquals("DEEPSEEK", JobRouting.main(s))
        assertEquals("GROQ", JobRouting.voice(s))
        assertEquals("GEMINI", JobRouting.images(s))
    }

    @Test fun deepSeekCanNeverBeTheVoiceProviderButCanBeTheImageProvider() {
        val s = setup(legacy = "DEEPSEEK", voice = "DEEPSEEK", images = "DEEPSEEK", deepseek = true, groq = true)
        assertEquals("GROQ", JobRouting.voice(s))
        assertEquals("DEEPSEEK", JobRouting.images(s))
    }

    @Test fun deepSeekMainWithDeepSeekImagesSendsPicturesStraightAndStillNeedsVoiceProvider() {
        val plan = JobRouting.plan("DEEPSEEK", "GROQ", "DEEPSEEK", hasAudio = true, hasImages = true)
        assertTrue(plan.passImagesToMain)
        assertNull(plan.describeVia)
        assertEquals("GROQ", plan.transcribeVia)
        assertFalse(plan.passAudioToMain)
    }

    @Test fun geminiMainWithDeepSeekImagesDescribesViaDeepSeek() {
        val plan = JobRouting.plan("GEMINI", "GEMINI", "DEEPSEEK", hasAudio = false, hasImages = true)
        assertEquals("DEEPSEEK", plan.describeVia)
        assertFalse(plan.passImagesToMain)
    }

    @Test fun unknownProviderStringsFallBackToGemini() {
        assertEquals("GEMINI", JobRouting.main(setup(legacy = "weird")))
    }

    @Test fun deepSeekMainWithGroqVoiceAndGeminiImagesTranscribesAndDescribesFirst() {
        val plan = JobRouting.plan("DEEPSEEK", "GROQ", "GEMINI", hasAudio = true, hasImages = true)
        assertEquals("GROQ", plan.transcribeVia)
        assertEquals("GEMINI", plan.describeVia)
        assertFalse(plan.passAudioToMain)
        assertFalse(plan.passImagesToMain)
    }

    @Test fun geminiMainWithGeminiVoiceAndImagesSendsMediaStraightThrough() {
        val plan = JobRouting.plan("GEMINI", "GEMINI", "GEMINI", hasAudio = true, hasImages = true)
        assertNull(plan.transcribeVia)
        assertNull(plan.describeVia)
        assertTrue(plan.passAudioToMain)
        assertTrue(plan.passImagesToMain)
    }

    @Test fun noMediaMeansNoExtraWork() {
        val plan = JobRouting.plan("DEEPSEEK", "GROQ", "GEMINI", hasAudio = false, hasImages = false)
        assertNull(plan.transcribeVia)
        assertNull(plan.describeVia)
    }

    @Test fun noVoiceProviderDropsAudioInsteadOfCrashing() {
        val plan = JobRouting.plan("DEEPSEEK", "NONE", "NONE", hasAudio = true, hasImages = true)
        assertNull(plan.transcribeVia)
        assertNull(plan.describeVia)
        assertFalse(plan.passAudioToMain)
        assertFalse(plan.passImagesToMain)
    }

    @Test fun customMainSeesImagesItselfButStillNeedsAVoiceProvider() {
        val plan = JobRouting.plan("OPENAI_COMPATIBLE", "GROQ", "OPENAI_COMPATIBLE", hasAudio = true, hasImages = true)
        assertEquals("GROQ", plan.transcribeVia)
        assertTrue(plan.passImagesToMain)
    }
}
