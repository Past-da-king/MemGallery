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
) : ComposioApi {

    @Inject
    constructor(settings: SettingsRepository) : this(
        keyProvider = { settings.composioApiKey() },
        baseUrl = ComposioCatalog.BASE_URL,
        http = defaultHttp(),
        maxAttempts = 3,
        backoff = { attempt -> delay(1000L shl (attempt - 1)) },
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
        // The read-only guarantee: only the listed read tools can ever be run.
        if (slug !in ComposioCatalog.READ_TOOLS) {
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

    private suspend fun send(builder: Request.Builder): String {
        val key = keyProvider()?.trim()
        if (key.isNullOrEmpty()) throw ComposioException(ComposioErrorKind.NO_KEY, "Add your Composio key to connect")
        val request = builder.header(ComposioCatalog.KEY_HEADER, key).header("Accept", "application/json").build()
        var last: ComposioException? = null
        for (attempt in 1..maxAttempts) {
            try {
                val (code, text) = withContext(Dispatchers.IO) {
                    http.newCall(request).execute().use { it.code to (it.body?.string() ?: "") }
                }
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
