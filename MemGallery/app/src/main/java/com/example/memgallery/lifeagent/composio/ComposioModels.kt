package com.example.memgallery.lifeagent.composio

import com.google.gson.JsonElement

/** One connected account in the user's own Composio project (an id and a status, never a credential). */
data class ConnectedAccount(
    val id: String,
    val toolkit: String,
    val userId: String?,
    val status: String,
    val label: String?,
) {
    val active: Boolean get() = status.equals("ACTIVE", ignoreCase = true)
}

/** The answer of a read tool. [data] is whatever the tool returned, parsed by ComposioCatalog. */
data class ToolResult(
    val successful: Boolean,
    val data: JsonElement?,
    val error: String?,
)

enum class ComposioErrorKind { NO_KEY, AUTH, NOT_FOUND, RATE_LIMIT, NETWORK, SERVER, BAD_RESPONSE, TOOL_FAILED, BLOCKED }

/** Every failure of the client. [message] is one plain line the connections screen can show as lastError. */
class ComposioException(val kind: ComposioErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)

enum class RawType { EMAIL, EVENT, TASK, MESSAGE }

/** One thing pulled from an app, before filtering. Plain data, no secrets. */
data class RawItem(
    val app: String,
    val sourceId: String,
    val type: RawType,
    val title: String,
    val body: String,
    /** Raw sender, "Name <email>" when known. */
    val author: String?,
    val url: String?,
    val occurredAt: Long,
    val labels: List<String> = emptyList(),
    val startAt: Long? = null,
    val endAt: Long? = null,
    val dueAt: Long? = null,
    /** ClickUp status, calendar status (confirmed, cancelled). */
    val status: String? = null,
    val location: String? = null,
    val joinUrl: String? = null,
    val listName: String? = null,
    val updatedAt: Long? = null,
    val attendeeCount: Int = 0,
    val isBot: Boolean = false,
)
