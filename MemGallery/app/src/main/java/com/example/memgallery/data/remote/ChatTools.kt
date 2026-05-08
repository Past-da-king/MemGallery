package com.example.memgallery.data.remote

import android.util.Log
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.asSharedFlow
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
    var searchClient: com.google.genai.Client? = null
    var settingsRepository: com.example.memgallery.data.repository.SettingsRepository? = null
    
    // State holder for memory IDs to display in chat UI after AI response
    var lastDisplayedMemoryIds: List<Int> = emptyList()
        private set
    
    // Tool Event System for UI Feedback
    sealed class ToolEvent {
        data class Started(val toolName: String, val input: String) : ToolEvent()
        data class Finished(val toolName: String, val result: String) : ToolEvent()
    }
    
    private val _toolEvents = kotlinx.coroutines.flow.MutableSharedFlow<ToolEvent>(extraBufferCapacity = 10)
    val toolEvents: kotlinx.coroutines.flow.SharedFlow<ToolEvent> = _toolEvents.asSharedFlow()
    
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    /**
     * Unified database query function for AI read-only access.
     */
    @JvmStatic
    fun queryDatabase(table: String, filters: String, fields: String): String {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🔧 TOOL CALL: queryDatabase")
        Log.i(TAG, "📥 INPUT: table='$table', filters='$filters', fields='$fields'")
        
        // Emit Started Event
        _toolEvents.tryEmit(ToolEvent.Started("Querying Database", "Table: $table"))
        
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
                
                // Truncate result to prevent token limit/timeout issues (max ~3000 chars)
                val truncatedResult = if (result.length > 3000) {
                    result.take(3000) + "\n...[Result truncated to save tokens. Refine query for more specifics.]"
                } else {
                    result
                }
                
                Log.i(TAG, "📤 RESULT (${truncatedResult.length} chars):")
                Log.i(TAG, truncatedResult)
                Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                // Emit Finished Event
                _toolEvents.tryEmit(ToolEvent.Finished("Querying Database", "Found ${result.length} chars of data"))
                
                truncatedResult
            } catch (e: Exception) {
                Log.e(TAG, "❌ TOOL ERROR: queryDatabase failed", e)
                val errorMsg = "Query error: ${e.message}"
                _toolEvents.tryEmit(ToolEvent.Finished("Querying Database", "Failed: ${e.message}"))
                errorMsg
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
            if (ts > 0) memories = memories.filter { it.creationTimestamp >= ts }
        }
        filters["dateTo"]?.let { date ->
            val ts = parseDate(date.toString())
            if (ts > 0) memories = memories.filter { it.creationTimestamp <= (ts + 86400000) } // Include end date
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
        
        // Ordering
        val orderBy = filters["orderBy"]?.toString()?.lowercase() ?: "date_desc" // Default to newest
        memories = when (orderBy) {
            "date_asc" -> memories.sortedBy { it.creationTimestamp }
            "date_desc" -> memories.sortedByDescending { it.creationTimestamp }
            "title_asc" -> memories.sortedBy { it.aiTitle }
            else -> memories.sortedByDescending { it.creationTimestamp }
        }

        // Offset & Limit
        val offset = (filters["offset"] as? Number)?.toInt() ?: 0
        val safeLimit = limit.coerceAtMost(20) // Cap default limit at 20
        
        if (offset >= memories.size) return "No memories found (Offset $offset is beyond total ${memories.size})."
        
        memories = memories.drop(offset).take(safeLimit)
        
        if (memories.isEmpty()) return "No memories found matching the criteria."
        
        val countInfo = if (offset > 0) "[Showing items ${offset + 1}-${offset + memories.size}]" else ""
        return "$countInfo\n" + formatMemories(memories, fields)
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
                // Image URIs for AI to reference in Markdown
                val hasImageField = showAll || fieldList.any { it.replace("_", "") == "imageuri" } || "media" in fieldList || "image" in fieldList
                if (hasImageField) {
                    m.imageUri?.let { appendLine("\nimageUri: $it") }
                    m.imageUris?.takeIf { it.size > 1 }?.let { 
                        appendLine("imageUris: ${it.joinToString(", ")}")
                    }
                    m.bookmarkImageUrl?.let { appendLine("bookmarkImageUrl: $it") }
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
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🔧 TOOL CALL: webSearch")
        Log.i(TAG, "📥 INPUT: query='$query'")
        
        _toolEvents.tryEmit(ToolEvent.Started("Searching Web", query))
        
        return runBlocking {
            try {
                val tool = com.google.genai.types.Tool.builder()
                    .googleSearch(com.google.genai.types.GoogleSearch.builder().build())
                    .build()
                
                val config = com.google.genai.types.GenerateContentConfig.builder()
                    .tools(tool)
                    .build()

                val client = searchClient ?: return@runBlocking "Web search is currently unavailable. This feature requires the Gemini provider."

                val model = settingsRepository?.geminiModelIdFlow?.first() ?: "gemini-3.1-flash-lite"
                val response = client.models.generateContent(model, query, config)
                val result = response.text() ?: "No results found."
                Log.i(TAG, "📤 RESULT (${result.length} chars):")
                result.chunked(1000).forEachIndexed { index, chunk ->
                    Log.i(TAG, "    [Part $index]: $chunk")
                }
                Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                _toolEvents.tryEmit(ToolEvent.Finished("Searching Web", "Completed"))
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ TOOL ERROR: webSearch failed", e)
                val errorMsg = "Error searching web: ${e.message}"
                _toolEvents.tryEmit(ToolEvent.Finished("Searching Web", "Failed"))
                errorMsg
            }
        }
    }

    /**
     * Display memory cards in the chat UI.
     * This tool signals the UI to show interactive memory cards after the AI's text response.
     * 
     * @param memoryIds - JSON array of memory IDs to display, e.g., "[1, 5, 12]"
     * @return Confirmation message for the AI
     */
    @JvmStatic
    fun displayMemoriesById(memoryIds: String): String {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🔧 TOOL CALL: displayMemoriesById")
        Log.i(TAG, "📥 INPUT: memoryIds='$memoryIds'")
        
        _toolEvents.tryEmit(ToolEvent.Started("Displaying Memories", memoryIds))
        
        return runBlocking {
            try {
                val ids = parseMemoryIds(memoryIds)
                if (ids.isEmpty()) {
                    val errorResult = "Error: No valid memory IDs provided. Use format: [1, 2, 3]"
                    Log.w(TAG, "📤 RESULT: $errorResult")
                    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    _toolEvents.tryEmit(ToolEvent.Finished("Displaying Memories", "Invalid IDs"))
                    return@runBlocking errorResult
                }
                
                // Verify memories exist
                val memories = memoryDao.getAllMemories().first()
                val validIds = ids.filter { id -> memories.any { it.id == id } }
                
                if (validIds.isEmpty()) {
                    val errorResult = "Error: None of the specified memory IDs exist in the database."
                    Log.w(TAG, "📤 RESULT: $errorResult")
                    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    _toolEvents.tryEmit(ToolEvent.Finished("Displaying Memories", "No matching memories"))
                    return@runBlocking errorResult
                }
                
                // Store for UI to pick up
                lastDisplayedMemoryIds = validIds
                
                val result = "Successfully queued ${validIds.size} memories for display: ${validIds.joinToString(", ")}. The user will see interactive memory cards below your response."
                Log.i(TAG, "📤 RESULT: $result")
                Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                _toolEvents.tryEmit(ToolEvent.Finished("Displaying Memories", "Showing ${validIds.size} items"))
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ TOOL ERROR: displayMemoriesById failed", e)
                _toolEvents.tryEmit(ToolEvent.Finished("Displaying Memories", "Failed"))
                "Error displaying memories: ${e.message}"
            }
        }
    }
    
    /**
     * Clears the displayed memory IDs. Call this before each new AI request.
     */
    fun clearDisplayedMemoryIds() {
        lastDisplayedMemoryIds = emptyList()
    }
    
    private fun parseMemoryIds(json: String): List<Int> {
        return try {
            val cleaned = json.trim().removeSurrounding("[", "]")
            if (cleaned.isBlank()) emptyList()
            else cleaned.split(",").mapNotNull { it.trim().toIntOrNull() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse memory IDs: $json", e)
            emptyList()
        }
    }

    /**
     * Get the database schema (tables and fields).
     * Helps the AI understand available data structures.
     */
    @JvmStatic
    fun getDatabaseSchema(): String {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🔧 TOOL CALL: getDatabaseSchema")
        
        _toolEvents.tryEmit(ToolEvent.Started("Getting DB Schema", "all tables"))
        
        val schema = """
            Available Tables and Fields:
            
            1. 'memories' - User's stored memories (images, audio, text, bookmarks)
               Fields: id, userText, aiTitle, aiSummary, aiTags (List), aiImageAnalysis, aiAudioTranscription, type (IMAGE/TEXT/AUDIO/BOOKMARK), status, creationTimestamp, imageUri, imageUris (List), bookmarkUrl, bookmarkTitle, bookmarkDescription, bookmarkImageUrl
            
            2. 'tasks' - Tasks and events linked to memories or standalone
               Fields: id, memoryId, title, description, dueDate (YYYY-MM-DD), dueTime, isCompleted, priority (LOW/MED/HIGH), type (TODO/EVENT)
            
            3. 'collections' - Groups of memories
               Fields: id, name, description
            
            Use 'queryDatabase' to access data in these tables. 
            IMPORTANT: When querying fields, normalization happens automatically (e.g., 'image_uri' becomes 'imageuri').
        """.trimIndent()
        
        Log.i(TAG, "📤 RESULT: Schema returned")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        _toolEvents.tryEmit(ToolEvent.Finished("Getting DB Schema", "Returned 3 tables"))
        
        return schema
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
