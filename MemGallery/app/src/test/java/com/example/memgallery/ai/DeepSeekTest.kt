package com.example.memgallery.ai

import com.example.memgallery.data.remote.ai.DeepSeekConfig
import com.example.memgallery.data.remote.ai.OpenAiChatLoop
import com.example.memgallery.data.remote.ai.ParameterDef
import com.example.memgallery.data.remote.ai.ToolDefinition
import com.example.memgallery.data.remote.ai.ToolExecutor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DeepSeekTest {
    private lateinit var server: MockWebServer

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private class Exec : ToolExecutor {
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun getToolDefinitions() = listOf(
            ToolDefinition("queryDatabase", "memory", mapOf("table" to ParameterDef("string", "t", true))),
            ToolDefinition("webSearch", "web", mapOf("query" to ParameterDef("string", "q", true)))
        )
        override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
            calls += toolName to arguments
            return if (toolName == "webSearch") "[1] Result https://r.example" else "memory rows"
        }
    }

    private fun cfg(thinking: Boolean = false) = DeepSeekConfig.chat(
        baseUrl = server.url("/").toString().removeSuffix("/"), apiKey = "sk-test", model = "deepseek-flash", thinking = thinking, maxToolCalls = 3
    )

    private fun messages(user: String) = JSONArray().put(JSONObject().put("role", "system").put("content", "sys")).put(JSONObject().put("role", "user").put("content", user))

    @Test fun plainAnswerSendsBearerModelAndDisablesThinkingByDefault() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Hello there"}}]}"""))
        val r = OpenAiChatLoop(OkHttpClient()).run(cfg(), messages("hi"), null)
        assertEquals("Hello there", r.getOrThrow())
        val req = server.takeRequest()
        assertEquals("/chat/completions", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("deepseek-flash", body.getString("model"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertFalse(body.has("tools"))
    }

    @Test fun thinkingOnSendsEnabled() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"x"}}]}"""))
        OpenAiChatLoop(OkHttpClient()).run(cfg(thinking = true), messages("hi"), null)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"))
    }

    @Test fun webSearchToolIsAdvertisedAfterMemorySearch() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""))
        OpenAiChatLoop(OkHttpClient()).run(cfg(), messages("hi"), Exec())
        val tools = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("tools")
        assertEquals("queryDatabase", tools.getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("webSearch", tools.getJSONObject(1).getJSONObject("function").getString("name"))
    }

    @Test fun toolCallLoopRunsWebSearchThenReturnsFinalAnswerAndKeepsReasoningContent() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"","reasoning_content":"need web","tool_calls":[{"id":"call_1","type":"function","function":{"name":"webSearch","arguments":"{\"query\":\"deepseek news\"}"}}]}}]}"""))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Here is the news [1]"}}]}"""))
        val exec = Exec()
        val r = OpenAiChatLoop(OkHttpClient()).run(cfg(thinking = true), messages("news?"), exec)
        assertEquals("Here is the news [1]", r.getOrThrow())
        assertEquals(1, exec.calls.size)
        assertEquals("webSearch", exec.calls[0].first)
        assertEquals("deepseek news", exec.calls[0].second["query"])
        server.takeRequest()
        val second = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        val assistant = second.getJSONObject(second.length() - 2)
        assertEquals("assistant", assistant.getString("role"))
        assertEquals("need web", assistant.getString("reasoning_content"))
        val tool = second.getJSONObject(second.length() - 1)
        assertEquals("tool", tool.getString("role"))
        assertEquals("call_1", tool.getString("tool_call_id"))
        assertTrue(tool.getString("content").contains("https://r.example"))
    }

    @Test fun badToolArgumentsAreNotExecuted() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"c","type":"function","function":{"name":"webSearch","arguments":"{not json"}}]}}]}"""))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"done"}}]}"""))
        val exec = Exec()
        OpenAiChatLoop(OkHttpClient()).run(cfg(), messages("x"), exec)
        assertEquals(0, exec.calls.size)
    }

    @Test fun runawayToolLoopStopsAtTheCap() = runBlocking {
        repeat(6) {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"c$it","type":"function","function":{"name":"queryDatabase","arguments":"{\"table\":\"memories\"}"}}]}}]}"""))
        }
        val r = OpenAiChatLoop(OkHttpClient()).run(cfg(), messages("x"), Exec())
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("Max tool calls"))
    }

    @Test fun unauthorisedKeyGivesAClearError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Authentication Fails"}}"""))
        val r = OpenAiChatLoop(OkHttpClient()).run(cfg(), messages("x"), null)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test fun jsonModeRequestForMemoryProcessingAsksForAJsonObject() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"```json\n{\"title\":\"T\",\"summary\":\"S\",\"tags\":[\"a\"]}\n```"}}]}"""))
        val r = OpenAiChatLoop(OkHttpClient()).completeJson(cfg(), "sys", "user note")
        val dto = r.getOrThrow()
        assertEquals("T", dto.title)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
    }

    @Test fun validateKeyHitsModelsEndpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"deepseek-flash","object":"model","owned_by":"deepseek"},{"id":"deepseek-v4-pro","object":"model","owned_by":"deepseek"}]}"""))
        val ids = OpenAiChatLoop(OkHttpClient()).listModels(cfg()).getOrThrow()
        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), ids)
        val req = server.takeRequest()
        assertEquals("/models", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
    }
}
