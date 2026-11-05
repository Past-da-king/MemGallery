package com.example.memgallery.agent.model

/**
 * The event vocabulary. One list, used by the SSE stream, outbound webhooks and the
 * hub's fan-out — so "task.approved" means the same thing on every path out of the device.
 */
enum class AgentEventType(override val wire: String) : WireEnum {
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_COMPLETED("task.completed"),
    TASK_APPROVED("task.approved"),
    TASK_REJECTED("task.rejected"),
    TASK_DELETED("task.deleted"),

    MEMORY_CREATED("memory.created"),
    MEMORY_PROCESSED("memory.processed"),
    MEMORY_FAILED("memory.failed"),
    MEMORY_UPDATED("memory.updated"),
    MEMORY_DELETED("memory.deleted"),

    PROJECT_CREATED("project.created"),
    PROJECT_UPDATED("project.updated"),
    PROJECT_DELETED("project.deleted"),

    ASK_CREATED("ask.created"),
    ASK_ANSWERED("ask.answered"),
    ASK_EXPIRED("ask.expired"),
    ASK_DISMISSED("ask.dismissed"),

    /** The operator tapped a button on an agent notification. */
    NOTIFICATION_ACTION("notification.action"),

    /** Keeps SSE connections and proxies alive; never persisted to the outbox. */
    HEARTBEAT("heartbeat");

    companion object {
        const val ALL = "*"

        fun fromWire(value: String?): AgentEventType? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }

        /**
         * Expands a subscription list. Supports exact names, the `*` wildcard, and
         * prefix globs like `task.*`, because "tell me about tasks" is the common case.
         */
        fun expand(patterns: List<String>): Set<AgentEventType> {
            if (patterns.isEmpty() || patterns.any { it.trim() == ALL }) {
                return entries.toSet() - HEARTBEAT
            }
            val out = mutableSetOf<AgentEventType>()
            for (raw in patterns) {
                val pattern = raw.trim()
                if (pattern.endsWith(".*")) {
                    val prefix = pattern.removeSuffix("*")
                    out += entries.filter { it.wire.startsWith(prefix) }
                } else {
                    fromWire(pattern)?.let { out += it }
                }
            }
            out -= HEARTBEAT
            return out
        }
    }
}

/**
 * One thing that happened. [payloadJson] is the affected object serialised with the same
 * wire shape the REST endpoints use, so a webhook consumer never needs a second round trip.
 */
data class AgentEvent(
    val id: Long,
    val type: AgentEventType,
    val at: Long,
    val entityId: String? = null,
    val payloadJson: String = "{}"
)
