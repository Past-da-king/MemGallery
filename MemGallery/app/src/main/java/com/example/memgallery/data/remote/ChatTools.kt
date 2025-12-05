package com.example.memgallery.data.remote

import android.util.Log
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ChatTools"

object ChatTools {
    lateinit var memoryDao: MemoryDao
    lateinit var collectionDao: CollectionDao
    lateinit var taskDao: TaskDao
    lateinit var searchClient: com.google.genai.Client
    
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    /**
     * Unified database query function for AI read-only access.
     * 
     * @param table - "memories", "tasks", or "collections"
     * @param filters - JSON string with optional filters:
     *   - id: Int - Get specific record by ID
     *   - search: String - Text search in title/summary/tags
     *   - dateFrom/dateTo: String (YYYY-MM-DD) - Date range filter
     *   - completed: Boolean - For tasks only
     *   - dueDate: String - For tasks only
     *   - priority: String - For tasks (LOW/MEDIUM/HIGH)
     *   - collectionName: String - Get memories in a collection
     *   - limit: Int - Max results (default 20)
     * @param fields - "all" or comma-separated field names like "id,title,summary"
     */
    @JvmStatic
    fun queryDatabase(table: String, filters: String, fields: String): String {
        Log.d(TAG, "queryDatabase called: table=$table, filters=$filters, fields=$fields")
        return runBlocking {
            try {
                val filterMap = parseFilters(filters)
                val limit = (filterMap["limit"] as? Number)?.toInt() ?: 20
                
                val result = when (table.lowercase()) {
                    "memories" -> queryMemories(filterMap, fields, limit)
                    "tasks" -> queryTasks(filterMap, fields, limit)
                    "collections" -> queryCollections(filterMap, fields, limit)
                    else -> "Unknown table: $table. Valid tables: 'memories', 'tasks', 'collections'."
                }
                Log.d(TAG, "Query result length: ${result.length}")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Query error", e)
                "Query error: ${e.message}"
            }
        }
    }

    private suspend fun queryMemories(filters: Map<String, Any?>, fields: String, limit: Int): String {
        var memories = memoryDao.getAllMemories().first()
        
        // Filter by ID
        filters["id"]?.let { id -> 
            val targetId = (id as Number).toInt()
            memories = memories.filter { it.id == targetId }
        }
        
        // Text search
        filters["search"]?.let { query ->
            val q = query.toString().lowercase()
            memories = memories.filter { m ->
                m.aiTitle?.lowercase()?.contains(q) == true ||
                m.aiSummary?.lowercase()?.contains(q) == true ||
                m.userText?.lowercase()?.contains(q) == true ||
                m.aiTags?.any { tag -> tag.lowercase().contains(q) } == true ||
                m.aiImageAnalysis?.lowercase()?.contains(q) == true ||
                m.aiAudioTranscription?.lowercase()?.contains(q) == true
            }
        }
        
        // Date range filter
        filters["dateFrom"]?.let { date ->
            val ts = parseDate(date.toString())
            memories = memories.filter { it.creationTimestamp >= ts }
        }
        filters["dateTo"]?.let { date ->
            val ts = parseDate(date.toString()) + 86400000 // Include end date
            memories = memories.filter { it.creationTimestamp <= ts }
        }
        
        // Collection filter
        filters["collectionName"]?.let { name ->
            val collection = collectionDao.getCollectionByName(name.toString())
            if (collection != null) {
                val collectionMemories = collectionDao.getMemoriesForCollection(collection.id).first()
                val ids = collectionMemories.map { it.id }.toSet()
                memories = memories.filter { it.id in ids }
            } else {
                return "Collection '$name' not found."
            }
        }
        
        memories = memories.take(limit)
        if (memories.isEmpty()) return "No memories found matching the criteria."
        
        return formatMemories(memories, fields)
    }

    private suspend fun queryTasks(filters: Map<String, Any?>, fields: String, limit: Int): String {
        var tasks = taskDao.getAllTasks().first()
        
        // Filter by ID
        filters["id"]?.let { id ->
            val targetId = (id as Number).toInt()
            tasks = tasks.filter { it.id == targetId }
        }
        
        // Completion status
        filters["completed"]?.let { completed ->
            val isCompleted = completed.toString().toBoolean()
            tasks = tasks.filter { it.isCompleted == isCompleted }
        }
        
        // Due date
        filters["dueDate"]?.let { date ->
            tasks = tasks.filter { it.dueDate == date.toString() }
        }
        
        // Priority
        filters["priority"]?.let { priority ->
            tasks = tasks.filter { it.priority.equals(priority.toString(), ignoreCase = true) }
        }
        
        // Type (TODO/EVENT)
        filters["type"]?.let { type ->
            tasks = tasks.filter { it.type.equals(type.toString(), ignoreCase = true) }
        }
        
        tasks = tasks.take(limit)
        if (tasks.isEmpty()) return "No tasks found matching the criteria."
        
        return formatTasks(tasks, fields)
    }

    private suspend fun queryCollections(filters: Map<String, Any?>, fields: String, limit: Int): String {
        val collections = collectionDao.getAllCollections().first().take(limit)
        if (collections.isEmpty()) return "No collections found."
        
        return collections.joinToString("\n\n") { c ->
            buildString {
                appendLine("**${c.name}** (ID: ${c.id})")
                appendLine("Description: ${c.description}")
            }
        }
    }

    // Format memories based on requested fields
    private fun formatMemories(memories: List<MemoryEntity>, fields: String): String {
        val showAll = fields.equals("all", ignoreCase = true)
        val fieldList = if (showAll) emptyList() else fields.split(",").map { it.trim().lowercase() }
        
        return memories.joinToString("\n\n---\n\n") { m ->
            buildString {
                appendLine("**${m.aiTitle ?: "Untitled Memory"}** (ID: ${m.id})")
                appendLine("Created: ${formatDate(m.creationTimestamp)} | Type: ${m.type} | Status: ${m.status}")
                
                if (showAll || "summary" in fieldList) {
                    m.aiSummary?.let { appendLine("\n**Summary:** $it") }
                }
                if (showAll || "tags" in fieldList) {
                    m.aiTags?.let { appendLine("**Tags:** ${it.joinToString(", ")}") }
                }
                if (showAll || "usertext" in fieldList || "note" in fieldList) {
                    m.userText?.let { appendLine("\n**User Note:** $it") }
                }
                if (showAll || "imageanalysis" in fieldList || "image" in fieldList) {
                    m.aiImageAnalysis?.let { appendLine("\n**Image Analysis:** $it") }
                }
                if (showAll || "transcription" in fieldList || "audio" in fieldList) {
                    m.aiAudioTranscription?.let { appendLine("\n**Audio Transcription:** $it") }
                }
                if (showAll || "bookmark" in fieldList || "url" in fieldList) {
                    m.bookmarkUrl?.let { 
                        appendLine("\n**Bookmark:** $it")
                        m.bookmarkTitle?.let { t -> appendLine("Title: $t") }
                        m.bookmarkDescription?.let { d -> appendLine("Description: $d") }
                    }
                }
            }
        }
    }

    // Format tasks based on requested fields
    private fun formatTasks(tasks: List<TaskEntity>, fields: String): String {
        return tasks.joinToString("\n\n") { t ->
            buildString {
                val checkbox = if (t.isCompleted) "[x]" else "[ ]"
                append("$checkbox **${t.title}** (ID: ${t.id})")
                appendLine()
                append("Due: ${t.dueDate ?: "No date"}")
                t.dueTime?.let { append(" at $it") }
                append(" | Priority: ${t.priority} | Type: ${t.type}")
                if (t.description.isNotBlank()) {
                    appendLine()
                    append("Description: ${t.description}")
                }
            }
        }
    }

    /**
     * Web search using Google Search via Gemini.
     */
    @JvmStatic
    fun webSearch(query: String): String {
        Log.d(TAG, "webSearch called: query=$query")
        return runBlocking {
            try {
                val tool = com.google.genai.types.Tool.builder()
                    .googleSearch(com.google.genai.types.GoogleSearch.builder().build())
                    .build()
                
                val config = com.google.genai.types.GenerateContentConfig.builder()
                    .tools(tool)
                    .build()

                val response = searchClient.models.generateContent("gemini-2.5-flash", query, config)
                response.text() ?: "No results found."
            } catch (e: Exception) {
                Log.e(TAG, "Web search error", e)
                "Error searching web: ${e.message}"
            }
        }
    }

    // Helper functions
    private fun parseFilters(json: String): Map<String, Any?> {
        return try {
            if (json.isBlank() || json == "{}") {
                emptyMap()
            } else {
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(json, Map::class.java) as? Map<String, Any?> ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse filters: $json", e)
            emptyMap()
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        return displayDateFormat.format(Date(timestamp))
    }
    
    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
