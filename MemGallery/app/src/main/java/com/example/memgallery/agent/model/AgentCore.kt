package com.example.memgallery.agent.model

/**
 * Cross-cutting types for the Agent Bridge.
 *
 * Nothing in this package touches Android or any transport. These are the words the
 * contract is written in; HTTP, MCP and the hub are all just ways of pronouncing them.
 */

/** Enums that have a stable over-the-wire spelling, independent of the Kotlin name. */
interface WireEnum {
    val wire: String
}

/** Bumped only on a breaking change. Served in `/v1/meta` so clients can refuse to guess. */
const val AGENT_API_VERSION = "1.0"

// ---------------------------------------------------------------------------
// Identity and permission
// ---------------------------------------------------------------------------

enum class AgentScope(override val wire: String) : WireEnum {
    /** Read tasks, projects, chats and memory *metadata* — including AI summaries and transcriptions. */
    READ("read"),

    /** Create and mutate tasks, projects and memories. */
    WRITE("write"),

    /** Post notifications and ask the operator questions. */
    NOTIFY("notify"),

    /** Pull raw photo/audio bytes. Deliberately not granted by default. */
    MEDIA_READ("media:read"),

    /** Manage other clients and webhooks. Implies everything else. */
    ADMIN("admin");

    companion object {
        fun fromWire(value: String): AgentScope? =
            entries.firstOrNull { it.wire.equals(value.trim(), ignoreCase = true) }

        /** What a freshly created token gets unless the operator ticks more. */
        val DEFAULT: Set<AgentScope> = setOf(READ, WRITE, NOTIFY)

        fun parse(csv: String): Set<AgentScope> =
            csv.split(',').mapNotNull { fromWire(it) }.toSet()

        fun format(scopes: Set<AgentScope>): String = scopes.joinToString(",") { it.wire }
    }
}

/**
 * The authenticated caller. Threaded through every mutating call so writes are
 * attributable, quotas are enforceable, and the in-app audit log can name names.
 */
data class AgentClient(
    val id: String,
    val name: String,
    val scopes: Set<AgentScope>,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val revoked: Boolean = false,
    val dailyAiQuota: Int = 50
) {
    /** Stamped onto everything this client creates, e.g. `AGENT:claude-code`. */
    val sourceTag: String get() = "$SOURCE_PREFIX$name"

    fun has(scope: AgentScope): Boolean =
        !revoked && (scope in scopes || AgentScope.ADMIN in scopes)

    fun require(scope: AgentScope) {
        if (revoked) throw AgentException(AgentErrorCode.UNAUTHORIZED, "This token has been revoked.")
        if (!has(scope)) throw AgentException(
            AgentErrorCode.FORBIDDEN_SCOPE,
            "This token is missing the '${scope.wire}' scope.",
            mapOf("required_scope" to scope.wire)
        )
    }

    companion object {
        const val SOURCE_PREFIX = "AGENT:"

        /** In-app callers (the chat assistant, the UI itself) that bypass token checks. */
        fun internal(name: String = "app"): AgentClient = AgentClient(
            id = "internal",
            name = name,
            scopes = setOf(AgentScope.ADMIN),
            createdAt = 0L
        )
    }
}

// ---------------------------------------------------------------------------
// Envelopes and paging
// ---------------------------------------------------------------------------

/**
 * Whether an answer came from the phone itself or from the hub's mirror. Agents must be
 * able to tell: a mirror read can be minutes stale, and acting on it blindly is how you
 * complete a task the operator already deleted.
 */
enum class DataSource(override val wire: String) : WireEnum {
    DEVICE("device"),
    MIRROR("mirror")
}

data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val total: Int? = null
) {
    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), nextCursor, total)

    companion object {
        fun <T> of(items: List<T>): Page<T> = Page(items, null, items.size)
    }
}

/**
 * Opaque forward-only cursor. Encodes the offset and a hash of the query, so a client
 * that changes filters mid-scroll gets a clean error instead of silently skewed results.
 */
data class Cursor(val offset: Int, val queryHash: Int) {
    fun encode(): String = "$offset:$queryHash"

    companion object {
        fun decode(raw: String?): Cursor? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(':')
            if (parts.size != 2) return null
            val offset = parts[0].toIntOrNull() ?: return null
            val hash = parts[1].toIntOrNull() ?: return null
            return Cursor(offset, hash)
        }
    }
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

enum class AgentErrorCode(override val wire: String, val httpStatus: Int) : WireEnum {
    UNAUTHORIZED("unauthorized", 401),
    FORBIDDEN_SCOPE("forbidden_scope", 403),
    NOT_FOUND("not_found", 404),
    INVALID_REQUEST("invalid_request", 400),
    CONFLICT("conflict", 409),
    RATE_LIMITED("rate_limited", 429),
    QUOTA_EXCEEDED("quota_exceeded", 429),
    UNAVAILABLE("unavailable", 503),
    INTERNAL("internal", 500)
}

class AgentException(
    val code: AgentErrorCode,
    message: String,
    val details: Map<String, String> = emptyMap()
) : Exception(message)
