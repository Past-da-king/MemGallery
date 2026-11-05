package com.example.memgallery.agent.model

/**
 * Query objects. Every list endpoint takes exactly one of these, which is what lets the
 * HTTP layer, the MCP connector and the hub mirror share one filtering implementation
 * instead of drifting into three subtly different notions of "overdue".
 */

const val DEFAULT_PAGE_SIZE = 50
const val MAX_PAGE_SIZE = 200

enum class TaskStatusFilter(override val wire: String) : WireEnum {
    OPEN("open"), COMPLETED("completed"), ANY("any");

    companion object {
        fun fromWire(value: String?): TaskStatusFilter =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: OPEN
    }
}

enum class TaskOrder(override val wire: String) : WireEnum {
    DUE_ASC("due_asc"), DUE_DESC("due_desc"),
    CREATED_ASC("created_asc"), CREATED_DESC("created_desc"),
    PRIORITY_DESC("priority_desc");

    companion object {
        fun fromWire(value: String?): TaskOrder =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: DUE_ASC
    }
}

data class TaskQuery(
    /** Defaults to OPEN: an agent asking for "tasks" almost never means "including the 400 done ones". */
    val status: TaskStatusFilter = TaskStatusFilter.OPEN,
    /**
     * Null means approved-only. Pass `false` to inspect the approval inbox, `null` for the
     * normal list. Unapproved tasks are never mixed into a default listing.
     */
    val approved: Boolean? = null,
    val type: TaskType? = null,
    val priority: TaskPriority? = null,
    val projectId: Int? = null,
    val memoryId: Int? = null,
    /** Inclusive, YYYY-MM-DD. */
    val dueBefore: String? = null,
    val dueAfter: String? = null,
    /** Matches title and description, accent- and case-insensitively. */
    val search: String? = null,
    val source: String? = null,
    val externalId: String? = null,
    val order: TaskOrder = TaskOrder.DUE_ASC,
    val limit: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null
) {
    val effectiveLimit: Int get() = limit.coerceIn(1, MAX_PAGE_SIZE)

    /** Identity of the filter set, so a cursor can't be replayed against a different query. */
    fun fingerprint(): Int = listOf(
        status, approved, type, priority, projectId, memoryId,
        dueBefore, dueAfter, search, source, externalId, order
    ).hashCode()
}

enum class MemoryOrder(override val wire: String) : WireEnum {
    CREATED_DESC("created_desc"), CREATED_ASC("created_asc"), TITLE_ASC("title_asc");

    companion object {
        fun fromWire(value: String?): MemoryOrder =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: CREATED_DESC
    }
}

data class MemoryQuery(
    /** Free text across title, summary, user text, tags, image analysis and transcription. */
    val search: String? = null,
    val kind: MemoryKind? = null,
    val status: ProcessingStatus? = null,
    val projectId: Int? = null,
    val tag: String? = null,
    /** Epoch millis. */
    val from: Long? = null,
    val to: Long? = null,
    /** Hidden memories stay hidden unless explicitly asked for. */
    val includeHidden: Boolean = false,
    val order: MemoryOrder = MemoryOrder.CREATED_DESC,
    val limit: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null
) {
    val effectiveLimit: Int get() = limit.coerceIn(1, MAX_PAGE_SIZE)

    fun fingerprint(): Int =
        listOf(search, kind, status, projectId, tag, from, to, includeHidden, order).hashCode()
}

data class ProjectQuery(
    val status: ProjectStatus? = null,
    val includeArchived: Boolean = false,
    /** Counting tasks and memories per project costs extra queries; off unless asked. */
    val withCounts: Boolean = false,
    val search: String? = null,
    val limit: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null
) {
    val effectiveLimit: Int get() = limit.coerceIn(1, MAX_PAGE_SIZE)

    fun fingerprint(): Int = listOf(status, includeArchived, withCounts, search).hashCode()
}

data class ChatQuery(
    val search: String? = null,
    val limit: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null
) {
    val effectiveLimit: Int get() = limit.coerceIn(1, MAX_PAGE_SIZE)

    fun fingerprint(): Int = listOf(search).hashCode()
}

data class SearchQuery(
    val text: String,
    val scope: Set<SearchEntity> = setOf(SearchEntity.MEMORIES, SearchEntity.TASKS),
    val limitPerEntity: Int = 10
)
