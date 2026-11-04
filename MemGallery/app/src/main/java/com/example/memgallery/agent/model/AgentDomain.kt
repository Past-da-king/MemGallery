package com.example.memgallery.agent.model

/**
 * The domain as agents see it.
 *
 * These are deliberately *not* the Room entities. Entities carry storage concerns
 * (content URIs, file paths, a nullable `aiTags` that really means "empty") that no
 * outside caller should have to reason about. Mapping lives in `AgentMappers.kt`, and
 * keeping the two apart means the schema can move without breaking every connector.
 */

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

enum class TaskPriority(override val wire: String) : WireEnum {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        fun fromStorage(value: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: MEDIUM

        fun fromWire(value: String?): TaskPriority? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

enum class TaskType(override val wire: String) : WireEnum {
    TODO("todo"), EVENT("event");

    companion object {
        fun fromStorage(value: String?): TaskType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: TODO

        fun fromWire(value: String?): TaskType? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

data class AgentTask(
    val id: Int,
    val title: String,
    val description: String,
    /** YYYY-MM-DD, local to the device. */
    val dueDate: String? = null,
    /** HH:MM, 24-hour, local to the device. */
    val dueTime: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val type: TaskType = TaskType.TODO,
    val completed: Boolean = false,
    /**
     * False means the task is sitting in the operator's approval inbox and is invisible
     * in the main task list. Anything an agent proposes rather than asserts lands here.
     */
    val approved: Boolean = true,
    val recurring: Boolean = false,
    val recurrenceRule: String? = null,
    val projectId: Int? = null,
    /** The memory this task was extracted from, if any. */
    val memoryId: Int? = null,
    /** "USER", "AI", or "AGENT:<client name>". */
    val source: String = "USER",
    val externalId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

// ---------------------------------------------------------------------------
// Memories
// ---------------------------------------------------------------------------

enum class MemoryKind(override val wire: String) : WireEnum {
    TEXT("text"), IMAGE("image"), AUDIO("audio"), BOOKMARK("bookmark"), CHAT("chat"), MIXED("mixed")
}

enum class ProcessingStatus(override val wire: String) : WireEnum {
    PENDING("pending"), PROCESSING("processing"), COMPLETED("completed"), FAILED("failed");

    companion object {
        fun fromStorage(value: String?): ProcessingStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class MediaType(override val wire: String) : WireEnum {
    IMAGE("image"), AUDIO("audio")
}

/**
 * A pointer to media, never the bytes. Fetching [url] requires the `media:read` scope,
 * is served only by the device (never the hub mirror), and 404s once the memory is gone.
 */
data class MediaRef(
    val index: Int,
    val type: MediaType,
    val url: String,
    val mimeType: String? = null
)

data class MediaBlob(
    val bytes: ByteArray,
    val mimeType: String,
    val filename: String? = null
) {
    // ByteArray in a data class needs these; the generated ones compare references.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaBlob) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && filename == other.filename
    }

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + mimeType.hashCode()) * 31 + (filename?.hashCode() ?: 0)
}

data class BookmarkInfo(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val faviconUrl: String? = null
)

/** Something the AI spotted inside a memory — the raw material tasks get made from. */
data class DetectedAction(
    val type: String,
    val description: String,
    val date: String? = null,
    val time: String? = null
)

data class AgentMemory(
    val id: Int,
    val kind: MemoryKind,
    val title: String? = null,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val userText: String? = null,
    val imageAnalysis: String? = null,
    val transcription: String? = null,
    val bookmark: BookmarkInfo? = null,
    val media: List<MediaRef> = emptyList(),
    val actions: List<DetectedAction> = emptyList(),
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val hidden: Boolean = false,
    val projectIds: List<Int> = emptyList(),
    val createdAt: Long = 0L
)

// ---------------------------------------------------------------------------
// Projects (collections, wearing a project hat)
// ---------------------------------------------------------------------------

enum class ProjectStatus(override val wire: String) : WireEnum {
    ACTIVE("active"), PAUSED("paused"), DONE("done");

    companion object {
        fun fromStorage(value: String?): ProjectStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: ACTIVE

        fun fromWire(value: String?): ProjectStatus? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

data class TaskCounts(
    val total: Int = 0,
    val open: Int = 0,
    val overdue: Int = 0
)

data class AgentProject(
    val id: Int,
    val name: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    /** ARGB, or null to follow the app accent. */
    val color: Int? = null,
    val targetDate: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    /** Null when the caller asked for a cheap listing rather than a counted one. */
    val memoryCount: Int? = null,
    val taskCounts: TaskCounts? = null
)

// ---------------------------------------------------------------------------
// Chats
// ---------------------------------------------------------------------------

data class AgentChat(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val messageCount: Int? = null,
    val savedAsMemory: Boolean = false,
    val createdAt: Long = 0L
)

data class AgentChatMessage(
    val id: Int,
    val chatId: Int,
    /** "user" or "model". */
    val role: String,
    val content: String,
    val hasAudio: Boolean = false,
    val hasImage: Boolean = false,
    val createdAt: Long = 0L
)

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

enum class SearchEntity(override val wire: String) : WireEnum {
    MEMORIES("memories"), TASKS("tasks"), PROJECTS("projects"), CHATS("chats")
}

data class SearchResult(
    val query: String,
    val memories: List<AgentMemory> = emptyList(),
    val tasks: List<AgentTask> = emptyList(),
    val projects: List<AgentProject> = emptyList(),
    val chats: List<AgentChat> = emptyList(),
    val truncated: Boolean = false
)

// ---------------------------------------------------------------------------
// Digest — one call for "what is he up to right now"
// ---------------------------------------------------------------------------

data class AwaitingApproval(
    val tasks: Int = 0,
    val asks: Int = 0
)

data class ProcessingCounts(
    val pending: Int = 0,
    val processing: Int = 0,
    val failed: Int = 0
)

data class ActivitySummary(
    val memoriesToday: Int = 0,
    val tasksCompletedToday: Int = 0,
    val streakDays: Int = 0
)

/**
 * The endpoint agents should hit before deciding whether to interrupt. Everything an
 * assistant needs to be oriented, in one round trip, cheap enough to mirror to the hub.
 */
data class Digest(
    val generatedAt: Long,
    /** YYYY-MM-DD on the device. */
    val today: String,
    val timeZone: String,
    val overdue: List<AgentTask> = emptyList(),
    val dueToday: List<AgentTask> = emptyList(),
    val dueThisWeek: List<AgentTask> = emptyList(),
    val eventsToday: List<AgentTask> = emptyList(),
    val awaitingApproval: AwaitingApproval = AwaitingApproval(),
    val recentMemories: List<AgentMemory> = emptyList(),
    val activeProjects: List<AgentProject> = emptyList(),
    val processing: ProcessingCounts = ProcessingCounts(),
    val activity: ActivitySummary = ActivitySummary()
)

// ---------------------------------------------------------------------------
// Device metadata
// ---------------------------------------------------------------------------

data class AgentMeta(
    val apiVersion: String = AGENT_API_VERSION,
    val appVersion: String,
    val deviceName: String,
    val deviceId: String,
    /** Wire names of everything this build actually supports, so clients degrade instead of guessing. */
    val capabilities: List<String>,
    val scopes: List<String>,
    val serverTime: Long,
    val timeZone: String
) {
    companion object {
        const val CAP_MEDIA = "media"
        const val CAP_EVENTS = "events"
        const val CAP_WEBHOOKS = "webhooks"
        const val CAP_ASK = "ask"
        const val CAP_NOTIFY = "notify"
        const val CAP_MEMORY_CREATE = "memory.create"
        const val CAP_AI_PROCESSING = "memory.ai_processing"
        const val CAP_MIRROR = "mirror"
    }
}
