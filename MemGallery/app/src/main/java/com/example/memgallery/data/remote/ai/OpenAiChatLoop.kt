package com.example.memgallery.data.remote.ai

import android.util.Log
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "OpenAiChatLoop"

/** Everything one OpenAI-format endpoint call needs. [extraBody] is merged into the request JSON. */
data class OpenAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val extraBody: Map<String, Any?> = emptyMap(),
    val maxToolCalls: Int = 3,
)

/** DeepSeek's official, OpenAI-compatible API (https://api-docs.deepseek.com). */
object DeepSeekConfig {
    const val BASE_URL = "https://api.deepseek.com"
    /** Current model ids per api-docs.deepseek.com (checked 5 Nov 2026). The list in Settings is fetched live from GET /models. */
    const val DEFAULT_MODEL = "deepseek-flash"
    val KNOWN_MODELS = listOf("deepseek-flash", "deepseek-v4-pro")
    /**
     * The model that takes pictures. deepseek-flash accepts text and image input (GET /models, api-docs vision guide);
     * deepseek-v4-pro is text only, and the older deepseek-v4-flash-vision-exp is served by Flash now.
     */
    const val VISION_MODEL = "deepseek-flash"

    /** User message content: a plain string, or text plus base64 image_url parts (images are accepted in user messages only). */
    fun userContent(text: String, images: List<ImageData>): Any {
        if (images.isEmpty()) return text
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        for (img in images) {
            val b64 = java.util.Base64.getEncoder().encodeToString(img.bytes)
            parts.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${img.mimeType};base64,$b64")))
        }
        return parts
    }

    fun chat(baseUrl: String = BASE_URL, apiKey: String, model: String, thinking: Boolean, maxToolCalls: Int): OpenAiConfig =
        OpenAiConfig(
            baseUrl = baseUrl.trim().removeSuffix("/"),
            apiKey = apiKey,
            model = model.ifBlank { DEFAULT_MODEL },
            // DeepSeek has thinking on by default; it costs time and tokens for tags and summaries, so it is opt-in.
            extraBody = mapOf("thinking" to mapOf("type" to if (thinking) "enabled" else "disabled")),
            maxToolCalls = maxToolCalls,
        )
}

/**
 * The shared OpenAI chat-completions client: one request, or the tool-call loop (memory search first,
 * web search second, whatever the model asks for), until the model answers in plain text.
 * Keeps the assistant message exactly as returned, so DeepSeek's `reasoning_content` goes back with the tool results.
 */
class OpenAiChatLoop(private val client: OkHttpClient, private val gson: Gson = Gson()) {

    private fun post(config: OpenAiConfig, path: String, body: JSONObject): Request =
        Request.Builder()
            .url(config.baseUrl + path)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

    private fun applyExtra(body: JSONObject, extra: Map<String, Any?>) {
        for ((k, v) in extra) body.put(k, toJson(v))
    }

    private fun toJson(v: Any?): Any? = when (v) {
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        else -> v
    }

    suspend fun run(config: OpenAiConfig, messages: JSONArray, toolExecutor: ToolExecutor?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val current = JSONArray(messages.toString())
                var toolCallCount = 0
                while (true) {
                    if (toolCallCount >= config.maxToolCalls) {
                        return@withContext Result.failure(Exception("Max tool calls (${config.maxToolCalls}) exceeded. The AI may be stuck in a loop."))
                    }
                    val body = JSONObject().apply {
                        put("model", config.model)
                        put("messages", current)
                        applyExtra(this, config.extraBody)
                        if (toolExecutor != null) {
                            val tools = buildTools(toolExecutor)
                            if (tools.length() > 0) {
                                put("tools", tools)
                                put("tool_choice", "auto")
                            }
                        }
                    }
                    val bodyStr = client.newCall(post(config, "/chat/completions", body)).execute().use { resp ->
                        val s = resp.body?.string()
                        if (!resp.isSuccessful || s == null) throw IOException("API request failed (${resp.code}): $s")
                        s
                    }
                    val choices = JSONObject(bodyStr).optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        return@withContext Result.failure(Exception("Invalid API response: no choices"))
                    }
                    val assistant = choices.getJSONObject(0).getJSONObject("message")
                    current.put(assistant)
                    val toolCalls = assistant.optJSONArray("tool_calls")
                    if (toolCalls == null || toolCalls.length() == 0) {
                        return@withContext Result.success(assistant.optString("content", ""))
                    }
                    for (i in 0 until toolCalls.length()) {
                        val call = toolCalls.getJSONObject(i)
                        val id = call.getString("id")
                        val fn = call.getJSONObject("function")
                        val name = fn.getString("name")
                        val argsStr = fn.optString("arguments", "{}")
                        val args: Map<String, Any?>? = try {
                            @Suppress("UNCHECKED_CAST")
                            gson.fromJson(argsStr, Map::class.java) as? Map<String, Any?>
                        } catch (e: Exception) { null }
                        val result = if (args == null) {
                            "Error: arguments JSON invalid. Re-emit with valid JSON."
                        } else try {
                            toolExecutor?.executeTool(name, args) ?: "Tool executor not available"
                        } catch (e: Exception) {
                            Log.e(TAG, "Tool $name failed", e)
                            "Tool execution error: ${e.message}"
                        }
                        current.put(JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", id)
                            put("content", result)
                        })
                        toolCallCount++
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.failure(IllegalStateException("unreachable"))
            } catch (e: Exception) {
                Log.e(TAG, "Chat request failed", e)
                Result.failure(e)
            }
        }

    /** Memory processing: ask for one JSON object and parse it into the analysis DTO. */
    suspend fun completeJson(config: OpenAiConfig, systemPrompt: String, userPrompt: String, images: List<ImageData> = emptyList()): Result<AiAnalysisDto> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", DeepSeekConfig.userContent(userPrompt, images))))
                    put("response_format", JSONObject().put("type", "json_object"))
                    applyExtra(this, config.extraBody)
                }
                val bodyStr = client.newCall(post(config, "/chat/completions", body)).execute().use { resp ->
                    val s = resp.body?.string()
                    if (!resp.isSuccessful || s == null) throw IOException("API request failed (${resp.code}): $s")
                    s
                }
                val content = JSONObject(bodyStr).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
                parseAnalysis(content)
            } catch (e: Exception) {
                Log.e(TAG, "completeJson failed", e)
                Result.failure(e)
            }
        }

    fun parseAnalysis(content: String): Result<AiAnalysisDto> {
        val clean = content.trim().let {
            when {
                it.startsWith("```json") -> it.removePrefix("```json").removeSuffix("```").trim()
                it.startsWith("```") -> it.removePrefix("```").removeSuffix("```").trim()
                else -> it
            }
        }
        return try {
            Result.success(gson.fromJson(clean, AiAnalysisDto::class.java))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse JSON from AI: $clean", e))
        }
    }

    /** GET /models. Doubles as the key check: a bad key answers 401. */
    suspend fun listModels(config: OpenAiConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(config.baseUrl + "/models").addHeader("Authorization", "Bearer ${config.apiKey}").get().build()
            client.newCall(req).execute().use { resp ->
                val s = resp.body?.string()
                if (!resp.isSuccessful || s == null) return@withContext Result.failure(IOException("Key check failed (${resp.code}): $s"))
                val data = JSONObject(s).optJSONArray("data") ?: JSONArray()
                Result.success((0 until data.length()).map { data.getJSONObject(it).getString("id") })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildTools(toolExecutor: ToolExecutor): JSONArray {
        val tools = JSONArray()
        toolExecutor.getToolDefinitions().forEach { def ->
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", def.name)
                    put("description", def.description)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            def.parameters.forEach { (name, p) ->
                                put(name, JSONObject().apply {
                                    put("type", p.type)
                                    put("description", p.description)
                                    p.enum?.let { put("enum", JSONArray(it)) }
                                })
                            }
                        })
                        put("required", JSONArray(def.parameters.filter { it.value.required }.keys))
                    })
                })
            })
        }
        return tools
    }
}
