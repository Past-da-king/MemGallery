package com.example.memgallery.ai

import com.example.memgallery.data.remote.ai.GeminiModels
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class GeminiModelsTest {
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun page(vararg ids: Pair<String, String>, next: String? = null): String {
        val models = ids.joinToString(",") { (id, m) -> """{"name":"models/$id","supportedGenerationMethods":[$m]}""" }
        return """{"models":[$models]${if (next != null) ""","nextPageToken":"$next"""" else ""}}"""
    }
    private val gc = "\"generateContent\""

    @Test fun followsEveryPageAndKeepsOnlyChatModels() {
        server.enqueue(MockResponse().setBody(page("gemini-3.5-flash-lite" to gc, "gemini-3.8-flash-tts" to gc, "gemini-embedding-2" to "\"embedContent\"", next = "p2")))
        server.enqueue(MockResponse().setBody(page("gemini-3.8-flash" to gc, "gemini-3.1-flash-image" to gc, "gemini-3.6-flash" to gc, "gemma-3-27b" to gc)))
        val r = GeminiModels.fetch(OkHttpClient(), "k", server.url("/").toString().removeSuffix("/")).getOrThrow()
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"), r)
        val first = server.takeRequest()
        assertEquals("k", first.getHeader("x-goog-api-key"))
        assertTrue(first.path!!.startsWith("/v1beta/models?pageSize=1000"))
        assertFalse(first.path!!.contains("key="))
        assertTrue(server.takeRequest().path!!.contains("pageToken=p2"))
    }

    @Test fun failureIsAFailureNotAnEmptyList() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"API key not valid"}"""))
        assertTrue(GeminiModels.fetch(OkHttpClient(), "bad", server.url("/").toString().removeSuffix("/")).isFailure)
    }

    @Test fun defaultIsTheNewestStableFlashLiteFromTheLiveList() {
        val live = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview", "gemini-4-argon")
        assertEquals("gemini-3.5-flash-lite", GeminiModels.pickDefault(live))
        assertEquals("gemini-3.8-flash", GeminiModels.pickDefault(listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3-flash-preview")))
        assertNull(GeminiModels.pickDefault(emptyList()))
    }

    @Test fun savedModelWinsWhileItExistsAndAStaleOneIsReplacedFromTheList() {
        val live = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite")
        assertEquals("gemini-3.8-flash", GeminiModels.resolve("gemini-3.8-flash", live))
        assertEquals("gemini-3.5-flash-lite", GeminiModels.resolve("gemini-2.0-flash", live))
        assertEquals("gemini-2.0-flash", GeminiModels.resolve("gemini-2.0-flash", emptyList()))
        assertEquals(GeminiModels.FALLBACK_DEFAULT, GeminiModels.resolve("", emptyList()))
    }
}
