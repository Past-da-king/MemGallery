package com.example.memgallery.data.remote.ai

import android.content.Context
import android.util.Log
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenAIProvider"

/**
 * AI Provider for OpenAI-compatible endpoints (e.g. LocalAI, Ollama, vLLM, or actual OpenAI).
 * Allows custom Base URL and Model Name.
 */
@Singleton
class OpenAICompatibleProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val gson: Gson
) : AIProvider {

    override val providerName: String = "OpenAI Compatible"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var apiKey: String? = null

    override fun initialize(apiKey: String) {
        this.apiKey = apiKey
        Log.i(TAG, "OpenAI Compatible provider initialized")
    }

    override fun isEnabled(): Boolean = !apiKey.isNullOrBlank()

    override fun disable() {
        apiKey = null
    }

    // Helper to get current config
    private suspend fun getConfig(): Triple<String, String, String> {
        val baseUrl = settingsRepository.customBaseUrlFlow.first().trim().removeSuffix("/")
        val modelName = settingsRepository.customModelNameFlow.first().trim()
        val key = apiKey ?: settingsRepository.customApiKeyFlow.first() ?: ""
        return Triple(baseUrl, modelName, key)
    }

    override suspend fun validateApiKey(apiKey: String): Result<Boolean> = validateCustomSettings(
        apiKey = apiKey,
        baseUrl = settingsRepository.customBaseUrlFlow.first(),
        modelName = settingsRepository.customModelNameFlow.first()
    )

    suspend fun validateCustomSettings(
        apiKey: String,
        baseUrl: String,
        modelName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = baseUrl.trim().removeSuffix("/")
            val finalUrl = "$cleanUrl/chat/completions"
            
            val jsonBody = JSONObject().apply {
                put("model", modelName.trim())
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
                put("max_tokens", 5)
            }

            val request = Request.Builder()
                .url(finalUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Result.failure(Exception("Validation failed (${response.code}): $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Custom settings validation failed", e)
            Result.failure(e)
        }
    }

    override suspend fun processMemory(
        imageDataList: List<ImageData>,
        audioData: AudioData?,
        userText: String?,
        bookmarkUrl: String?,
        bookmarkTitle: String?,
        bookmarkDescription: String?,
        bookmarkImageUrl: String?,
        existingCollections: List<String>,
        systemPrompt: String,
        responseSchemaJson: String
    ): Result<AiAnalysisDto> = withContext(Dispatchers.IO) {
        try {
            val (baseUrl, modelName, key) = getConfig()
            val finalUrl = "$baseUrl/chat/completions"

            // Build content list for multimodal input
            val contentList = JSONArray()

            // 1. Text Prompt construction
            val promptBuilder = StringBuilder()
            promptBuilder.append(systemPrompt).append("\n\n")
            
            if (bookmarkUrl != null) {
                promptBuilder.append("Analyze this bookmark: $bookmarkUrl\n")
                if (!bookmarkTitle.isNullOrBlank()) promptBuilder.append("Title: $bookmarkTitle\n")
                if (!bookmarkDescription.isNullOrBlank()) promptBuilder.append("Desc: $bookmarkDescription\n")
            }
            if (!userText.isNullOrBlank()) {
                promptBuilder.append("User Note: $userText\n")
            }
            promptBuilder.append("Return ONLY valid JSON matching the schema provided.")

            contentList.put(JSONObject().apply {
                put("type", "text")
                put("text", promptBuilder.toString())
            })

            // 2. Images (base64)
            imageDataList.forEach { img ->
                val base64Params = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                   Base64.getEncoder().encodeToString(img.bytes)
                } else {
                   android.util.Base64.encodeToString(img.bytes, android.util.Base64.NO_WRAP)
                }
                
                contentList.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${img.mimeType};base64,$base64Params")
                    })
                })
            }

            // 3. Construct Request Body
            // NOTE: Audio is not standard in OpenAI Chat Completions (usually Whisper API). 
            // We'll skip raw audio bytes here or handle via separate call if needed, 
            // but for "Compatible" providers, text+vision is safest overlap.
            // If audioData exists, we ideally need to transcribe it first. 
            // For now, we'll append a note if audio was dropped or implement transcription later.
            
            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", contentList)
                    })
                })
                put("response_format", JSONObject().apply { put("type", "json_object") }) // Force JSON if supported
            }

            val request = Request.Builder()
                .url(finalUrl)
                .addHeader("Authorization", "Bearer $key")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr == null) {
                    throw IOException("API Call failed: ${response.code} $bodyStr")
                }

                val jsonResponse = JSONObject(bodyStr)
                val choices = jsonResponse.getJSONArray("choices")
                val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
                
                // Parse the JSON content - robustly handle potential markdown wrapping
                val cleanContent = content.trim().run {
                    if (startsWith("```json")) {
                        removePrefix("```json").removeSuffix("```").trim() 
                    } else if (startsWith("```")) {
                        removePrefix("```").removeSuffix("```").trim()
                    } else {
                        this
                    }
                }
                
                // Parse the JSON content
                try {
                    val resultDto = gson.fromJson(content, AiAnalysisDto::class.java)
                    Result.success(resultDto)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to parse JSON from AI: $content", e))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process memory failed", e)
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val (baseUrl, modelName, key) = getConfig()
            val finalUrl = "$baseUrl/chat/completions"

            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            // Add history if present
            if (conversationHistory.isNotBlank()) {
                // Heuristic: try to parse roles if history is formatted, otherwise treat as text
                messages.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", "Previous context:\n$conversationHistory")
                })
            }

            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", message)
            })

            executeChatRequest(finalUrl, key, modelName, messages, toolExecutor)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
            Result.failure(e)
        }
    }

    override suspend fun sendMessageWithMedia(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val (baseUrl, modelName, key) = getConfig()
            val finalUrl = "$baseUrl/chat/completions"

            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            val contentArray = JSONArray()
            if (conversationHistory.isNotEmpty()) {
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Previous context:\n$conversationHistory")
                })
            }
            if (!message.isNullOrBlank()) {
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", message)
                })
            }
            
            if (imageData != null) {
                val base64Params = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                   Base64.getEncoder().encodeToString(imageData.bytes)
                } else {
                   android.util.Base64.encodeToString(imageData.bytes, android.util.Base64.NO_WRAP)
                }
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply { 
                         put("url", "data:${imageData.mimeType};base64,$base64Params")
                    })
                })
            }

            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            })

            executeChatRequest(finalUrl, key, modelName, messages, toolExecutor)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessageWithMedia failed", e)
            Result.failure(e)
        }
    }

    private suspend fun executeChatRequest(
        url: String,
        key: String,
        model: String,
        messages: JSONArray,
        toolExecutor: ToolExecutor?
    ): Result<String> {
        var currentMessages = messages
        var toolCallCount = 0
        val maxToolCalls = try { settingsRepository.maxToolCallsFlow.first() } catch (e: Exception) { 5 }

        // Loop until we get a final response (no tool calls) or hit max iterations
        while (true) {
            if (toolCallCount >= maxToolCalls) {
                return Result.failure(Exception("Max tool calls ($maxToolCalls) exceeded. The AI may be stuck in a loop."))
            }

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", currentMessages)
                // Only add tools if toolExecutor is provided and we haven't exceeded limits
                if (toolExecutor != null && toolCallCount < maxToolCalls) {
                    val tools = buildOpenAITools(toolExecutor)
                    if (tools.length() > 0) {
                        put("tools", tools)
                        put("tool_choice", "auto")
                    }
                }
            }

            Log.d(TAG, "Sending request to $url with ${currentMessages.length()} messages, toolCallCount=$toolCallCount")

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://github.com/Past-da-king/MemGallery")
                .addHeader("X-Title", "MemGallery")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string()
                
                if (!response.isSuccessful || bodyStr == null) {
                    response.close()
                    throw IOException("API request failed (${response.code}): $bodyStr")
                }
                response.close()

                val jsonResponse = JSONObject(bodyStr)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    Log.e(TAG, "No choices in response: $bodyStr")
                    return Result.failure(Exception("Invalid API response: no choices"))
                }
                
                val choice = choices.getJSONObject(0)
                val assistantMsgJson = choice.getJSONObject("message")
                
                // Add assistant message to history for potential next turn
                currentMessages.put(assistantMsgJson)

                // Check if the assistant wants to call tools
                val toolCalls = assistantMsgJson.optJSONArray("tool_calls")
                
                if (toolCalls == null || toolCalls.length() == 0) {
                    // No tool calls - this is the final response
                    val content = assistantMsgJson.optString("content", "")
                    Log.d(TAG, "Final response received (no tool calls): ${content.take(100)}...")
                    return Result.success(content)
                }

                // Process tool calls
                Log.d(TAG, "Assistant requested ${toolCalls.length()} tool calls")

                for (i in 0 until toolCalls.length()) {
                    val toolCall = toolCalls.getJSONObject(i)
                    val callId = toolCall.getString("id")
                    val function = toolCall.getJSONObject("function")
                    val fnName = function.getString("name")
                    val fnArgsStr = function.optString("arguments", "{}")

                    Log.d(TAG, "Executing tool: $fnName with args: $fnArgsStr")
                    
                    val argsMap = try {
                        @Suppress("UNCHECKED_CAST")
                        gson.fromJson(fnArgsStr, Map::class.java) as? Map<String, Any?> ?: emptyMap()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse tool arguments: $fnArgsStr", e)
                        emptyMap<String, Any?>()
                    }

                    val result = try {
                        toolExecutor?.executeTool(fnName, argsMap) ?: "Tool executor not available"
                    } catch (e: Exception) {
                        Log.e(TAG, "Tool execution failed: $fnName", e)
                        "Tool execution error: ${e.message}"
                    }
                    
                    Log.d(TAG, "Tool $fnName result: ${result.take(200)}...")
                    
                    currentMessages.put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", fnName)
                        put("content", result)
                    })
                    
                    toolCallCount++
                }
                // Continue the loop to send tool results back to the model
                
            } catch (e: IOException) {
                // Check if error is related to tools not being supported
                val msg = e.message ?: ""
                val isToolError = msg.contains("404") || msg.contains("400") || 
                                  msg.contains("tools") || msg.contains("tool use") ||
                                  msg.contains("not supported")
                
                if (isToolError && toolCallCount == 0 && toolExecutor != null) {
                    Log.w(TAG, "Tool request failed ($msg). Retrying without tools.")
                    // Retry without tools
                    return executeChatRequest(url, key, model, messages, null)
                }
                Log.e(TAG, "API request failed", e)
                return Result.failure(e)
            }
        }
    }

    private fun buildOpenAITools(toolExecutor: ToolExecutor): JSONArray {
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
                            def.parameters.forEach { (name, param) ->
                                put(name, JSONObject().apply {
                                    put("type", param.type)
                                    put("description", param.description)
                                    if (param.enum != null) {
                                        put("enum", JSONArray(param.enum))
                                    }
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

    override fun sendMessageStream(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
         try {
             emit(ChatStreamEvent.Thinking("Processing..."))
             // Fallback to blocking sendMessage to ensure tool execution loops work correctly.
             val result = sendMessage(conversationHistory, message, systemPrompt, toolExecutor)
             result.onSuccess { 
                 emit(ChatStreamEvent.Content(it)) 
                 emit(ChatStreamEvent.Done)
             }.onFailure { emit(ChatStreamEvent.Error(it)) }
         } catch (e: Exception) {
             emit(ChatStreamEvent.Error(e))
         }
    }.flowOn(Dispatchers.IO)

    override fun sendMessageWithMediaStream(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
        try {
             emit(ChatStreamEvent.Thinking("Processing media..."))
             val result = sendMessageWithMedia(conversationHistory, message, audioData, imageData, systemPrompt, toolExecutor)
             result.onSuccess { 
                 emit(ChatStreamEvent.Content(it)) 
                 emit(ChatStreamEvent.Done)
             }.onFailure { emit(ChatStreamEvent.Error(it)) }
        } catch (e: Exception) {
             emit(ChatStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateUserContext(memoryText: String): Result<String> = withContext(Dispatchers.IO) {
        // Reuse sendMessage with specific prompt
        sendMessage("", "Generate user context from: $memoryText", "You are a helpful assistant.", null)
    }
}
