package com.example.memgallery.agent.server

import com.example.memgallery.agent.model.AGENT_API_VERSION
import com.example.memgallery.agent.model.AgentErrorCode
import com.example.memgallery.agent.model.AgentException
import com.example.memgallery.agent.model.AgentScope
import com.example.memgallery.agent.model.AskKind
import com.example.memgallery.agent.model.AskRequest
import com.example.memgallery.agent.model.CreateMemory
import com.example.memgallery.agent.model.CreateProject
import com.example.memgallery.agent.model.CreateTask
import com.example.memgallery.agent.model.DataSource
import com.example.memgallery.agent.model.MemoryContent
import com.example.memgallery.agent.model.MemoryPatch
import com.example.memgallery.agent.model.NotifyAction
import com.example.memgallery.agent.model.NotifyPriority
import com.example.memgallery.agent.model.NotifyRequest
import com.example.memgallery.agent.model.Page
import com.example.memgallery.agent.model.ProjectPatch
import com.example.memgallery.agent.model.ProjectStatus
import com.example.memgallery.agent.model.RegisterWebhook
import com.example.memgallery.agent.model.TaskPatch
import com.example.memgallery.agent.model.TaskPriority
import com.example.memgallery.agent.model.TaskType
import com.example.memgallery.agent.model.WireEnum
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import android.util.Base64

/**
 * The wire codec.
 *
 * Responses are serialised by Gson with snake_case naming, which matches
 * `connectors/openapi.yaml` field for field. Requests are parsed **by hand**.
 *
 * That asymmetry is deliberate. Gson constructs Kotlin objects by unsafe allocation,
 * bypassing constructors — so a missing `"description"` doesn't become `""`, it becomes
 * a `null` sitting inside a non-null Kotlin field, and the NPE surfaces somewhere far
 * away with no useful message. Parsing requests explicitly costs a hundred lines and
 * buys real 400s with real explanations.
 */
object AgentJson {

    /** Serialises any [WireEnum] as its wire spelling, and reads either spelling back. */
    private val wireEnumFactory = object : TypeAdapterFactory {
        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val raw = type.rawType
            if (!Enum::class.java.isAssignableFrom(raw) || !WireEnum::class.java.isAssignableFrom(raw)) {
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val constants = raw.enumConstants as Array<Enum<*>>
            val adapter = object : TypeAdapter<Any?>() {
                override fun write(out: JsonWriter, value: Any?) {
                    if (value == null) out.nullValue() else out.value((value as WireEnum).wire)
                }

                override fun read(reader: JsonReader): Any? {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        return null
                    }
                    val raw2 = reader.nextString()
                    return constants.firstOrNull {
                        (it as WireEnum).wire.equals(raw2, ignoreCase = true) ||
                            it.name.equals(raw2, ignoreCase = true)
                    }
                }
            }
            @Suppress("UNCHECKED_CAST")
            return adapter as TypeAdapter<T>
        }
    }

    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapterFactory(wireEnumFactory)
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    fun toJson(value: Any?): String = gson.toJson(value)

    // -----------------------------------------------------------------------
    // Response envelopes
    // -----------------------------------------------------------------------

    /**
     * Every list and digest response carries `source` and `as_of`. Not decoration: it is
     * the only way a caller can tell a live device read from a mirror read that may be
     * minutes stale.
     */
    fun envelope(
        data: Any?,
        source: DataSource = DataSource.DEVICE,
        asOf: Long = System.currentTimeMillis(),
        nextCursor: String? = null,
        total: Int? = null
    ): String {
        val obj = JsonObject()
        obj.add("data", gson.toJsonTree(data))
        obj.addProperty("source", source.wire)
        obj.addProperty("as_of", asOf)
        obj.addProperty("api_version", AGENT_API_VERSION)
        obj.addProperty("next_cursor", nextCursor)
        if (total != null) obj.addProperty("total", total)
        return gson.toJson(obj)
    }

    fun <T> envelopePage(page: Page<T>, source: DataSource = DataSource.DEVICE): String =
        envelope(page.items, source, nextCursor = page.nextCursor, total = page.total)

    fun error(code: AgentErrorCode, message: String, details: Map<String, String> = emptyMap()): String {
        val obj = JsonObject()
        obj.addProperty("code", code.wire)
        obj.addProperty("message", message)
        if (details.isNotEmpty()) {
            val d = JsonObject()
            details.forEach { (k, v) -> d.addProperty(k, v) }
            obj.add("details", d)
        }
        return gson.toJson(obj)
    }

    // -----------------------------------------------------------------------
    // Request parsing
    // -----------------------------------------------------------------------

    fun parseObject(body: String): JsonObject {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return JsonObject()
        return try {
            JsonParser.parseString(trimmed).asJsonObject
        } catch (e: Exception) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "Body must be a JSON object.")
        }
    }

    private fun JsonObject.el(name: String): JsonElement? =
        get(name)?.takeIf { !it.isJsonNull }

    private fun JsonObject.str(name: String): String? = el(name)?.asString

    private fun JsonObject.reqStr(name: String): String =
        str(name)?.takeIf { it.isNotBlank() }
            ?: throw AgentException(AgentErrorCode.INVALID_REQUEST, "'$name' is required.")

    private fun JsonObject.int(name: String): Int? = el(name)?.asInt

    private fun JsonObject.bool(name: String): Boolean? = el(name)?.asBoolean

    private fun JsonObject.strList(name: String): List<String> {
        val e = el(name) ?: return emptyList()
        if (!e.isJsonArray) return emptyList()
        return e.asJsonArray.mapNotNull { if (it.isJsonNull) null else it.asString }
    }

    private fun JsonObject.intList(name: String): List<Int> {
        val e = el(name) ?: return emptyList()
        if (!e.isJsonArray) return emptyList()
        return e.asJsonArray.mapNotNull { if (it.isJsonNull) null else it.asInt }
    }

    fun createTask(o: JsonObject): CreateTask = CreateTask(
        title = o.reqStr("title"),
        description = o.str("description") ?: "",
        dueDate = o.str("due_date"),
        dueTime = o.str("due_time"),
        priority = TaskPriority.fromWire(o.str("priority")) ?: TaskPriority.MEDIUM,
        type = TaskType.fromWire(o.str("type")) ?: TaskType.TODO,
        recurring = o.bool("recurring") ?: false,
        recurrenceRule = o.str("recurrence_rule"),
        projectId = o.int("project_id"),
        memoryId = o.int("memory_id"),
        requiresApproval = o.bool("requires_approval") ?: false,
        externalId = o.str("external_id")
    )

    fun taskPatch(o: JsonObject): TaskPatch = TaskPatch(
        title = o.str("title"),
        description = o.str("description"),
        dueDate = o.str("due_date"),
        clearDueDate = o.bool("clear_due_date") ?: false,
        dueTime = o.str("due_time"),
        clearDueTime = o.bool("clear_due_time") ?: false,
        priority = TaskPriority.fromWire(o.str("priority")),
        type = TaskType.fromWire(o.str("type")),
        completed = o.bool("completed"),
        recurring = o.bool("recurring"),
        recurrenceRule = o.str("recurrence_rule"),
        projectId = o.int("project_id"),
        clearProject = o.bool("clear_project") ?: false
    )

    fun createProject(o: JsonObject): CreateProject = CreateProject(
        name = o.reqStr("name"),
        description = o.str("description") ?: "",
        status = ProjectStatus.fromWire(o.str("status")) ?: ProjectStatus.ACTIVE,
        color = o.int("color"),
        targetDate = o.str("target_date")
    )

    fun projectPatch(o: JsonObject): ProjectPatch = ProjectPatch(
        name = o.str("name"),
        description = o.str("description"),
        status = ProjectStatus.fromWire(o.str("status")),
        color = o.int("color"),
        clearColor = o.bool("clear_color") ?: false,
        targetDate = o.str("target_date"),
        clearTargetDate = o.bool("clear_target_date") ?: false,
        archived = o.bool("archived"),
        sortOrder = o.int("sort_order")
    )

    fun createMemory(o: JsonObject): CreateMemory {
        val text = o.str("text")
        val url = o.str("url")
        val image = o.el("image")?.takeIf { it.isJsonObject }?.asJsonObject
        val audio = o.el("audio")?.takeIf { it.isJsonObject }?.asJsonObject

        val supplied = listOfNotNull(text, url, image, audio)
        if (supplied.isEmpty()) {
            throw AgentException(
                AgentErrorCode.INVALID_REQUEST,
                "Provide exactly one of 'text', 'url', 'image' or 'audio'."
            )
        }
        if (supplied.size > 1) {
            throw AgentException(
                AgentErrorCode.INVALID_REQUEST,
                "Provide only one of 'text', 'url', 'image' or 'audio' — got ${supplied.size}."
            )
        }

        val content: MemoryContent = when {
            text != null -> MemoryContent.Text(text)
            url != null -> MemoryContent.Url(url)
            image != null -> MemoryContent.Image(
                bytes = decodeBase64(image.str("data_base64"), "image.data_base64"),
                mimeType = image.str("mime_type") ?: "image/jpeg",
                filename = image.str("filename")
            )
            else -> MemoryContent.Audio(
                bytes = decodeBase64(audio!!.str("data_base64"), "audio.data_base64"),
                mimeType = audio.str("mime_type") ?: "audio/mp4",
                filename = audio.str("filename")
            )
        }

        return CreateMemory(
            content = content,
            process = o.bool("process") ?: true,
            projectIds = o.intList("project_ids"),
            hidden = o.bool("hidden") ?: false
        )
    }

    private fun decodeBase64(value: String?, field: String): ByteArray {
        if (value.isNullOrBlank()) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'$field' is required.")
        }
        return try {
            Base64.decode(value, Base64.DEFAULT)
        } catch (e: Exception) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'$field' is not valid base64.")
        }
    }

    fun memoryPatch(o: JsonObject): MemoryPatch = MemoryPatch(
        title = o.str("title"),
        summary = o.str("summary"),
        userText = o.str("user_text"),
        tags = if (o.has("tags")) o.strList("tags") else null,
        hidden = o.bool("hidden")
    )

    fun notifyRequest(o: JsonObject): NotifyRequest {
        val actions = (o.el("actions") as? JsonArray).orEmpty().mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            val a = el.asJsonObject
            val id = a.str("id") ?: return@mapNotNull null
            val label = a.str("label") ?: return@mapNotNull null
            NotifyAction(id, label)
        }
        return NotifyRequest(
            title = o.reqStr("title"),
            body = o.str("body") ?: "",
            priority = NotifyPriority.fromWire(o.str("priority")),
            deepLink = o.str("deep_link"),
            actions = actions.take(3),
            tag = o.str("tag")
        )
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()

    fun askRequest(o: JsonObject): AskRequest {
        val kind = AskKind.fromWire(o.str("kind"))
            ?: throw AgentException(
                AgentErrorCode.INVALID_REQUEST,
                "'kind' must be one of: confirm, choice, text."
            )
        val options = o.strList("options")
        if (kind == AskKind.CHOICE && options.size < 2) {
            throw AgentException(
                AgentErrorCode.INVALID_REQUEST,
                "kind 'choice' needs at least two 'options'."
            )
        }
        return AskRequest(
            kind = kind,
            question = o.reqStr("question"),
            context = o.str("context"),
            options = options,
            timeoutSec = o.int("timeout_sec") ?: 3600,
            priority = NotifyPriority.fromWire(o.str("priority") ?: "high"),
            deepLink = o.str("deep_link")
        )
    }

    fun registerWebhook(o: JsonObject): RegisterWebhook {
        val url = o.reqStr("url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'url' must be http(s).")
        }
        val types = o.strList("event_types")
        if (types.isEmpty()) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'event_types' must not be empty. Use [\"*\"] for everything.")
        }
        return RegisterWebhook(url = url, eventTypes = types, secret = o.str("secret"))
    }

    fun scopes(o: JsonObject): Set<AgentScope> {
        val raw = o.strList("scopes")
        if (raw.isEmpty()) return AgentScope.DEFAULT
        return raw.mapNotNull { AgentScope.fromWire(it) }.toSet().ifEmpty { AgentScope.DEFAULT }
    }
}
