package com.example.memgallery.data.remote

import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object ChatTools {
    lateinit var memoryDao: MemoryDao
    lateinit var collectionDao: CollectionDao
    lateinit var searchClient: com.google.genai.Client

    @JvmStatic
    fun webSearch(query: String): String {
        return runBlocking {
            try {
                // Use the searchClient to perform the search
                // We need to configure it with the GoogleSearch tool here or assume it's already configured?
                // The Client itself holds the API key. The tools are passed in the request config.
                // So we need to build the config here.
                val tool = com.google.genai.types.Tool.builder()
                    .googleSearch(com.google.genai.types.GoogleSearch.builder().build())
                    .build()
                
                val config = com.google.genai.types.GenerateContentConfig.builder()
                    .tools(tool)
                    .build()

                val response = searchClient.models.generateContent("gemini-2.5-flash", query, config)
                response.text() ?: "No results found."
            } catch (e: Exception) {
                "Error searching web: ${e.message}"
            }
        }
    }

    @JvmStatic
    fun searchMemories(query: String): String {
        // We need to run this synchronously as the SDK expects a return value
        return runBlocking {
            try {
                // For now, we just fetch all memories and filter simply, 
                // or if we had FTS we would use that. 
                // Since we don't have FTS yet, we'll fetch all and filter by text.
                // In a real app with many memories, we should use FTS or Vector Search.
                val allMemories = memoryDao.getAllMemories().first()
                val filtered = allMemories.filter { 
                    (it.userText?.contains(query, ignoreCase = true) == true) ||
                    (it.aiSummary?.contains(query, ignoreCase = true) == true) ||
                    (it.aiTitle?.contains(query, ignoreCase = true) == true) ||
                    (it.aiTags?.any { tag -> tag.contains(query, ignoreCase = true) } == true)
                }.take(10) // Limit to 10 relevant memories

                if (filtered.isEmpty()) {
                    return@runBlocking "No memories found matching '$query'."
                }

                filtered.joinToString("\n\n") { memory ->
                    "ID: ${memory.id}\nTitle: ${memory.aiTitle}\nSummary: ${memory.aiSummary}\nDate: ${java.time.Instant.ofEpochMilli(memory.creationTimestamp)}"
                }
            } catch (e: Exception) {
                "Error searching memories: ${e.message}"
            }
        }
    }

    @JvmStatic
    fun getCollectionMemories(collectionName: String): String {
        return runBlocking {
            try {
                // 1. Find collection by name
                val collection = collectionDao.getCollectionByName(collectionName)
                    ?: return@runBlocking "Collection '$collectionName' not found."

                // 2. Get memories for collection
                val memories = collectionDao.getMemoriesForCollection(collection.id).first()
                
                if (memories.isEmpty()) {
                    return@runBlocking "No memories found in collection '$collectionName'."
                }

                memories.joinToString("\n\n") { memory ->
                     "ID: ${memory.id}\nTitle: ${memory.aiTitle}\nSummary: ${memory.aiSummary}\nDate: ${java.time.Instant.ofEpochMilli(memory.creationTimestamp)}"
                }
            } catch (e: Exception) {
                "Error getting collection memories: ${e.message}"
            }
        }
    }
}
