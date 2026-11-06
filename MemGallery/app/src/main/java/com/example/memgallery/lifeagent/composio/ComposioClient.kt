package com.example.memgallery.lifeagent.composio

import com.example.memgallery.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the collection pipeline needs from Composio. Faked in tests. Both calls only ever read. */
interface ComposioApi {
    suspend fun listConnectedAccounts(): List<ConnectedAccount>
    suspend fun execute(slug: String, accountId: String, userId: String?, arguments: Map<String, Any?>): ToolResult

    /** Every toolkit Composio offers, paged. Defaults keep older fakes compiling. */
    suspend fun listToolkits(cursor: String? = null, search: String? = null, category: String? = null, limit: Int = 100): ToolkitPage =
        throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "This connector cannot list toolkits")

    /** The tools of one toolkit, paged. Used to find its read tools. */
    suspend fun listTools(toolkitSlug: String, cursor: String? = null, limit: Int = 100): ToolPage =
        throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "This connector cannot list tools")

    /** A link the user opens to connect a toolkit, made against his own Composio project. */
    suspend fun createConnectLink(toolkitSlug: String, userId: String, callbackUrl: String? = null): ConnectLink =
        throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "This connector cannot make a connect link")

    /** What Composio says about the stored key, including the real error when it is refused. */
    suspend fun checkKey(): KeyCheck =
        throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "This connector cannot check a key")

    /** The projects a personal key can see (empty for a project key). */
    suspend fun listProjects(): List<ComposioProject> = emptyList()

    /**
     * A real read of one app with the arguments that app needs, for the door that knows them (Connect/consumer).
     * Null means this client has no recipe and the caller should use the catalog's own pull.
     */
    suspend fun pull(toolkit: String, accountId: String, since: Long): List<RawItem>? = null

    /** Waits for a connection link to finish, on the door that can tell (Connect). False means not yet. */
    suspend fun awaitActive(toolkit: String, timeoutMs: Long = 0): Boolean = false
}

/**
 * Composio v3 over OkHttp. The key is the user's own, read from settings on every call (never held in code).
 * Two requests exist and nothing else: GET connected_accounts, and POST tools/execute/{slug} for a slug that is in
 * ComposioCatalog.READ_TOOLS. Timeouts, and up to [maxAttempts] tries with backoff on network errors, 429 and 5xx.
 */
@Singleton
class ComposioClient(
    private val keyProvider: suspend () -> String?,
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val maxAttempts: Int,
    private val backoff: suspend (attempt: Int) -> Unit,
    private val keyKindProvider: suspend () -> KeyKind? = { null },
    private val orgIdProvider: suspend () -> String? = { null },
) : ComposioApi {

    @Inject
    constructor(settings: SettingsRepository) : this(
        keyProvider = { settings.composioApiKey() },
        baseUrl = ComposioCatalog.BASE_URL,
        http = defaultHttp(),
        maxAttempts = 3,
        backoff = { attempt -> delay(1000L shl (attempt - 1)) },
        keyKindProvider = { settings.composioKeyKind() },
        orgIdProvider = { settings.composioOrgId() },
    )

    private val gson = Gson()

    override suspend fun listConnectedAccounts(): List<ConnectedAccount> {
        val out = ArrayList<ConnectedAccount>()
        var cursor: String? = null
        repeat(5) {
            val url = (baseUrl + ComposioCatalog.PATH_ACCOUNTS).toHttpUrl().newBuilder().apply {
                addQueryParameter("limit", "100")
                if (cursor != null) addQueryParameter("cursor", cursor!!)
            }.build()
            val body = send(Request.Builder().url(url).get())
            val o = parseObject(body)
            (o.get("items") as? JsonArray)?.forEach { el ->
                val a = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val id = a.str("id") ?: return@forEach
                out += ConnectedAccount(
                    id = id,
                    toolkit = (a.get("toolkit") as? JsonObject)?.str("slug") ?: a.str("toolkit_slug") ?: "",
                    userId = a.str("user_id"),
                    status = a.str("status") ?: "",
                    label = a.str("alias") ?: a.str("word_id"),
                )
            }
            cursor = o.str("next_cursor")
            if (cursor == null) return out
        }
        return out
    }

    override suspend fun execute(slug: String, accountId: String, userId: String?, arguments: Map<String, Any?>): ToolResult {
        // The read-only guarantee: the four fixture tools, plus any tool whose own name says it only reads.
        if (!ReadToolRules.isReadTool(slug)) {
            throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: $slug is not a read tool")
        }
        val payload = JsonObject().apply {
            addProperty("connected_account_id", accountId)
            if (!userId.isNullOrBlank()) addProperty("user_id", userId)
            add("arguments", gson.toJsonTree(arguments))
        }
        val url = (baseUrl + ComposioCatalog.PATH_EXECUTE + slug).toHttpUrl()
        val body = send(Request.Builder().url(url).post(payload.toString().toRequestBody(JSON)))
        val o = parseObject(body)
        val err = o.get("error")?.takeIf { !it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else it.toString() }
        val ok = o.get("successful")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: (err == null)
        return ToolResult(ok, o.get("data"), err)
    }

    /** Which header this key belongs in. Auto-detected by prefix, and the probe in [checkKey] corrects it. */
    suspend fun keyKind(): KeyKind {
        val stored = keyKindProvider()?.takeIf { it != KeyKind.UNKNOWN }
        return stored ?: KeyKind.of(keyProvider())
    }

    private suspend fun send(builder: Request.Builder, kind: KeyKind? = null): String {
        val key = keyProvider()?.trim()
        if (key.isNullOrEmpty()) throw ComposioException(ComposioErrorKind.NO_KEY, "Add your Composio key to connect")
        val how = (kind ?: keyKind()).takeIf { it != KeyKind.UNKNOWN } ?: KeyKind.PROJECT
        var b = builder.header(how.header, key).header("Accept", "application/json")
        if (how == KeyKind.USER) orgIdProvider()?.takeIf { it.isNotBlank() }?.let { b = b.header("x-org-id", it) }
        val request = b.build()
        LifeAgentLog.add("rest ${request.method} ${request.url.encodedPath} with ${LifeAgentLog.maskKey(key)} in ${how.header}")
        var last: ComposioException? = null
        for (attempt in 1..maxAttempts) {
            try {
                val (code, text) = withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
                }
                LifeAgentLog.add("rest ${request.method} ${request.url.encodedPath} -> $code")
                when {
                    code in 200..299 -> return text
                    code == 401 || code == 403 -> throw ComposioException(ComposioErrorKind.AUTH, "Composio did not accept the key ($code)")
                    code == 404 -> throw ComposioException(ComposioErrorKind.NOT_FOUND, "Composio could not find that account or tool (404)")
                    code == 429 -> last = ComposioException(ComposioErrorKind.RATE_LIMIT, "Composio is rate limiting, try later (429)")
                    code >= 500 -> last = ComposioException(ComposioErrorKind.SERVER, "Composio had a problem ($code)")
                    else -> throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio answered $code: ${errorMessage(text)}")
                }
            } catch (e: ComposioException) {
                if (e.kind != ComposioErrorKind.RATE_LIMIT && e.kind != ComposioErrorKind.SERVER) throw e
                last = e
            } catch (e: IOException) {
                last = ComposioException(ComposioErrorKind.NETWORK, "No connection to Composio", e)
            }
            if (attempt < maxAttempts) backoff(attempt)
        }
        throw last ?: ComposioException(ComposioErrorKind.NETWORK, "No connection to Composio")
    }

    // ---- the widened Connections screen: any toolkit, any read tool, both key kinds ----

    override suspend fun listToolkits(cursor: String?, search: String?, category: String?, limit: Int): ToolkitPage {
        val url = (baseUrl + ComposioCatalog.PATH_TOOLKITS).toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", limit.toString())
            if (!cursor.isNullOrBlank()) addQueryParameter("cursor", cursor)
            if (!search.isNullOrBlank()) addQueryParameter("search", search)
            if (!category.isNullOrBlank()) addQueryParameter("category", category)
        }.build()
        val o = parseObject(send(Request.Builder().url(url).get()))
        val items = (o.get("items") as? JsonArray)?.mapNotNull { el ->
            val t = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val slug = t.str("slug") ?: return@mapNotNull null
            val meta = t.get("meta") as? JsonObject
            ToolkitSummary(
                slug = slug,
                name = t.str("name") ?: meta?.str("name") ?: slug.replaceFirstChar { it.uppercase() },
                description = meta?.str("description") ?: t.str("description"),
                logoUrl = meta?.str("logo") ?: t.str("logo"),
                categories = (meta?.get("categories") as? JsonArray)?.mapNotNull {
                    (it.takeIf { p -> p.isJsonObject }?.asJsonObject?.str("name")) ?: it.takeIf { p -> p.isJsonPrimitive }?.asString
                } ?: emptyList(),
                authSchemes = (t.get("auth_schemes") as? JsonArray)?.mapNotNull { it.takeIf { p -> p.isJsonPrimitive }?.asString } ?: emptyList(),
                noAuth = t.get("no_auth")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                toolkitType = t.str("toolkit_type"),
            )
        } ?: emptyList()
        return ToolkitPage(items, o.str("next_cursor"))
    }

    override suspend fun listTools(toolkitSlug: String, cursor: String?, limit: Int): ToolPage {
        val url = (baseUrl + ComposioCatalog.PATH_TOOLS).toHttpUrl().newBuilder().apply {
            addQueryParameter("toolkit_slug", toolkitSlug)
            addQueryParameter("limit", limit.toString())
            addQueryParameter("toolkit_versions", "latest")
            if (!cursor.isNullOrBlank()) addQueryParameter("cursor", cursor)
        }.build()
        val o = parseObject(send(Request.Builder().url(url).get()))
        val items = (o.get("items") as? JsonArray)?.mapNotNull { el ->
            val t = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val slug = t.str("slug") ?: return@mapNotNull null
            ToolSpec(slug, t.str("name") ?: slug, t.str("description"))
        } ?: emptyList()
        return ToolPage(items, o.str("next_cursor"))
    }

    /** The project's auth config for a toolkit, made once with Composio-managed auth when there is none. */
    suspend fun authConfigFor(toolkitSlug: String): String? {
        val listUrl = (baseUrl + ComposioCatalog.PATH_AUTH_CONFIGS).toHttpUrl().newBuilder()
            .addQueryParameter("toolkit_slug", toolkitSlug)
            .addQueryParameter("limit", "1")
            .build()
        val existing = parseObject(send(Request.Builder().url(listUrl).get()))
        (existing.get("items") as? JsonArray)?.firstOrNull()?.let { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject
            o?.str("id")?.let { return it }
        }
        val payload = JsonObject().apply {
            add("toolkit", JsonObject().apply { addProperty("slug", toolkitSlug) })
            add("auth_config", JsonObject().apply {
                addProperty("type", "use_composio_managed_auth")
                add("credentials", JsonObject())
                add("restrict_to_following_tools", JsonArray())
            })
        }
        val created = parseObject(
            send(Request.Builder().url((baseUrl + ComposioCatalog.PATH_AUTH_CONFIGS).toHttpUrl()).post(payload.toString().toRequestBody(JSON)))
        )
        return created.str("id") ?: (created.get("auth_config") as? JsonObject)?.str("id") ?: created.str("nanoid")
    }

    override suspend fun createConnectLink(toolkitSlug: String, userId: String, callbackUrl: String?): ConnectLink {
        val authConfigId = authConfigFor(toolkitSlug)
            ?: throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio has no auth config for $toolkitSlug")
        val payload = JsonObject().apply {
            addProperty("auth_config_id", authConfigId)
            addProperty("user_id", userId)
            if (!callbackUrl.isNullOrBlank()) addProperty("callback_url", callbackUrl)
        }
        val o = parseObject(
            send(Request.Builder().url((baseUrl + ComposioCatalog.PATH_CONNECT_LINK).toHttpUrl()).post(payload.toString().toRequestBody(JSON)))
        )
        val redirect = o.str("redirect_url") ?: o.str("redirectUrl") ?: o.str("url") ?: o.str("link")
            ?: throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio did not return a connect link")
        return ConnectLink(redirect, o.str("connected_account_id") ?: o.str("id"))
    }

    /**
     * Checks the saved key with Composio and says what happened. A personal key is checked against auth/session/info,
     * which names the project it lands in; a project key against the toolkits list. A key whose prefix we do not know
     * is tried both ways. The error is Composio's own status and message, never a silent fail.
     */
    /**
     * Finds out which header the key belongs in by trying it, never by guessing from the prefix. In order: the kind
     * that worked last time, then project (x-api-key), personal (x-user-api-key, with the org header when one is
     * stored), organisation (x-org-api-key). The first that answers is kept and saved by the repository. When none
     * answers, every header's own status and message is returned so the screen can show the real reason.
     */
    override suspend fun checkKey(): KeyCheck {
        val stored = keyKindProvider()?.takeIf { it != KeyKind.UNKNOWN }
        val order = (listOfNotNull(stored) + KeyKind.PROBE_ORDER).distinct()
        val attempts = ArrayList<String>()
        for (kind in order) {
            val (code, text) = sendRaw(
                when (kind) {
                    KeyKind.USER -> Request.Builder().url(baseUrl + ComposioCatalog.PATH_SESSION_INFO).get()
                    KeyKind.ORG -> Request.Builder()
                        .url((baseUrl + ComposioCatalog.PATH_ORG_PROJECTS).toHttpUrl().newBuilder().addQueryParameter("limit", "1").build())
                        .get()
                    else -> Request.Builder()
                        .url((baseUrl + ComposioCatalog.PATH_TOOLKITS).toHttpUrl().newBuilder().addQueryParameter("limit", "1").build())
                        .get()
                },
                kind,
            )
            if (code in 200..299) {
                val o = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val project = o?.get("project") as? JsonObject
                val org = o?.get("org") as? JsonObject
                return KeyCheck(
                    kind = kind, ok = true, header = kind.header, attempts = attempts.toList(),
                    projectName = project?.str("name"),
                    projectId = project?.str("nano_id") ?: project?.str("id"),
                    orgName = org?.str("name"), orgId = org?.str("org_id") ?: org?.str("id"),
                )
            }
            val message = errorMessage(text).ifBlank { "refused" }
            attempts += "${kind.header} $code: $message"
        }
        return KeyCheck(
            kind = stored ?: KeyKind.UNKNOWN,
            ok = false,
            header = null,
            attempts = attempts.toList(),
            httpStatus = attempts.firstOrNull()?.substringAfter(' ')?.substringBefore(':')?.toIntOrNull(),
            error = attempts.joinToString(" · "),
        )
    }

    override suspend fun listProjects(): List<ComposioProject> {
        val out = ArrayList<ComposioProject>()
        var cursor: String? = null
        repeat(5) {
            val url = (baseUrl + ComposioCatalog.PATH_PROJECTS).toHttpUrl().newBuilder().apply {
                addQueryParameter("limit", "100")
                addQueryParameter("list_all_org_projects", "true")
                if (cursor != null) addQueryParameter("cursor", cursor!!)
            }.build()
            val o = parseObject(send(Request.Builder().url(url).get()))
            (o.get("data") as? JsonArray)?.forEach { el ->
                val p = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val id = p.str("id") ?: return@forEach
                out += ComposioProject(id, p.str("name") ?: id, p.str("org_id"))
            }
            cursor = o.str("next_cursor")
            if (cursor == null) return out
        }
        return out
    }

    /** One request with its status kept, for the key check. Never logs the key. */
    private suspend fun sendRaw(builder: Request.Builder, kind: KeyKind): Pair<Int, String> {
        val key = keyProvider()?.trim()
        if (key.isNullOrEmpty()) return 0 to """{"error":{"message":"No Composio key is saved"}}"""
        var b = builder.header(kind.header, key).header("Accept", "application/json")
        if (kind == KeyKind.USER) orgIdProvider()?.takeIf { it.isNotBlank() }?.let { b = b.header("x-org-id", it) }
        val request = b.build()
        return withContext(Dispatchers.IO) {
            runCatching { http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") } }
                .getOrElse { 0 to """{"error":{"message":"${it.message ?: "No connection to Composio"}"}}""" }
        }
    }

    private fun errorMessage(text: String): String = try {
        val o = JsonParser.parseString(text).asJsonObject
        ((o.get("error") as? JsonObject)?.str("message") ?: o.str("message") ?: "").take(120)
    } catch (e: Exception) { "" }

    private fun parseObject(text: String): JsonObject = try {
        JsonParser.parseString(text).asJsonObject
    } catch (e: Exception) {
        throw ComposioException(ComposioErrorKind.BAD_RESPONSE, "Composio sent something unreadable", e)
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
