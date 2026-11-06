package com.example.memgallery.lifeagent.composio

/**
 * Which header a Composio key goes in. Composio has more than the two obvious kinds and the prefix is not a reliable
 * sign (a key that starts `ck_` may be any of them), so the client probes each header and keeps the one that answers.
 */
enum class KeyKind(val header: String, val label: String) {
    /** Project key: everything scoped to one project. */
    PROJECT("x-api-key", "Project key"),

    /** Personal or user key: can see several projects, may need x-org-id. */
    USER("x-user-api-key", "Personal key"),

    /** Organisation key: the whole organisation rather than one project. */
    ORG("x-org-api-key", "Org key"),

    /** Nothing has been confirmed yet. */
    UNKNOWN("", "Key");

    companion object {
        /** The order a key is tried in. Project first, then personal, then org. */
        val PROBE_ORDER = listOf(PROJECT, USER, ORG)

        /** Only a hint for the first attempt; the probe decides, never this. */
        fun of(key: String?): KeyKind {
            val k = key?.trim().orEmpty()
            return when {
                k.startsWith("uak_", ignoreCase = true) || k.startsWith("uk_", ignoreCase = true) -> USER
                k.startsWith("ak_", ignoreCase = true) -> PROJECT
                k.startsWith("ok_", ignoreCase = true) -> ORG
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
    /** The header that answered, once one did. */
    val header: String? = null,
    /** One line per header tried, e.g. "x-api-key 401: Invalid API key", for the screen to show when all fail. */
    val attempts: List<String> = emptyList(),
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
