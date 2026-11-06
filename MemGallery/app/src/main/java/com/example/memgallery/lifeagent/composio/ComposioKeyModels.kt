package com.example.memgallery.lifeagent.composio

/** Which Composio key the user pasted. The two kinds go in two different headers. */
enum class KeyKind(val header: String) {
    /** Project key, `ak_...`: the x-api-key header, everything scoped to that one project. */
    PROJECT("x-api-key"),

    /** Personal or user key, `uak_...`: the x-user-api-key header, and it can see several projects. */
    USER("x-user-api-key"),

    /** Nothing that looks like a key yet. */
    UNKNOWN("x-api-key");

    companion object {
        /** Auto-detected from the prefix; UNKNOWN when it looks like neither, and then the client probes. */
        fun of(key: String?): KeyKind {
            val k = key?.trim().orEmpty()
            return when {
                k.startsWith("uak_", ignoreCase = true) -> USER
                k.startsWith("uk_", ignoreCase = true) -> USER
                k.startsWith("ak_", ignoreCase = true) -> PROJECT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * What Composio said about a key. [ok] false always carries Composio's own status and message, so the screen can show
 * the real reason instead of a silent fail. The key itself is never part of this.
 */
data class KeyCheck(
    val kind: KeyKind,
    val ok: Boolean,
    val projectName: String? = null,
    val projectId: String? = null,
    val orgName: String? = null,
    val orgId: String? = null,
    val httpStatus: Int? = null,
    val error: String? = null,
)

/** One project a personal key can see. [id] is the nano id the picker stores. */
data class ComposioProject(
    val id: String,
    val name: String,
    val orgId: String?,
)

/** One toolkit in the catalog (cached, never a credential). */
data class ToolkitSummary(
    val slug: String,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val categories: List<String>,
    val authSchemes: List<String>,
    val noAuth: Boolean,
    val toolkitType: String?,
)

/** A page of toolkits, with a cursor when Composio has more. */
data class ToolkitPage(val items: List<ToolkitSummary>, val nextCursor: String?)

/** One tool of a toolkit. [slug] is what the executor takes. */
data class ToolSpec(val slug: String, val name: String, val description: String?)

/** A connect link Composio made: open it in the browser and the account connects. */
data class ConnectLink(val redirectUrl: String, val connectedAccountId: String?)

/** A page of tools. */
data class ToolPage(val items: List<ToolSpec>, val nextCursor: String?)
