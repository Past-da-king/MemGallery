package com.example.memgallery.lifeagent.composio

/**
 * Picks the road to Composio for the key that is stored.
 *
 * Composio has two separate doors: the backend REST API (project, personal and org keys) and the Connect MCP
 * endpoint (consumer keys, `ck_`, the "For You" key). The stored kind decides; when it is not known yet, the prefix
 * is tried first and a key the REST door refuses is tried on the Connect door before giving up. Everything here is
 * READ ONLY, and the read gate lives inside both clients, so a router can never open a write path.
 */
class RoutingComposioApi(
    private val rest: ComposioClient,
    private val connect: ComposioConnectClient,
    private val keyKindProvider: suspend () -> KeyKind? = { null },
    private val keyProvider: suspend () -> String? = { null },
) : ComposioApi {

    private suspend fun isConsumer(): Boolean {
        val stored = keyKindProvider()
        if (stored == KeyKind.CONSUMER) return true
        if (stored != null && stored != KeyKind.UNKNOWN) return false
        return KeyKind.of(keyProvider()) == KeyKind.CONSUMER
    }

    private suspend fun <T> route(block: suspend (ComposioApi) -> T): T {
        if (isConsumer()) {
            LifeAgentLog.add("door: connect (consumer key)")
            return block(connect)
        }
        return try {
            block(rest)
        } catch (e: ComposioException) {
            // A key the REST door does not take may still be a Connect key: try that door once before failing.
            if (e.kind == ComposioErrorKind.AUTH || e.kind == ComposioErrorKind.NOT_FOUND) {
                try {
                    block(connect)
                } catch (e2: Exception) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    override suspend fun listConnectedAccounts(): List<ConnectedAccount> = route { it.listConnectedAccounts() }

    override suspend fun execute(slug: String, accountId: String, userId: String?, arguments: Map<String, Any?>): ToolResult =
        route { it.execute(slug, accountId, userId, arguments) }

    override suspend fun listToolkits(cursor: String?, search: String?, category: String?, limit: Int): ToolkitPage =
        route { it.listToolkits(cursor, search, category, limit) }

    override suspend fun listTools(toolkitSlug: String, cursor: String?, limit: Int): ToolPage =
        route { it.listTools(toolkitSlug, cursor, limit) }

    override suspend fun createConnectLink(toolkitSlug: String, userId: String, callbackUrl: String?): ConnectLink =
        route { it.createConnectLink(toolkitSlug, userId, callbackUrl) }

    override suspend fun listProjects(): List<ComposioProject> = route { it.listProjects() }

    override suspend fun pull(toolkit: String, accountId: String, since: Long): List<RawItem>? =
        route { it.pull(toolkit, accountId, since) }

    override suspend fun awaitActive(toolkit: String, timeoutMs: Long): Boolean =
        route { it.awaitActive(toolkit, timeoutMs) }

    /**
     * Tries the door the key should belong to, and the other one when that fails, so a `ck_` key, an unknown prefix
     * and a personal key all end up working. Every header's own status and message is kept for the screen.
     */
    override suspend fun checkKey(): KeyCheck {
        if (isConsumer()) {
            val mcp = connect.checkKey()
            if (mcp.ok) return mcp
            val rest = rest.checkKey()
            if (rest.ok) return rest
            return mcp.copy(attempts = rest.attempts + mcp.attempts, error = joinError(rest, mcp))
        }
        val rest = rest.checkKey()
        if (rest.ok) return rest
        val mcp = connect.checkKey()
        if (mcp.ok) return mcp
        return rest.copy(attempts = rest.attempts + mcp.attempts, error = joinError(rest, mcp))
    }

    private fun joinError(rest: KeyCheck, mcp: KeyCheck): String =
        listOfNotNull(rest.error, mcp.attempts.firstOrNull(), mcp.error).joinToString(" · ").take(400)
}
