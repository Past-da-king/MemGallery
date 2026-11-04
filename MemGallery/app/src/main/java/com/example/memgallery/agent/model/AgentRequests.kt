package com.example.memgallery.agent.model

/**
 * Write-side request objects, plus the operator-attention primitives.
 *
 * Patch types use `null` for "leave alone", which means a field can't be cleared by
 * omission. Where clearing matters ([TaskPatch.clearDueDate], [TaskPatch.clearProject])
 * there's an explicit flag — ugly, but unambiguous, and JSON has no other honest way to
 * distinguish "absent" from "set to null" once it's been through Gson.
 */

data class CreateTask(
    val title: String,
    val description: String = "",
    val dueDate: String? = null,
    val dueTime: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val type: TaskType = TaskType.TODO,
    val recurring: Boolean = false,
    val recurrenceRule: String? = null,
    val projectId: Int? = null,
    val memoryId: Int? = null,
    /**
     * When true the task lands in the operator's approval inbox instead of the live list,
     * and `task.approved` / `task.rejected` come back as events. This is how an agent
     * proposes work rather than asserting it.
     */
    val requiresApproval: Boolean = false,
    /** Caller-owned id; creating twice with the same one updates rather than duplicates. */
    val externalId: String? = null
)

data class TaskPatch(
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val clearDueDate: Boolean = false,
    val dueTime: String? = null,
    val clearDueTime: Boolean = false,
    val priority: TaskPriority? = null,
    val type: TaskType? = null,
    val completed: Boolean? = null,
    val recurring: Boolean? = null,
    val recurrenceRule: String? = null,
    val projectId: Int? = null,
    val clearProject: Boolean = false
) {
    val isEmpty: Boolean
        get() = title == null && description == null && dueDate == null && !clearDueDate &&
            dueTime == null && !clearDueTime && priority == null && type == null &&
            completed == null && recurring == null && recurrenceRule == null &&
            projectId == null && !clearProject
}

data class CreateProject(
    val name: String,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val color: Int? = null,
    val targetDate: String? = null
)

data class ProjectPatch(
    val name: String? = null,
    val description: String? = null,
    val status: ProjectStatus? = null,
    val color: Int? = null,
    val clearColor: Boolean = false,
    val targetDate: String? = null,
    val clearTargetDate: Boolean = false,
    val archived: Boolean? = null,
    val sortOrder: Int? = null
)

/** What an agent is handing over. Exactly one of these should be populated. */
sealed class MemoryContent {
    data class Text(val text: String) : MemoryContent()
    data class Url(val url: String) : MemoryContent()
    data class Image(val bytes: ByteArray, val mimeType: String, val filename: String? = null) : MemoryContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && filename == other.filename
        }

        override fun hashCode(): Int =
            (bytes.contentHashCode() * 31 + mimeType.hashCode()) * 31 + (filename?.hashCode() ?: 0)
    }
    data class Audio(val bytes: ByteArray, val mimeType: String, val filename: String? = null) : MemoryContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Audio) return false
            return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && filename == other.filename
        }

        override fun hashCode(): Int =
            (bytes.contentHashCode() * 31 + mimeType.hashCode()) * 31 + (filename?.hashCode() ?: 0)
    }
}

data class CreateMemory(
    val content: MemoryContent,
    /**
     * False stores the memory raw and leaves it PENDING for the normal pipeline to pick up
     * later. True runs AI processing now, which spends the operator's API quota — hence the
     * per-client daily cap and the Settings kill switch.
     */
    val process: Boolean = true,
    val projectIds: List<Int> = emptyList(),
    val hidden: Boolean = false
)

data class MemoryPatch(
    val title: String? = null,
    val summary: String? = null,
    val userText: String? = null,
    val tags: List<String>? = null,
    val hidden: Boolean? = null
)

// ---------------------------------------------------------------------------
// Operator attention
// ---------------------------------------------------------------------------

enum class NotifyPriority(override val wire: String) : WireEnum {
    LOW("low"), NORMAL("normal"), HIGH("high"), URGENT("urgent");

    companion object {
        fun fromWire(value: String?): NotifyPriority =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) } ?: NORMAL
    }
}

data class NotifyAction(
    val id: String,
    val label: String
)

data class NotifyRequest(
    val title: String,
    val body: String,
    val priority: NotifyPriority = NotifyPriority.NORMAL,
    /** e.g. `memgallery://detail/412`. Opens the app at the right place when tapped. */
    val deepLink: String? = null,
    /** Up to three; Android shows no more. Taps arrive back as `notification.action` events. */
    val actions: List<NotifyAction> = emptyList(),
    /** Reusing a tag replaces the previous notification instead of stacking another. */
    val tag: String? = null
)

data class NotificationRef(
    val id: Int,
    val tag: String? = null,
    val postedAt: Long
)

enum class AskKind(override val wire: String) : WireEnum {
    /** Yes/No, answerable from the notification shade. */
    CONFIRM("confirm"),

    /** One of [AskRequest.options]. */
    CHOICE("choice"),

    /** Free text, via RemoteInput — typed without unlocking the phone. */
    TEXT("text");

    companion object {
        fun fromWire(value: String?): AskKind? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }

        fun fromStorage(value: String?): AskKind =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CONFIRM
    }
}

enum class AskStatus(override val wire: String) : WireEnum {
    PENDING("pending"), ANSWERED("answered"), EXPIRED("expired"), DISMISSED("dismissed");

    companion object {
        fun fromStorage(value: String?): AskStatus =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class AskRequest(
    val kind: AskKind,
    val question: String,
    /** Background the operator needs to answer sensibly. Shown under the question. */
    val context: String? = null,
    /** Required for [AskKind.CHOICE]; the first three become notification buttons. */
    val options: List<String> = emptyList(),
    val timeoutSec: Int? = 3600,
    val priority: NotifyPriority = NotifyPriority.HIGH,
    val deepLink: String? = null
)

data class AgentAsk(
    val id: String,
    val kind: AskKind,
    val question: String,
    val context: String? = null,
    val options: List<String> = emptyList(),
    val answer: String? = null,
    val status: AskStatus = AskStatus.PENDING,
    val askedBy: String,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val answeredAt: Long? = null
) {
    val settled: Boolean get() = status != AskStatus.PENDING
}

// ---------------------------------------------------------------------------
// Clients and webhooks
// ---------------------------------------------------------------------------

data class CreateClient(
    val name: String,
    val scopes: Set<AgentScope> = AgentScope.DEFAULT,
    val dailyAiQuota: Int = 50
)

/** Returned exactly once. [token] is never recoverable afterwards — only its hash is stored. */
data class IssuedToken(
    val client: AgentClient,
    val token: String
)

data class RegisterWebhook(
    val url: String,
    /** Wire event names, or a single "*". */
    val eventTypes: List<String>,
    /** Generated if absent. Used to sign deliveries with HMAC-SHA256. */
    val secret: String? = null
)

data class AgentWebhook(
    val id: String,
    val url: String,
    val eventTypes: List<String>,
    val active: Boolean,
    val createdAt: Long,
    val lastStatus: String? = null,
    val lastAttemptAt: Long? = null
)
