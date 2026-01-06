package com.example.memgallery.data.remote.ai

import android.util.Log
import com.example.memgallery.data.remote.ChatTools
import kotlinx.coroutines.runBlocking

private const val TAG = "ChatToolsExecutor"

/**
 * Implementation of ToolExecutor that wraps the existing ChatTools functions.
 * This provides a provider-agnostic way to execute tools.
 */
class ChatToolsExecutor : ToolExecutor {
    
    override fun getToolDefinitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "queryDatabase",
                description = "Query the database for memories, tasks, or collections. Returns structured data based on filters.",
                parameters = mapOf(
                    "table" to ParameterDef(
                        type = "string",
                        description = "The table to query: 'memories', 'tasks', or 'collections'",
                        required = true,
                        enum = listOf("memories", "tasks", "collections")
                    ),
                    "filters" to ParameterDef(
                        type = "string",
                        description = "JSON string with filter criteria. Keys: 'search' (text), 'dateFrom'/'dateTo' (yyyy-MM-dd), 'orderBy' ('date_asc', 'date_desc'), 'offset' (int), 'limit' (int). Example: {\"orderBy\": \"date_asc\", \"limit\": 1} for first memory.",
                        required = true
                    ),
                    "fields" to ParameterDef(
                        type = "string",
                        description = "Fields to return: 'all' or comma-separated field names",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "webSearch",
                description = "Perform a web search for current information not in the database.",
                parameters = mapOf(
                    "query" to ParameterDef(
                        type = "string",
                        description = "The search query",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "displayMemoriesById",
                description = "Display interactive memory cards in the chat UI. Use after finding relevant memories to show them visually.",
                parameters = mapOf(
                    "memoryIds" to ParameterDef(
                        type = "string",
                        description = "JSON array of memory IDs to display, e.g., \"[1, 5, 12]\"",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "getDatabaseSchema",
                description = "Get the list of available database tables and their fields. Use this at the start of a session or when unsure about database structure.",
                parameters = emptyMap()
            )
        )
    }
    
    override suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        Log.d(TAG, "Executing tool: $toolName with arguments: $arguments")
        
        return try {
            when (toolName) {
                "queryDatabase" -> {
                    val table = arguments["table"]?.toString() ?: ""
                    val filters = arguments["filters"]?.toString() ?: "{}"
                    val fields = arguments["fields"]?.toString() ?: "all"
                    ChatTools.queryDatabase(table, filters, fields)
                }
                
                "webSearch" -> {
                    val query = arguments["query"]?.toString() ?: ""
                    ChatTools.webSearch(query)
                }
                
                "displayMemoriesById" -> {
                    val memoryIds = arguments["memoryIds"]?.toString() ?: "[]"
                    ChatTools.displayMemoriesById(memoryIds)
                }
                
                "getDatabaseSchema" -> {
                    ChatTools.getDatabaseSchema()
                }
                
                else -> {
                    Log.w(TAG, "Unknown tool: $toolName")
                    "Error: Unknown tool '$toolName'"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $toolName", e)
            "Error executing tool: ${e.message}"
        }
    }
}
