package com.example.memgallery.data.remote.ai

import android.util.Log
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.ChatSystemPrompt
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.google.gson.Gson
import com.groq.sdk.client.GroqClient
import com.groq.sdk.models.audio.TranscriptionRequest
import com.groq.sdk.models.chat.ChatCompletionRequest
import com.groq.sdk.models.chat.ChatMessage
import com.groq.sdk.models.chat.ChatTool
import com.groq.sdk.models.chat.FunctionDefinition
import com.groq.sdk.models.mcp.MCPCallOutput
import com.groq.sdk.models.vision.VisionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GroqProvider"

// Maverick: 128 experts, 400B total params - Superior reasoning, multimodal, and detail
private const val VISION_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"

// Scout: 16 experts, 109B total params - Good for text-only chat, more cost-effective
// User requested switch to Maverick (LLaMA 4 128e) for superior reasoning.
private const val CHAT_MODEL = "meta-llama/llama-4-maverick-17b-128e-instruct"

private const val AUDIO_MODEL = "whisper-large-v3"
private const val MAX_TOOL_CALLS = 3

/**
 * Groq AI provider implementation.
 * Uses LLaMA 4 Maverick for vision (superior detail) and Scout for text-only chat.
 * Whisper for audio transcription.
 */
@Singleton
class GroqProvider @Inject constructor(
    private val gson: Gson
) : AIProvider {
    
    override val providerName: String = "Groq"
    
    private var client: GroqClient? = null
    
    override fun initialize(apiKey: String) {
        client = GroqClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(3)
            .build()
        
        Log.i(TAG, "Groq client initialized")
    }
    
    override fun isEnabled(): Boolean = client != null
    
    override fun disable() {
        client = null
    }
    
    override suspend fun validateApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val tempClient = GroqClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(10))
                .build()
            
            // Simple test message
            val message = ChatMessage("user", "test")
            val request = ChatCompletionRequest(CHAT_MODEL, listOf(message))
            request.maxTokens = 5
            
            val response = tempClient.chat().createCompletion(request)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("API validation failed: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
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
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Groq client is not initialized.")
        )
        
        try {
            // Step 1: Transcribe audio if present
            var audioTranscription: String? = null
            if (audioData != null) {
                Log.d(TAG, "Transcribing audio with Whisper")
                audioTranscription = transcribeAudio(localClient, audioData)
            }
            
            // Step 2: Build the prompt
            val promptBuilder = StringBuilder()
            promptBuilder.appendLine(systemPrompt)
            promptBuilder.appendLine()
            promptBuilder.appendLine("Please respond with valid JSON matching this schema:")
            promptBuilder.appendLine(responseSchemaJson)
            promptBuilder.appendLine()
            
            // Add comprehensive image analysis instructions - BE EXTREMELY DEMANDING
            if (imageDataList.isNotEmpty()) {
                promptBuilder.appendLine()
                promptBuilder.appendLine("=== CRITICAL IMAGE ANALYSIS INSTRUCTIONS ===")
                promptBuilder.appendLine()
                promptBuilder.appendLine("You are performing FORENSIC-LEVEL image analysis. This is a memory archiving system where NOTHING can be missed.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("**MANDATORY ANALYSIS REQUIREMENTS:**")
                promptBuilder.appendLine()
                promptBuilder.appendLine("1. **OBJECTS & ELEMENTS**: List EVERY single object visible - furniture, devices, decorations, plants, containers, papers, food, drinks, tools, accessories, clothing items, etc. DO NOT SKIP ANYTHING.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("2. **TEXT EXTRACTION**: Transcribe ALL text visible anywhere in the image - signs, labels, screens, documents, handwriting, logos, brands, dates, times, phone numbers, prices, addresses. BE EXHAUSTIVE.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("3. **PEOPLE ANALYSIS**: For any people - describe their approximate age, gender, clothing (colors, styles, brands if visible), accessories (glasses, jewelry, watches), posture, facial expressions, activities, and relative positions.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("4. **SPATIAL CONTEXT**: Describe the setting (indoor/outdoor, room type, establishment type), layout, relative positions of all elements, foreground/background distinction.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("5. **VISUAL PROPERTIES**: Colors (be specific - not just 'blue' but 'navy blue', 'sky blue'), lighting conditions, time of day indicators, weather if visible, shadows, reflections.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("6. **CONTEXT CLUES**: Any indications of occasion (birthday, holiday, work), season, location type (home, office, restaurant, outdoors), emotional mood of the scene.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("=== TAGGING REQUIREMENTS ===")
                promptBuilder.appendLine()
                promptBuilder.appendLine("Generate **MINIMUM 15 tags, ideally 20-30 tags** for each memory. Tags must include:")
                promptBuilder.appendLine("- Objects: Every significant object (e.g., 'laptop', 'coffee mug', 'notebook', 'potted plant')")
                promptBuilder.appendLine("- People: If people present (e.g., 'person', 'group', 'selfie', 'portrait')")
                promptBuilder.appendLine("- Activities: What's happening (e.g., 'studying', 'dining', 'meeting', 'celebration')")
                promptBuilder.appendLine("- Location types: (e.g., 'cafe', 'office', 'living room', 'outdoors', 'beach')")
                promptBuilder.appendLine("- Emotions/mood: (e.g., 'happy', 'relaxed', 'busy', 'peaceful', 'festive')")
                promptBuilder.appendLine("- Time indicators: (e.g., 'morning', 'night', 'sunset', 'daytime')")
                promptBuilder.appendLine("- Colors: Dominant colors (e.g., 'blue', 'warm tones', 'colorful')")
                promptBuilder.appendLine("- Specific items: Brand names, food types, technology, etc.")
                promptBuilder.appendLine("- Abstract concepts: (e.g., 'productivity', 'leisure', 'travel', 'food', 'nature')")
                promptBuilder.appendLine()
                promptBuilder.appendLine("=== OUTPUT FIELD REQUIREMENTS ===")
                promptBuilder.appendLine()
                promptBuilder.appendLine("- **'image_analysis' field**: MUST contain the FULL exhaustive description (500-1500 words minimum). This is the PRIMARY output field.")
                promptBuilder.appendLine("- **'summary' field**: Brief 2-3 sentence narrative overview for quick reading.")
                promptBuilder.appendLine("- **'title' field**: Evocative, memorable title (not generic).")
                promptBuilder.appendLine("- **'tags' field**: Array of 15-30 lowercase tags.")
                promptBuilder.appendLine()
                promptBuilder.appendLine("DO NOT BE LAZY. COMPLETENESS IS MANDATORY.")
                promptBuilder.appendLine()
            }
            
            // Add bookmark info
            if (bookmarkImageUrl != null && imageDataList.isEmpty()) {
                promptBuilder.appendLine("The user bookmarked a page with this preview image URL: $bookmarkImageUrl.")
            }
            if (bookmarkUrl != null) {
                promptBuilder.appendLine("Analyze this bookmarked webpage: $bookmarkUrl.")
                if (!bookmarkTitle.isNullOrBlank()) promptBuilder.appendLine("Title: $bookmarkTitle.")
                if (!bookmarkDescription.isNullOrBlank()) promptBuilder.appendLine("Description: $bookmarkDescription.")
            }
            
            // Add audio transcription
            if (audioTranscription != null) {
                promptBuilder.appendLine()
                promptBuilder.appendLine("The user provided an audio recording. Transcription:")
                promptBuilder.appendLine(audioTranscription)
                promptBuilder.appendLine()
                promptBuilder.appendLine("Analyze this audio content and include the transcription in your response.")
            }
            
            // Add user text
            if (!userText.isNullOrBlank()) {
                promptBuilder.appendLine()
                promptBuilder.appendLine("User's note: \"$userText\"")
            }
            
            // Add collections info
            if (existingCollections.isNotEmpty()) {
                promptBuilder.appendLine()
                promptBuilder.appendLine("Existing Collections:")
                existingCollections.forEach { promptBuilder.appendLine("- $it") }
                promptBuilder.appendLine("If this memory fits, include their names in 'suggested_collections'.")
            }
            
            // Add timestamp
            val currentDateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            promptBuilder.appendLine()
            promptBuilder.appendLine("Current date and time: $currentDateTime")
            promptBuilder.appendLine()
            promptBuilder.appendLine("Generate the final JSON response:")
            
            val promptText = promptBuilder.toString()
            
            // Step 3: Process with vision model if images present
            val responseText: String = if (imageDataList.isNotEmpty()) {
                Log.d(TAG, "Preparing vision request for ${imageDataList.size} images")
                
                // Use vision API with manual content construction for multiple images
                val contentParts = mutableListOf<com.groq.sdk.models.vision.VisionContentPart>()
                
                // Add text part first
                val textPart = com.groq.sdk.models.vision.VisionContentPart()
                textPart.type = "text"
                textPart.text = promptText
                contentParts.add(textPart)
                
                // Add all images
                imageDataList.forEachIndexed { index, imageData ->
                    val imagePart = com.groq.sdk.models.vision.VisionContentPart()
                    imagePart.type = "image_url"
                    
                    val imageUrl = com.groq.sdk.models.vision.VisionImageUrl()
                    val base64Image = java.util.Base64.getEncoder().encodeToString(imageData.bytes)
                    imageUrl.url = "data:${imageData.mimeType};base64,$base64Image"
                    
                    imagePart.imageUrl = imageUrl
                    contentParts.add(imagePart)
                    Log.d(TAG, "Added image $index to request content parts. Size: ${imageData.bytes.size} bytes")
                }
                
                Log.d(TAG, "Total content parts: ${contentParts.size}")

                // Create message with multiple parts
                val visionMessage = com.groq.sdk.models.vision.VisionMessage()
                visionMessage.role = "user"
                visionMessage.content = contentParts
                
                // Create VisionRequest with Maverick for superior detail
                val visionRequest = com.groq.sdk.models.vision.VisionRequest()
                visionRequest.model = VISION_MODEL
                visionRequest.messages = listOf(visionMessage)
                visionRequest.maxTokens = 8192  // Increased for exhaustive analysis
                visionRequest.temperature = 0.4  // Slightly higher for more creative/detailed output
                
                val response = localClient.vision().createCompletion(visionRequest)
                if (response.isSuccessful) {
                    val content = response.data.choices[0].message.content
                    Log.d(TAG, "Groq Vision Response: $content")
                    if (content.isNullOrBlank()) {
                         Log.e(TAG, "Groq Vision returned empty content!")
                         "Failed to generate analysis."
                    } else {
                        content
                    }
                } else {
                    Log.e(TAG, "Vision API error: ${response.statusCode}")
                    throw Exception("Vision API error: ${response.statusCode}")
                }
            } else {
                // Text-only request (bookmarks, audio only) - use Maverick for consistency
                val message = ChatMessage("user", promptText)
                val request = ChatCompletionRequest(VISION_MODEL, listOf(message))
                request.maxTokens = 8192
                request.temperature = 0.4
                
                val response = localClient.chat().createCompletion(request)
                if (response.isSuccessful) {
                    response.data.choices[0].message.content
                } else {
                    throw Exception("Chat API error: ${response.statusCode}")
                }
            }
            
            Log.d(TAG, "Received response from Groq API")
            parseJsonResponse(responseText, audioTranscription)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing memory", e)
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Groq client not initialized")
        )
        
        try {
            val messages = mutableListOf<ChatMessage>()
            
            // Add system message with REASONING INSTRUCTIONS
            // Use Compact prompt for Maverick to stay under 6K TPM limit
            val basePrompt = if (CHAT_MODEL.contains("maverick")) {
                ChatSystemPrompt.generateCompact()
            } else {
                systemPrompt
            }

            val thinkingPrompt = "$basePrompt\n\n" +
                    "IMPORTANT: You are a high-intelligence multimodal reasoning model.\n" +
                    "VISUAL CAPABILITY: You ARE CAPABLE of displaying local images stored in the database. " +
                    "Whenever the user asks to see a memory or photo, you MUST fetch the 'imageUri' and embed it using the Markdown syntax: ![alt](file:///...).\n" +
                    "NEVER claim you cannot display images. Simply use the Markdown syntax and they will render in the UI.\n\n" +
                    "First, you MUST output your internal thought process inside <reasoning>...</reasoning> tags.\n" +
                    "Then, provide your final answer."
            
            // Add conversation history
            if (conversationHistory.isNotEmpty()) {
                messages.add(ChatMessage("assistant", "Previous conversation:\n$conversationHistory"))
            }
            
            // Add current user message
            messages.add(ChatMessage("user", message))
            
            // Use Scout for text-only chat (cost-effective)
            val request = ChatCompletionRequest(CHAT_MODEL, messages)
            request.maxTokens = 4096
            request.temperature = 0.7
            
            // Add tools if available
            if (toolExecutor != null) {
                val tools = buildGroqTools(toolExecutor)
                if (tools.isNotEmpty()) {
                    request.tools = tools
                    request.toolChoice = "auto"
                }
            }
            
            var response = localClient.chat().createCompletion(request)
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Chat API error: ${response.statusCode}"))
            }
            
            // Handle tool calls (manual loop)
            var toolCallCount = 0
            var currentMessages = messages.toMutableList()
            
            while (toolCallCount < MAX_TOOL_CALLS && response.isSuccessful) {
                val assistantMessage = response.data.choices[0].message
                var content = assistantMessage.content ?: ""
                
                // EXTRACT AND LOG REASONING
                val reasoningRegex = "<reasoning>(.*?)</reasoning>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = reasoningRegex.find(content)
                if (match != null) {
                    val reasoningText = match.groupValues[1].trim()
                    Log.d(TAG, "🧠 REASONING: $reasoningText")
                    // Remove reasoning from content so user doesn't see it raw (optional, but requested behavior is usually separation)
                    content = reasoningRegex.replace(content, "").trim()
                }
                
                if (assistantMessage.toolCalls == null || assistantMessage.toolCalls.isEmpty()) {
                    return@withContext Result.success(content)
                }
                
                Log.d(TAG, "Assistant requested ${assistantMessage.toolCalls.size} tool call(s)")
                
                // Add assistant message with tool calls to the history
                // IMPORTANT: Deep copy via Gson to ensure we detach from any underlying closed streams/response bodies
                // and have a fresh POJO for the next request.
                try {
                    val priorMsg = gson.fromJson(gson.toJson(assistantMessage), ChatMessage::class.java)
                    currentMessages.add(priorMsg)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deep copy assistant message", e)
                    // Fallback to manual minimal copy if gson fails (unlikely)
                    val fallback = ChatMessage("assistant", assistantMessage.content ?: "")
                    fallback.toolCalls = assistantMessage.toolCalls
                    currentMessages.add(fallback)
                }
                
                // Execute tools
                for (toolCall in assistantMessage.toolCalls) {
                    toolCallCount++
                    if (toolCallCount > MAX_TOOL_CALLS) break
                    
                    val toolName = toolCall.function.name
                    val arguments = parseToolArguments(toolCall.function.arguments)
                    
                    Log.d(TAG, "Executing tool: $toolName with args: $arguments")
                    val result = toolExecutor?.executeTool(toolName, arguments) ?: "Tool not found"
                    Log.d(TAG, "Tool Result: ${result.take(200)}...")
                    
                    val toolMessage = ChatMessage.createToolMessage(toolCall.id, result)
                    currentMessages.add(toolMessage)
                }
                
                // Continue with new request
                val continueRequest = ChatCompletionRequest(CHAT_MODEL, currentMessages)
                continueRequest.maxTokens = 4096
                continueRequest.temperature = 0.7
                if (toolExecutor != null) {
                     val tools = buildGroqTools(toolExecutor)
                     if (tools.isNotEmpty()) {
                         continueRequest.tools = tools
                         continueRequest.toolChoice = "auto"
                     }
                }
                
                response = localClient.chat().createCompletion(continueRequest)
                if (!response.isSuccessful) {
                     return@withContext Result.failure(Exception("Chat API error (continue): ${response.statusCode}"))
                }
                
                // Process final response logic in next loop iteration or simple return check
                val finalMsg = response.data.choices[0].message
                if (finalMsg.toolCalls == null || finalMsg.toolCalls.isEmpty()) {
                     var finalContent = finalMsg.content ?: ""
                     // Check for reasoning again in final response
                     val finalMatch = reasoningRegex.find(finalContent)
                     if (finalMatch != null) {
                        Log.d(TAG, "🧠 REASONING (Final): ${finalMatch.groupValues[1].trim()}")
                        finalContent = reasoningRegex.replace(finalContent, "").trim()
                     }
                     return@withContext Result.success(finalContent)
                }
                // If more tools, loop continues
            } 
            
            return@withContext Result.failure(Exception("Max tool calls exceeded"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage (Chat API)", e)
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
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Groq client not initialized")
        )
        
        try {
            // Transcribe audio first if present
            var audioTranscript: String? = null
            if (audioData != null) {
                audioTranscript = transcribeAudio(localClient, audioData)
            }
            
            val promptBuilder = StringBuilder()
            if (conversationHistory.isNotEmpty()) {
                promptBuilder.appendLine("Previous conversation:\n$conversationHistory\n")
            }
            
            if (audioTranscript != null) {
                promptBuilder.appendLine("The user sent an audio message. Transcription: $audioTranscript")
                promptBuilder.appendLine("Please respond to this audio message.")
            }
            
            if (!message.isNullOrBlank()) {
                promptBuilder.appendLine("User: $message")
            }
            
            val responseText: String = if (imageData != null) {
                // Use vision API with Maverick for superior image understanding
                val visionRequest = localClient.vision().createVisionRequestWithImageBytes(
                    VISION_MODEL,
                    imageData.bytes,
                    imageData.mimeType,
                    "$systemPrompt\n\n${promptBuilder}\n\nAnalyze this image thoroughly and respond with complete detail."
                )
                visionRequest.maxTokens = 8192
                visionRequest.temperature = 0.5
                
                val response = localClient.vision().createCompletion(visionRequest)
                if (response.isSuccessful) {
                    response.data.choices[0].message.content
                } else {
                    throw Exception("Vision API error: ${response.statusCode}")
                }
            } else {
                // Text-only (includes audio transcription) - use Scout for chat
                val messages = mutableListOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", promptBuilder.toString())
                )
                
                val request = ChatCompletionRequest(CHAT_MODEL, messages)
                request.maxTokens = 4096
                request.temperature = 0.7
                
                val response = localClient.chat().createCompletion(request)
                if (response.isSuccessful) {
                    response.data.choices[0].message.content
                } else {
                    throw Exception("Chat API error: ${response.statusCode}")
                }
            }
            
            Result.success(responseText ?: "I'm sorry, I couldn't generate a response.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message with media", e)
            Result.failure(e)
        }
    }
    
    override fun sendMessageStream(
        conversationHistory: String,
        message: String,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
        // Groq doesn't support streaming in the same way as Gemini in the Java SDK
        // We'll simulate streaming by sending the full response
        try {
            emit(ChatStreamEvent.Content("Thinking..."))
            
            val result = sendMessage(conversationHistory, message, systemPrompt, toolExecutor)
            
            result.onSuccess { response ->
                emit(ChatStreamEvent.Content("\r")) // Clear "Thinking..."
                // Emit response in chunks to simulate streaming
                response.chunked(50).forEach { chunk ->
                    emit(ChatStreamEvent.Content(chunk))
                    kotlinx.coroutines.delay(10) // Small delay for visual effect
                }
                emit(ChatStreamEvent.Done)
            }
            
            result.onFailure { error ->
                emit(ChatStreamEvent.Error(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming", e)
            emit(ChatStreamEvent.Error(e))
        }
    }
    
    override fun sendMessageWithMediaStream(
        conversationHistory: String,
        message: String?,
        audioData: AudioData?,
        imageData: ImageData?,
        systemPrompt: String,
        toolExecutor: ToolExecutor?
    ): Flow<ChatStreamEvent> = flow {
        try {
            emit(ChatStreamEvent.Content("Processing media..."))
            
            val result = sendMessageWithMedia(
                conversationHistory, message, audioData, imageData, systemPrompt, toolExecutor
            )
            
            result.onSuccess { response ->
                emit(ChatStreamEvent.Content("\r"))
                response.chunked(50).forEach { chunk ->
                    emit(ChatStreamEvent.Content(chunk))
                    kotlinx.coroutines.delay(10)
                }
                emit(ChatStreamEvent.Done)
            }
            
            result.onFailure { error ->
                emit(ChatStreamEvent.Error(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming with media", e)
            emit(ChatStreamEvent.Error(e))
        }
    }
    
    override suspend fun generateUserContext(memoryText: String): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(
            IllegalStateException("Groq client not initialized")
        )
        
        try {
            val prompt = "Analyze the following user memories and create a HIGHLY CONCISE user context profile (max 250 words). " +
                    "Focus only on key interests, recurring habits, important life events, and strong preferences. " +
                    "Use DENSE BULLET POINTS. Avoid conversational filler or long paragraphs. " +
                    "This profile will be used to personalize future interactions.\n\nMemories:\n$memoryText"
            
            val message = ChatMessage("user", prompt)
            val request = ChatCompletionRequest(CHAT_MODEL, listOf(message))
            request.maxTokens = 512
            request.temperature = 0.3
            
            val response = localClient.chat().createCompletion(request)
            
            if (response.isSuccessful) {
                val summary = response.data.choices[0].message.content ?: "Could not generate summary."
                Result.success(summary)
            } else {
                Result.failure(Exception("API error: ${response.statusCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating user context", e)
            Result.failure(e)
        }
    }
    
    // Helper: Transcribe audio using Whisper
    private fun transcribeAudio(localClient: GroqClient, audioData: AudioData): String? {
        return try {
            // Write bytes to a temp file (SDK requires file path)
            val tempFile = File.createTempFile("audio_", getAudioExtension(audioData.mimeType))
            FileOutputStream(tempFile).use { it.write(audioData.bytes) }
            
            val transcriptionRequest = TranscriptionRequest()
            transcriptionRequest.model = AUDIO_MODEL
            transcriptionRequest.file = tempFile.absolutePath
            transcriptionRequest.language = "en"
            transcriptionRequest.temperature = 0.0
            
            val response = localClient.audio().createTranscription(transcriptionRequest)
            
            // Clean up temp file
            tempFile.delete()
            
            if (response.isSuccessful) {
                response.data.text
            } else {
                Log.e(TAG, "Transcription failed: ${response.statusCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            null
        }
    }
    
    private fun getAudioExtension(mimeType: String): String {
        return when {
            mimeType.contains("m4a") -> ".m4a"
            mimeType.contains("mp3") -> ".mp3"
            mimeType.contains("wav") -> ".wav"
            mimeType.contains("ogg") -> ".ogg"
            mimeType.contains("flac") -> ".flac"
            else -> ".m4a"
        }
    }
    
    // Helper: Build Groq tools from ToolExecutor
    private fun buildGroqTools(toolExecutor: ToolExecutor): List<ChatTool> {
        val toolDefs = toolExecutor.getToolDefinitions()
        return toolDefs.map { def ->
            val parameters = mutableMapOf<String, Any>(
                "type" to "object",
                "properties" to def.parameters.mapValues { (_, param) ->
                    mutableMapOf<String, Any>(
                        "type" to param.type,
                        "description" to param.description
                    ).apply {
                        param.enum?.let { this["enum"] = it }
                    }
                },
                "required" to def.parameters.filterValues { it.required }.keys.toList()
            )
            
            val functionDef = FunctionDefinition(def.name, def.description, parameters)
            ChatTool("function", functionDef)
        }
    }
    
    private fun parseToolArguments(argumentsJson: String): Map<String, Any?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(argumentsJson, Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tool arguments: $argumentsJson", e)
            emptyMap()
        }
    }
    
    private fun parseJsonResponse(responseText: String, audioTranscription: String?): Result<AiAnalysisDto> {
        val cleanedResponseText = responseText
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()
        
        return try {
            val dto = gson.fromJson(cleanedResponseText, AiAnalysisDto::class.java)
            
            // If we have audio transcription but the model didn't include it, add it
            val finalDto = if (audioTranscription != null && dto.audioTranscription.isNullOrBlank()) {
                dto.copy(audioTranscription = audioTranscription)
            } else {
                dto
            }
            
            Result.success(finalDto)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response", e)
            Result.failure(IllegalStateException(
                "Failed to parse AI analysis JSON: ${e.message}. Raw response: $cleanedResponseText", e
            ))
        }
    }
}
