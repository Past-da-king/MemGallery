package com.example.memgallery.lifeagent.composio

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The tools that are never called, whatever else happens. The consumer (Connect) endpoint exposes tools that run
 * code on Composio's side; nothing in this app needs them, and one of them would be a way around the read-only rule.
 * [allowed] is a whitelist, so a new tool that Composio adds tomorrow is refused until someone decides it is safe.
 */
object ConnectRules {
    const val MCP_URL = "https://connect.composio.dev/mcp"
    const val PROTOCOL_VERSION = "2024-11-05"

    /** Always refused, before any request is made. */
    val blocked: Set<String> = setOf(
        "COMPOSIO_REMOTE_BASH_TOOL",
        "COMPOSIO_REMOTE_WORKBENCH",
        "COMPOSIO_PROXY_EXECUTE",
        "COMPOSIO_MANAGE_SKILL",
        "COMPOSIO_SEARCH_SKILLS",
        "COMPOSIO_USE_SKILL",
        "COMPOSIO_SUBMIT_FEEDBACK",
    )

    /** The only meta tools this app speaks. Everything else is refused. */
    val allowed: Set<String> = setOf(
        "COMPOSIO_MANAGE_CONNECTIONS",
        "COMPOSIO_SEARCH_TOOLS",
        "COMPOSIO_GET_TOOL_SCHEMAS",
        "COMPOSIO_MULTI_EXECUTE_TOOL",
        "COMPOSIO_WAIT_FOR_CONNECTIONS",
    )

    /** Throws [ComposioException] with BLOCKED when [name] may not be called. Never touches the network. */
    fun checkTool(name: String?) {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: no tool name")
        if (n in blocked) throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: $n is never called by this app")
        if (n !in allowed) throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: $n is not a tool this app calls")
    }

    /** Every slug inside a multi execute goes through the same read gate as the REST path. */
    fun checkReadSlugs(slugs: List<String>) {
        for (s in slugs) {
            if (!ReadToolRules.isReadTool(s)) {
                throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: $s is not a read tool")
            }
        }
    }
}

/**
 * Composio Connect (consumer keys, `ck_`) over MCP Streamable HTTP. Nothing but JSON-RPC at
 * [ConnectRules.MCP_URL], with the key in `x-consumer-api-key`. Every request is a POST and every reply may be
 * plain JSON or server-sent events, so both are read. The key is fetched per call and never logged.
 */
class ComposioConnectClient(
    private val keyProvider: suspend () -> String?,
    private val http: OkHttpClient,
    private val mcpUrl: String = ConnectRules.MCP_URL,
    private val maxAttempts: Int = 2,
) : ComposioApi {

    private val ids = AtomicInteger(1)
    private val initLock = Mutex()

    @Volatile private var initialized = false

    private val json = "application/json; charset=utf-8".toMediaType()

    // ---- transport ----

    private suspend fun key(): String {
        val k = keyProvider()?.trim()
        if (k.isNullOrEmpty()) throw ComposioException(ComposioErrorKind.NO_KEY, "Add your Composio key to connect")
        return k
    }

    /** One POST. Keeps the status so the key check can show what Composio really said. */
    internal suspend fun post(payload: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(mcpUrl)
            .header("x-consumer-api-key", key())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toRequestBody(json))
            .build()
        var last: ComposioException? = null
        for (attempt in 1..maxAttempts) {
            try {
                return withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { r -> r.code to (r.body?.string() ?: "") }
                }
            } catch (e: IOException) {
                last = ComposioException(ComposioErrorKind.NETWORK, "No connection to Composio Connect", e)
            }
            if (attempt < maxAttempts) kotlinx.coroutines.delay(600L * attempt)
        }
        throw last ?: ComposioException(ComposioErrorKind.NETWORK, "No connection to Composio Connect")
    }

    /** The JSON-RPC message out of a reply: the matching `data:` line of an event stream, or the whole body. */
    internal fun extract(body: String, id: Int?): JsonObject? {
        val candidates = ArrayList<JsonObject>()
        if (body.contains("data:")) {
            for (line in body.lineSequence()) {
                val t = line.trim()
                if (!t.startsWith("data:")) continue
                val payload = t.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()?.let { candidates += it }
            }
        }
        if (candidates.isEmpty()) {
            runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()?.let { candidates += it }
        }
        if (candidates.isEmpty()) return null
        if (id == null) return candidates.last()
        return candidates.firstOrNull { it.get("id")?.takeIf { e -> e.isJsonPrimitive }?.asInt == id } ?: candidates.last()
    }

    private suspend fun rpc(method: String, params: JsonObject? = null, id: Int? = ids.getAndIncrement()): JsonObject {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            if (id != null) addProperty("id", id)
            addProperty("method", method)
            if (params != null) add("params", params)
        }
        val (code, body) = post(payload.toString())
        if (code !in 200..299) {
            val kind = when (code) {
                401, 403 -> ComposioErrorKind.AUTH
                429 -> ComposioErrorKind.RATE_LIMIT
                in 500..599 -> ComposioErrorKind.SERVER
                else -> ComposioErrorKind.BAD_RESPONSE
            }
            throw ComposioException(kind, "Composio Connect answered $code: ${messageOf(body).take(140)}")
        }
        val msg = extract(body, id)
            ?: throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio Connect sent something unreadable")
        (msg.get("error") as? JsonObject)?.let {
            throw ComposioException(ComposioErrorKind.BAD_RESPONSE, it.str("message") ?: "Composio Connect refused that call")
        }
        return (msg.get("result") as? JsonObject) ?: JsonObject()
    }

    /** MCP handshake: initialize, then the initialized notification. Idempotent per client. */
    suspend fun initialize(): Unit = initLock.withLock {
        if (initialized) return@withLock
        rpc(
            "initialize",
            JsonObject().apply {
                addProperty("protocolVersion", ConnectRules.PROTOCOL_VERSION)
                add("capabilities", JsonObject())
                add("clientInfo", JsonObject().apply {
                    addProperty("name", "MemGallery Life Agent")
                    addProperty("version", "0.4.0")
                })
            },
        )
        runCatching { rpc("notifications/initialized", null, id = null) }
        initialized = true
    }

    /** The tools this endpoint offers, by name. Only for diagnostics; the whitelist decides what may be called. */
    suspend fun listTools(): List<String> {
        initialize()
        val result = rpc("tools/list", JsonObject())
        return (result.get("tools") as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.str("name")
        } ?: emptyList()
    }

    /**
     * Calls one whitelisted meta tool and returns the inner object it carries (Composio wraps its payload as text in
     * content[0].text). A blocked or unknown tool is refused before any request is sent.
     */
    suspend fun callTool(name: String, arguments: JsonObject = JsonObject()): JsonObject {
        ConnectRules.checkTool(name)
        initialize()
        val result = rpc(
            "tools/call",
            JsonObject().apply {
                addProperty("name", name)
                add("arguments", arguments)
            },
        )
        val text = (result.get("content") as? JsonArray)?.firstOrNull()
            ?.let { it as? JsonObject }?.str("text")
        val parsed = text?.let { runCatching { JsonParser.parseString(it) as? JsonObject }.getOrNull() }
        val isError = result.get("isError")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        if (parsed == null) {
            throw ComposioException(
                ComposioErrorKind.TOOL_FAILED,
                text?.take(160) ?: if (isError) "Composio Connect refused that call" else "Composio Connect returned nothing",
            )
        }
        return parsed
    }

    // ---- ComposioApi, over MCP ----

    /** Connected accounts, read from the connection statuses each search returns, then confirmed per toolkit. */
    override suspend fun listConnectedAccounts(): List<ConnectedAccount> {
        val connectedSlugs = connectedSlugs()
        if (connectedSlugs.isEmpty()) return emptyList()
        val out = ArrayList<ConnectedAccount>()
        for (slug in connectedSlugs) {
            val data = dataOf(
                callTool(
                    "COMPOSIO_MANAGE_CONNECTIONS",
                    JsonObject().apply {
                        add("toolkits", JsonArray().apply {
                            add(JsonObject().apply { addProperty("name", slug); addProperty("action", "list") })
                        })
                    },
                ),
            )
            val results = data?.get("results") as? JsonObject
            val entry = results?.get(slug) as? JsonObject
            val accounts = entry?.get("accounts") as? JsonArray
            if (accounts == null || accounts.isEmpty) {
                out += ConnectedAccount(slug, slug, null, entry?.str("status") ?: "ACTIVE", null)
                continue
            }
            accounts.forEach { el ->
                val a = el as? JsonObject ?: return@forEach
                val id = a.str("id") ?: a.str("account_id") ?: return@forEach
                out += ConnectedAccount(
                    id = id,
                    toolkit = a.str("toolkit") ?: slug,
                    userId = a.str("user_id"),
                    status = a.str("status") ?: "ACTIVE",
                    label = a.str("alias") ?: a.str("name"),
                )
            }
        }
        return out
    }

    /** One read tool, through multi execute. Refused unless its slug passes the read gate. */
    override suspend fun execute(slug: String, accountId: String, userId: String?, arguments: Map<String, Any?>): ToolResult {
        ConnectRules.checkReadSlugs(listOf(slug))
        val data = dataOf(
            callTool(
                "COMPOSIO_MULTI_EXECUTE_TOOL",
                JsonObject().apply {
                    add("tools", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("tool_slug", slug)
                            add("arguments", com.google.gson.Gson().toJsonTree(arguments))
                            if (accountId.isNotBlank()) addProperty("account", accountId)
                        })
                    })
                    addProperty("thought", "Read only collection for the Life Agent")
                },
            ),
        )
        val first = (data?.get("results") as? JsonArray)?.firstOrNull() as? JsonObject
            ?: return ToolResult(false, null, "Composio Connect returned no result")
        val err = first.str("error")
        if (err != null) return ToolResult(false, null, err.take(200))
        val response = (first.get("response") as? JsonObject) ?: (first.get("data") as? JsonObject)
        val ok = response?.get("successful")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
        val payload = response?.get("data") ?: first.get("response") ?: first.get("data")
        val error = response?.str("error")
        return ToolResult(ok && error == null, payload, error)
    }

    /**
     * The catalog. There is no "all toolkits" tool on the consumer endpoint, so a set of ordinary read use cases is
     * searched and every toolkit Composio names is kept, with whether it already has an active connection.
     */
    override suspend fun listToolkits(cursor: String?, search: String?, category: String?, limit: Int): ToolkitPage {
        val found = LinkedHashMap<String, ToolkitSummary>()
        val queries = if (search.isNullOrBlank()) SEED_QUERIES else listOf(search)
        for (q in queries) {
            for (t in discover(q)) found.putIfAbsent(t.slug, t)
            if (!search.isNullOrBlank() || found.size >= MAX_CATALOG) break
        }
        return ToolkitPage(found.values.toList().sortedBy { it.name.lowercase() }, null)
    }

    /** The tools of one toolkit, so its read tools can be picked. */
    override suspend fun listTools(toolkitSlug: String, cursor: String?, limit: Int): ToolPage {
        val data = search("read and list items from $toolkitSlug")
        val schemas = data?.get("tool_schemas") as? JsonObject
        val out = ArrayList<ToolSpec>()
        schemas?.entrySet()?.forEach { (slug, el) ->
            val o = el as? JsonObject ?: return@forEach
            val toolkit = o.str("toolkit")
            if (toolkit != null && !toolkit.equals(toolkitSlug, ignoreCase = true)) return@forEach
            out += ToolSpec(slug, o.str("name") ?: slug, o.str("description"))
        }
        return ToolPage(out, null)
    }

    /** The connect link Composio makes for a toolkit. Opening it starts its own auth; nothing is written to the app. */
    override suspend fun createConnectLink(toolkitSlug: String, userId: String, callbackUrl: String?): ConnectLink {
        val data = dataOf(
            callTool(
                "COMPOSIO_MANAGE_CONNECTIONS",
                JsonObject().apply {
                    add("toolkits", JsonArray().apply {
                        add(JsonObject().apply { addProperty("name", toolkitSlug); addProperty("action", "add") })
                    })
                },
            ),
        )
        val entry = (data?.get("results") as? JsonObject)?.get(toolkitSlug) as? JsonObject
        val link = entry?.str("redirect_url") ?: entry?.str("redirectUrl")
            ?: throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio Connect did not return a connect link")
        return ConnectLink(link, entry?.str("connected_account_id"))
    }

    /** Consumer keys only answer over MCP, so the handshake itself is the check. */
    override suspend fun checkKey(): KeyCheck {
        initialized = false
        return try {
            initialize()
            KeyCheck(
                kind = KeyKind.CONSUMER,
                ok = true,
                header = KeyKind.CONSUMER.header,
                attempts = listOf("${KeyKind.CONSUMER.header} 200: MCP handshake accepted"),
            )
        } catch (e: ComposioException) {
            KeyCheck(
                kind = KeyKind.CONSUMER,
                ok = false,
                header = null,
                attempts = listOf("${KeyKind.CONSUMER.header} ${e.message}"),
                httpStatus = Regex("(\\d{3})").find(e.message ?: "")?.groupValues?.get(1)?.toIntOrNull(),
                error = e.message,
            )
        } catch (e: Exception) {
            KeyCheck(KeyKind.CONSUMER, false, null, listOf("${KeyKind.CONSUMER.header} ${e.message ?: "failed"}"), error = e.message)
        }
    }

    /** Consumer keys have no project picker. */
    override suspend fun listProjects(): List<ComposioProject> = emptyList()

    // ---- helpers ----

    /** One search, and the toolkits it names. */
    private suspend fun discover(useCase: String? = null): List<ToolkitSummary> {
        val queries = if (useCase.isNullOrBlank()) SEED_QUERIES.take(3) else listOf(useCase)
        val merged = LinkedHashMap<String, ToolkitSummary>()
        for (q in queries) toolkitSummaries(search(q)).forEach { merged.putIfAbsent(it.slug, it) }
        return merged.values.toList()
    }

    /** The toolkits Composio says have an active connection, from a few ordinary searches. */
    private suspend fun connectedSlugs(): List<String> {
        val out = LinkedHashSet<String>()
        for (q in SEED_QUERIES.take(3)) {
            val data = search(q) ?: continue
            (data.get("toolkit_connection_statuses") as? JsonArray)?.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val slug = o.str("toolkit") ?: return@forEach
                if (o.get("has_active_connection")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) out += slug
            }
        }
        return out.toList()
    }

    private suspend fun search(useCase: String): JsonObject? = dataOf(
        callTool(
            "COMPOSIO_SEARCH_TOOLS",
            JsonObject().apply {
                add("queries", JsonArray().apply {
                    add(JsonObject().apply { addProperty("use_case", useCase) })
                })
            },
        ),
    )

    private fun toolkitSummaries(data: JsonObject?): List<ToolkitSummary> {
        if (data == null) return emptyList()
        val out = LinkedHashMap<String, ToolkitSummary>()
        val statuses = data.get("toolkit_connection_statuses") as? JsonArray
        statuses?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val slug = o.str("toolkit") ?: return@forEach
            val connected = o.get("has_active_connection")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            out[slug] = summary(slug, o.str("description"))
        }
        val schemas = data.get("tool_schemas") as? JsonObject
        schemas?.entrySet()?.forEach { (_, el) ->
            val o = el as? JsonObject ?: return@forEach
            val toolkit = o.str("toolkit")?.lowercase() ?: return@forEach
            for (part in toolkit.split(",")) {
                val slug = part.trim()
                if (slug.isEmpty()) continue
                val existing = out[slug]
                out[slug] = summary(slug, existing?.description).copy(
                    logoUrl = existing?.logoUrl ?: logoFor(slug),
                )
            }
        }
        return out.values.toList()
    }

    private fun summary(slug: String, description: String?): ToolkitSummary = ToolkitSummary(
        slug = slug,
        name = prettyName(slug),
        description = description,
        logoUrl = logoFor(slug),
        categories = emptyList(),
        authSchemes = emptyList(),
        noAuth = false,
        toolkitType = null,
    )

    private fun logoFor(slug: String): String = "https://logos.composio.dev/api/$slug"

    private fun prettyName(slug: String): String {
        val known = LifeAppNames.bySlug(slug)
        if (known != null) return known
        val parts = slug.split("_", "-").filter { it.isNotBlank() }
        return parts.joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
    }

    private fun dataOf(inner: JsonObject): JsonObject? {
        val ok = inner.get("successful")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
        if (!ok) {
            val msg = inner.str("error") ?: "Composio Connect refused that call"
            throw ComposioException(ComposioErrorKind.TOOL_FAILED, msg.take(200))
        }
        return inner.get("data") as? JsonObject
    }

    private fun messageOf(body: String): String = runCatching {
        val o = JsonParser.parseString(body).asJsonObject
        ((o.get("error") as? JsonObject)?.str("message") ?: o.str("message") ?: "").take(120)
    }.getOrDefault("")

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    companion object {
        /** Ordinary read use cases. Between them Composio names the apps people actually connect. */
        val SEED_QUERIES = listOf(
            "read my recent emails",
            "read my calendar events for this week",
            "read my tasks and task lists",
            "read messages in my chat channels",
            "read my notes and pages",
            "read files and documents from my drive",
            "read my contacts",
            "read issues and pull requests",
            "read rows from my spreadsheets",
            "read my CRM contacts and deals",
            "read my support tickets",
            "read my reminders",
        )
        private const val MAX_CATALOG = 120

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

/** The four apps get their real names; any other slug is prettified. */
internal object LifeAppNames {
    fun bySlug(slug: String): String? = when (slug.lowercase()) {
        "gmail" -> "Gmail"
        "googlecalendar", "google_calendar" -> "Google Calendar"
        "clickup" -> "ClickUp"
        "slack" -> "Slack"
        "googledrive" -> "Google Drive"
        "googlesheets" -> "Google Sheets"
        "googledocs" -> "Google Docs"
        "notion" -> "Notion"
        "github" -> "GitHub"
        "outlook" -> "Outlook"
        "teams", "microsoft_teams" -> "Microsoft Teams"
        "trello" -> "Trello"
        "linear" -> "Linear"
        "asana" -> "Asana"
        "jira" -> "Jira"
        "todoist" -> "Todoist"
        "discord" -> "Discord"
        else -> null
    }
}
