package com.example.memgallery.data.remote

import android.content.Context
import android.util.Log
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatGeminiService"

@Singleton
class ChatGeminiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val memoryDao: MemoryDao,
    private val collectionDao: CollectionDao,
    private val chatDao: ChatDao
) {
    private var client: Client? = null

    init {
        // Initialize ChatTools with DAOs
        ChatTools.memoryDao = memoryDao
        ChatTools.collectionDao = collectionDao
    }

    fun initialize(apiKey: String) {
        client = Client.builder().apiKey(apiKey).build()
        
        // Initialize the search client for ChatTools
        // We reuse the API key to create a separate client for search operations
        try {
             val searchClient = Client.builder().apiKey(apiKey).build()
             // We can't check if lateinit is initialized easily, but we can assign it.
             // Ideally we should check if it's already assigned to avoid re-creation if not needed,
             // but re-creating the client is cheap enough here.
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
            
            // 2. Build Context (System Instruction)
            val userContext = settingsRepository.userContextSummaryFlow.first()
            val baseInstruction = "You are a helpful AI assistant with access to the user's memories. " +
                    "Use the provided tools to search for memories or collections when asked. " +
                    "If the user asks for current information or something you don't know, use the 'webSearch' tool. " +
                    "Always answer based on the user's context if available."
            
            val systemInstruction = if (userContext.isNotBlank()) {
                "$baseInstruction\n\nUser Context:\n$userContext"
            } else {
                baseInstruction
            }

            // 3. Prepare Tools
            // Reflection for ChatTools methods
            val searchMemoriesMethod = ChatTools::class.java.getMethod("searchMemories", String::class.java)
            val getCollectionMemoriesMethod = ChatTools::class.java.getMethod("getCollectionMemories", String::class.java)
            val webSearchMethod = ChatTools::class.java.getMethod("webSearch", String::class.java)

            val tools = Tool.builder()
                //.googleSearch(GoogleSearch.builder().build()) // Removed integrated Google Search
                .functions(searchMemoriesMethod, getCollectionMemoriesMethod, webSearchMethod)
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
            
            // 7. Save User Message and AI Response to DB
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "user", content = message))
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "model", content = responseText))

            Result.success(responseText)

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage", e)
            Result.failure(e)
        }
    }
}
