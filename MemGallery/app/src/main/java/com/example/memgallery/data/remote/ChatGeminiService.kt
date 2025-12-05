package com.example.memgallery.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.repository.SettingsRepository
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatGeminiService"

@Singleton
class ChatGeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val memoryDao: MemoryDao,
    private val collectionDao: CollectionDao,
    private val chatDao: ChatDao,
    private val taskDao: TaskDao
) {
    private var client: Client? = null

    init {
        // Initialize ChatTools with DAOs
        ChatTools.memoryDao = memoryDao
        ChatTools.collectionDao = collectionDao
        ChatTools.taskDao = taskDao
    }

    fun initialize(apiKey: String) {
        client = Client.builder().apiKey(apiKey).build()
        
        // Initialize the search client for ChatTools
        try {
            val searchClient = Client.builder().apiKey(apiKey).build()
            ChatTools.searchClient = searchClient
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing search client", e)
        }
    }

    suspend fun generateUserContext(): Result<String> = withContext(Dispatchers.IO) {
        val localClient = client ?: return@withContext Result.failure(IllegalStateException("Gemini client not initialized"))

        try {
            val memories = memoryDao.getAllMemories().first()
            if (memories.isEmpty()) return@withContext Result.success("No memories available yet.")

            val memoryText = memories.joinToString("\n\n") { memory ->
                "Title: ${memory.aiTitle}\nSummary: ${memory.aiSummary}\nTags: ${memory.aiTags?.joinToString()}"
            }

            val prompt = "Analyze the following user memories and create a detailed user context profile. " +
                    "Summarize their interests, habits, important events, and preferences based on these memories. " +
                    "This profile will be used to personalize future interactions.\n\nMemories:\n$memoryText"

            val response = localClient.models.generateContent("gemini-2.5-flash", prompt, null)
            val summary = response.text() ?: "Could not generate summary."

            settingsRepository.saveUserContextSummary(summary)
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: Int, message: String): Result<String> = withContext(Dispatchers.IO) {
        if (client == null) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (!apiKey.isNullOrBlank()) {
                initialize(apiKey)
            }
        }
        val localClient = client ?: return@withContext Result.failure(IllegalStateException("Gemini client not initialized"))

        try {
            Log.d(TAG, "Sending message: $message")
            
            // 1. Get Chat History
            val history = chatDao.getMessagesForChat(chatId).first()
            
            // 2. Build System Instruction with Query Tool Documentation
            val userContext = settingsRepository.userContextSummaryFlow.first()
            
            val baseInstruction = """
You are a helpful AI assistant with FULL READ ACCESS to the user's personal data.

## QUERY TOOL
Use `queryDatabase(table, filters, fields)` to query any data:

**Parameters:**
- `table`: "memories", "tasks", or "collections"
- `filters`: JSON string with optional filters
- `fields`: "all" or comma-separated like "id,title,summary"

**Available Filters:**
| Filter | Type | Description |
|--------|------|-------------|
| id | Int | Get specific record |
| search | String | Text search in content |
| dateFrom | String | Start date (YYYY-MM-DD) |
| dateTo | String | End date (YYYY-MM-DD) |
| completed | Boolean | Task completion status |
| dueDate | String | Task due date (YYYY-MM-DD) |
| priority | String | Task priority (LOW/MEDIUM/HIGH) |
| collectionName | String | Memories in collection |
| limit | Int | Max results (default 20) |

**Examples:**
- All memories: `queryDatabase("memories", "{}", "all")`
- Search "meeting": `queryDatabase("memories", "{\"search\": \"meeting\"}", "all")`
- Memory #5: `queryDatabase("memories", "{\"id\": 5}", "all")`
- Today's tasks: `queryDatabase("tasks", "{\"dueDate\": \"2024-12-05\"}", "all")`
- Pending tasks: `queryDatabase("tasks", "{\"completed\": false}", "all")`
- All collections: `queryDatabase("collections", "{}", "all")`

## WEB SEARCH
Use `webSearch(query)` for current web information.

## GUIDELINES
- Query data before answering questions about user's memories/tasks
- Use markdown formatting in responses
- Be helpful and conversational
""".trimIndent()
            
            val systemInstruction = if (userContext.isNotBlank()) {
                "$baseInstruction\n\n## USER CONTEXT\n$userContext"
            } else {
                baseInstruction
            }

            // 3. Prepare Tools (just 2 now: queryDatabase and webSearch)
            val queryDatabaseMethod = ChatTools::class.java.getMethod(
                "queryDatabase", 
                String::class.java, 
                String::class.java, 
                String::class.java
            )
            val webSearchMethod = ChatTools::class.java.getMethod("webSearch", String::class.java)

            val tools = Tool.builder()
                .functions(queryDatabaseMethod, webSearchMethod)
                .build()

            val config = GenerateContentConfig.builder()
                .tools(tools)
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .build()
            
            // 4. Build Content History
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            val fullPrompt = if (historyText.isNotEmpty()) {
                "Previous conversation:\n$historyText\n\nUser: $message"
            } else {
                message
            }

            // 5. Send Request
            val response = localClient.models.generateContent("gemini-2.5-flash", fullPrompt, config)
            
            val responseText = response.text() ?: "I'm sorry, I couldn't generate a response."
            Log.d(TAG, "Received response: $responseText")
            
            // 6. Save AI Response to DB (user message already saved by ViewModel)
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "model", content = responseText))

            Result.success(responseText)

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage", e)
            Result.failure(e)
        }
    }

    /**
     * Send a message with optional media attachments (audio, image, PDF)
     * Uses the same pattern as GeminiService.processMemory for handling media
     */
    suspend fun sendMessageWithMedia(
        chatId: Int,
        message: String?,
        audioUri: String? = null,
        imageUri: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (client == null) {
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (!apiKey.isNullOrBlank()) {
                initialize(apiKey)
            }
        }
        val localClient = client ?: return@withContext Result.failure(IllegalStateException("Gemini client not initialized"))

        try {
            Log.d(TAG, "Sending message with media - audio: $audioUri, image: $imageUri")
            
            // 1. Get Chat History
            val history = chatDao.getMessagesForChat(chatId).first()
            
            // 2. Build System Instruction
            val userContext = settingsRepository.userContextSummaryFlow.first()
            val baseInstruction = """
You are a helpful AI assistant with FULL READ ACCESS to the user's personal data.
When the user sends you media (audio, images, documents), analyze them carefully and respond helpfully.
For audio: Transcribe and respond to the content.
For images/documents: Analyze the content and respond to any questions about it.
Use markdown formatting in responses. Be helpful and conversational.
""".trimIndent()
            
            val systemInstruction = if (userContext.isNotBlank()) {
                "$baseInstruction\n\n## USER CONTEXT\n$userContext"
            } else {
                baseInstruction
            }

            // 3. Prepare Tools
            val queryDatabaseMethod = ChatTools::class.java.getMethod(
                "queryDatabase", 
                String::class.java, 
                String::class.java, 
                String::class.java
            )
            val webSearchMethod = ChatTools::class.java.getMethod("webSearch", String::class.java)

            val tools = Tool.builder()
                .functions(queryDatabaseMethod, webSearchMethod)
                .build()

            val config = GenerateContentConfig.builder()
                .tools(tools)
                .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                .build()
            
            // 4. Build multimodal content parts
            val parts = mutableListOf<Part>()
            
            // Add chat history context
            val historyText = history.joinToString("\n") { "${it.role}: ${it.content}" }
            if (historyText.isNotEmpty()) {
                parts.add(Part.fromText("Previous conversation:\n$historyText\n\n"))
            }
            
            // Add audio if present
            if (audioUri != null) {
                Log.d(TAG, "Processing audio URI: $audioUri")
                val audioBytes = getBytesFromUri(audioUri)
                if (audioBytes != null) {
                    val parsedUri = Uri.parse(audioUri)
                    val mimeType = if (parsedUri.scheme == "content") {
                        context.contentResolver.getType(parsedUri) ?: "audio/m4a"
                    } else {
                        "audio/m4a"
                    }
                    parts.add(Part.fromBytes(audioBytes, mimeType))
                    parts.add(Part.fromText("The user sent an audio message. Please transcribe and respond to it."))
                }
            }
            
            // Add image/document if present
            if (imageUri != null) {
                Log.d(TAG, "Processing image/document URI: $imageUri")
                val parsedUri = Uri.parse(imageUri)
                val mimeType = context.contentResolver.getType(parsedUri) ?: "image/jpeg"
                
                val mediaBytes = getBytesFromUri(imageUri)
                if (mediaBytes != null) {
                    // If it's an image, resize it like GeminiService does
                    if (mimeType.startsWith("image/")) {
                        val bitmap = BitmapFactory.decodeByteArray(mediaBytes, 0, mediaBytes.size)
                        if (bitmap != null) {
                            val resizedBitmap = resizeBitmap(bitmap, 1024)
                            val outputStream = ByteArrayOutputStream()
                            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                            val resizedBytes = outputStream.toByteArray()
                            parts.add(Part.fromBytes(resizedBytes, "image/jpeg"))
                        }
                    } else {
                        // For PDFs and other documents, send as-is
                        parts.add(Part.fromBytes(mediaBytes, mimeType))
                    }
                    parts.add(Part.fromText("The user sent an attachment. Please analyze it."))
                }
            }
            
            // Add user message if present
            if (!message.isNullOrBlank()) {
                parts.add(Part.fromText("User: $message"))
            }
            
            // 5. Send Request
            val multimodalContent = Content.fromParts(*parts.toTypedArray())
            val response = localClient.models.generateContent("gemini-2.5-flash", multimodalContent, config)
            
            val responseText = response.text() ?: "I'm sorry, I couldn't generate a response."
            Log.d(TAG, "Received response: $responseText")
            
            // 6. Save AI Response to DB (user message already saved by ViewModel)
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "model", content = responseText))

            Result.success(responseText)

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessageWithMedia", e)
            Result.failure(e)
        }
    }

    private fun getBytesFromUri(uriString: String): ByteArray? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> uri.path?.let { java.io.File(it).readBytes() }
            else -> null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight = (originalHeight * (maxDimension.toFloat() / originalWidth)).toInt()
            } else {
                resizedHeight = maxDimension
                resizedWidth = (originalWidth * (maxDimension.toFloat() / originalHeight)).toInt()
            }
        }

        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }
}
