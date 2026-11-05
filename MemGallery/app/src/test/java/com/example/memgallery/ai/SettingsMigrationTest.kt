package com.example.memgallery.ai

import com.example.memgallery.data.remote.ai.ProviderSetup
import com.example.memgallery.data.remote.ai.SettingsMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {
    @Test fun existingGeminiUserKeepsProviderAndGetsPerJobValuesWrittenOnce() {
        val s = ProviderSetup("GEMINI", null, null, null, hasGeminiKey = true, hasGroqKey = true, hasDeepSeekKey = false, hasCustomKey = false, hasLocalModel = false)
        val m = SettingsMigration.perJobWrites(s)
        assertEquals("GEMINI", m.main)
        assertEquals("GEMINI", m.voice)
        assertEquals("GEMINI", m.images)
    }

    @Test fun alreadyChosenValuesAreNeverOverwritten() {
        val s = ProviderSetup("GEMINI", "DEEPSEEK", "GROQ", "GEMINI", true, true, true, false, false)
        val m = SettingsMigration.perJobWrites(s)
        assertNull(m.main); assertNull(m.voice); assertNull(m.images)
    }

    @Test fun groqUserWithGeminiKeyToo() {
        val s = ProviderSetup("GROQ", null, null, null, true, true, false, false, false)
        val m = SettingsMigration.perJobWrites(s)
        assertEquals("GROQ", m.main); assertEquals("GROQ", m.voice); assertEquals("GROQ", m.images)
    }

    @Test fun themeResetRunsOnceForOldInstalls() {
        assertTrue(SettingsMigration.shouldResetTheme(themeVersion = 0, dynamicStored = true, colorStored = -1))
        assertTrue(SettingsMigration.shouldResetTheme(themeVersion = 0, dynamicStored = false, colorStored = 0xFF800000.toInt()))
        assertFalse(SettingsMigration.shouldResetTheme(themeVersion = 1, dynamicStored = true, colorStored = 5))
        assertFalse(SettingsMigration.shouldResetTheme(themeVersion = 0, dynamicStored = false, colorStored = -1))
    }
}
