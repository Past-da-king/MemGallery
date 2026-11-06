package com.example.memgallery.lifeagent.composio

import com.example.memgallery.lifeagent.data.LifeApp
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Every Composio endpoint, tool slug, argument name and response field the Life Agent uses lives in this file,
 * so a change in Composio's API is a one-line fix here. READ ONLY: [READ_TOOLS] is the whole list of tools the
 * client will ever execute; anything else is refused before a request is made.
 *
 * Shapes checked against docs.composio.dev on 6 Nov 2026: GET /api/v3/connected_accounts (items[].id, toolkit.slug,
 * status, user_id, alias; next_cursor), POST /api/v3/tools/execute/{slug} ({connected_account_id, user_id,
 * arguments} -> {data, error, successful}). The shape INSIDE data is not documented by Composio, so every parser
 * below reads it tolerantly (several field names, data nested once more, or a JSON string).
 */
object ComposioCatalog {
    const val BASE_URL = "https://backend.composio.dev/api/v3"
    const val KEY_HEADER = "x-api-key"
    const val PATH_ACCOUNTS = "/connected_accounts"
    const val PATH_EXECUTE = "/tools/execute/"

    // Toolkit slugs as Composio names them.
    fun toolkit(app: LifeApp): String = when (app) {
        LifeApp.GMAIL -> "gmail"
        LifeApp.GCAL -> "googlecalendar"
        LifeApp.CLICKUP -> "clickup"
        LifeApp.SLACK -> "slack"
    }

    fun appForToolkit(slug: String?): LifeApp? = LifeApp.entries.firstOrNull { toolkit(it).equals(slug, ignoreCase = true) }

    // Read tools. Nothing that creates, updates, sends or deletes is listed, here or anywhere in the app.
    const val GMAIL_FETCH = "GMAIL_FETCH_EMAILS"
    const val CAL_LIST = "GOOGLECALENDAR_EVENTS_LIST"
    const val CLICKUP_WORKSPACES = "CLICKUP_GET_AUTHORIZED_TEAMS_WORKSPACES"
    const val CLICKUP_TASKS = "CLICKUP_GET_FILTERED_TEAM_TASKS"
    const val SLACK_CHANNELS = "SLACK_LIST_ALL_CHANNELS"
    const val SLACK_HISTORY = "SLACK_FETCH_CONVERSATION_HISTORY"

    val READ_TOOLS: Set<String> = setOf(GMAIL_FETCH, CAL_LIST, CLICKUP_WORKSPACES, CLICKUP_TASKS, SLACK_CHANNELS, SLACK_HISTORY)

    // Other endpoints the widened Connections screen needs. All GET except the auth config and the connect link,
    // which are account setup in his own Composio project, never a write to Gmail, Slack or any other service.
    const val PATH_TOOLKITS = "/toolkits"
    const val PATH_TOOLKIT_CATEGORIES = "/toolkits/categories"
    const val PATH_TOOLS = "/tools"
    const val PATH_AUTH_CONFIGS = "/auth_configs"
    const val PATH_CONNECT_LINK = "/connected_accounts/link"
    const val PATH_SESSION_INFO = "/auth/session/info"
    const val PATH_PROJECTS = "/org/project/list"

    const val MAX_PAGES = 3
    const val MAX_SLACK_CHANNELS = 20

    // ---- arguments ----

    fun gmailArgs(sinceMs: Long, pageToken: String?): Map<String, Any?> = buildMap {
        put("query", "after:${sinceMs / 1000} -in:spam -in:trash")
        put("max_results", 100)
        put("verbose", true)
        put("include_payload", true)
        if (pageToken != null) put("page_token", pageToken)
    }

    fun calendarArgs(fromMs: Long, toMs: Long, pageToken: String?): Map<String, Any?> = buildMap {
        put("calendarId", "primary")
        put("timeMin", Instant.ofEpochMilli(fromMs).toString())
        put("timeMax", Instant.ofEpochMilli(toMs).toString())
        put("singleEvents", true)
        put("orderBy", "startTime")
        put("maxResults", 100)
        if (pageToken != null) put("pageToken", pageToken)
    }

    fun clickupWorkspacesArgs(): Map<String, Any?> = emptyMap()

    /** First run (sinceMs 0): every open task. Later runs: tasks changed since, closed ones included. */
    fun clickupTasksArgs(teamId: String, sinceMs: Long, page: Int): Map<String, Any?> = buildMap {
        put("team_Id", teamId)
        put("team_id", teamId)
        put("page", page)
        put("subtasks", true)
        if (sinceMs > 0) {
            put("date_updated_gt", sinceMs)
            put("include_closed", true)
        } else {
            put("include_closed", false)
        }
    }

    fun slackChannelsArgs(cursor: String?): Map<String, Any?> = buildMap {
        put("types", "public_channel,private_channel,im,mpim")
        put("exclude_archived", true)
        put("limit", 200)
        if (cursor != null) put("cursor", cursor)
    }

    fun slackHistoryArgs(channel: String, sinceMs: Long, cursor: String?): Map<String, Any?> = buildMap {
        put("channel", channel)
        put("oldest", (sinceMs / 1000).toString())
        put("limit", 100)
        if (cursor != null) put("cursor", cursor)
    }

    // ---- pulling ----

    /** Pulls everything new from one app since [sinceMs]. Throws ComposioException; the runner records it as lastError. */
    suspend fun pull(api: ComposioApi, app: LifeApp, accountId: String, userId: String?, sinceMs: Long, nowMs: Long): List<RawItem> =
        when (app) {
            LifeApp.GMAIL -> pullGmail(api, accountId, userId, sinceMs)
            LifeApp.GCAL -> pullCalendar(api, accountId, userId, sinceMs, nowMs)
            LifeApp.CLICKUP -> pullClickUp(api, accountId, userId, sinceMs)
            LifeApp.SLACK -> pullSlack(api, accountId, userId, sinceMs)
        }

    private suspend fun pullGmail(api: ComposioApi, acc: String, user: String?, since: Long): List<RawItem> {
        val out = ArrayList<RawItem>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val data = api.execute(GMAIL_FETCH, acc, user, gmailArgs(since, token)).dataOrThrow()
            out += parseGmail(data)
            token = nextToken(data, "nextPageToken", "next_page_token")
            if (token == null) return out
        }
        return out
    }

    private suspend fun pullCalendar(api: ComposioApi, acc: String, user: String?, since: Long, now: Long): List<RawItem> {
        val out = ArrayList<RawItem>()
        var token: String? = null
        val from = startOfDay(now)
        repeat(MAX_PAGES) {
            val data = api.execute(CAL_LIST, acc, user, calendarArgs(from, from + 14 * DAY, token)).dataOrThrow()
            out += parseCalendar(data)
            token = nextToken(data, "nextPageToken", "next_page_token")
            if (token == null) return out
        }
        return out
    }

    private suspend fun pullClickUp(api: ComposioApi, acc: String, user: String?, since: Long): List<RawItem> {
        val teams = parseClickUpTeams(api.execute(CLICKUP_WORKSPACES, acc, user, clickupWorkspacesArgs()).dataOrThrow())
        val out = ArrayList<RawItem>()
        for (team in teams.take(3)) {
            for (page in 0 until MAX_PAGES) {
                val data = api.execute(CLICKUP_TASKS, acc, user, clickupTasksArgs(team, since, page)).dataOrThrow()
                val got = parseClickUp(data)
                out += got
                if (got.size < 100) break
            }
        }
        return out
    }

    private suspend fun pullSlack(api: ComposioApi, acc: String, user: String?, since: Long): List<RawItem> {
        val channels = parseSlackChannels(api.execute(SLACK_CHANNELS, acc, user, slackChannelsArgs(null)).dataOrThrow())
        val out = ArrayList<RawItem>()
        var failures = 0
        for ((id, name) in channels.take(MAX_SLACK_CHANNELS)) {
            try {
                val data = api.execute(SLACK_HISTORY, acc, user, slackHistoryArgs(id, since, null)).dataOrThrow()
                out += parseSlack(data, id, name)
            } catch (e: ComposioException) {
                // One unreadable channel (not a member, archived) never fails the app. Auth problems do.
                if (e.kind == ComposioErrorKind.AUTH || e.kind == ComposioErrorKind.NO_KEY || e.kind == ComposioErrorKind.RATE_LIMIT) throw e
                failures++
                if (failures >= 5 && out.isEmpty()) throw e
            }
        }
        return out
    }

    private fun ToolResult.dataOrThrow(): JsonElement {
        if (!successful) throw ComposioException(ComposioErrorKind.TOOL_FAILED, (error ?: "The app refused the read").take(160))
        return data ?: JsonObject()
    }

    // ---- parsing (tolerant: the shape inside data is not documented) ----

    /** data may be an object, a JSON string, or wrapped once more in {"data": ...}. */
    internal fun root(data: JsonElement?): JsonObject {
        var e: JsonElement? = data
        repeat(3) {
            if (e != null && e!!.isJsonPrimitive && e!!.asJsonPrimitive.isString) {
                e = try { JsonParser.parseString(e!!.asString) } catch (x: Exception) { null }
            }
        }
        val o = e?.takeIf { it.isJsonObject }?.asJsonObject ?: return JsonObject()
        val inner = o.get("data") ?: o.get("response_data")
        return if (inner != null && inner.isJsonObject && o.entrySet().size <= 3 && !o.has("messages") && !o.has("emails") && !o.has("items") && !o.has("events") && !o.has("tasks") && !o.has("channels") && !o.has("teams") && !o.has("workspaces")) {
            inner.asJsonObject
        } else o
    }

    private fun arr(o: JsonObject, vararg names: String): JsonArray {
        for (n in names) o.get(n)?.takeIf { it.isJsonArray }?.let { return it.asJsonArray }
        return JsonArray()
    }

    private fun str(o: JsonObject?, vararg names: String): String? {
        if (o == null) return null
        for (n in names) {
            val v = o.get(n) ?: continue
            if (v.isJsonNull) continue
            if (v.isJsonPrimitive) return v.asString.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun obj(o: JsonObject?, name: String): JsonObject? = o?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun nextToken(data: JsonElement, vararg names: String): String? {
        val r = root(data)
        return str(r, *names) ?: str(obj(r, "response_metadata"), "next_cursor")
    }

    internal fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        // Gmail Date headers can end in a zone comment: "Fri, 6 Nov 2026 09:00:00 +0200 (SAST)".
        val t = raw.trim().substringBefore(" (").trim()
        if (t.all { it.isDigit() }) {
            val n = t.toLongOrNull() ?: return null // longer than a Long: not a time
            if (n <= 0L) return null // ClickUp and Slack write 0 for "none"
            return if (n < 100_000_000_000L) n * 1000 else n
        }
        if (t.matches(Regex("\\d+\\.\\d+"))) return t.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toLong() } // Slack ts
        return try { OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (e: Exception) {
            try { Instant.parse(t).toEpochMilli() } catch (e2: Exception) {
                try { LocalDate.parse(t).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e3: Exception) {
                    try { DateTimeFormatter.RFC_1123_DATE_TIME.parse(t, Instant::from).toEpochMilli() } catch (e4: Exception) { null }
                }
            }
        }
    }

    internal fun parseGmail(data: JsonElement): List<RawItem> {
        val r = root(data)
        return arr(r, "messages", "emails", "items").mapNotNull { el ->
            val m = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = str(m, "messageId", "id") ?: return@mapNotNull null
            val preview = obj(m, "preview")
            val headers = gmailHeaders(m)
            val subject = str(m, "subject") ?: str(preview, "subject") ?: headers["subject"] ?: "(no subject)"
            val body = str(m, "messageText", "snippet") ?: str(preview, "body") ?: ""
            val labels = (m.get("labelIds") as? JsonArray)?.mapNotNull { l -> l.takeIf { it.isJsonPrimitive }?.asString }.orEmpty() +
                (if (hasAttachment(m)) listOf("ATTACHMENT") else emptyList())
            val thread = str(m, "threadId") ?: id
            RawItem(
                app = LifeApp.GMAIL.id, sourceId = id, type = RawType.EMAIL, title = subject, body = body.take(2000),
                author = str(m, "sender", "from") ?: headers["from"],
                url = "https://mail.google.com/mail/u/0/#all/$thread",
                occurredAt = parseTime(str(m, "messageTimestamp", "internalDate", "date")) ?: parseTime(headers["date"]) ?: 0L,
                labels = labels,
            )
        }
    }

    /** Raw Gmail keeps the headers in payload.headers as [{name, value}]; keys come back lower case. */
    private fun gmailHeaders(m: JsonObject): Map<String, String> {
        val list = obj(m, "payload")?.get("headers") as? JsonArray ?: return emptyMap()
        val out = HashMap<String, String>()
        for (h in list) {
            val o = h.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val n = str(o, "name")?.lowercase() ?: continue
            val v = str(o, "value") ?: continue
            out.putIfAbsent(n, v)
        }
        return out
    }

    private fun hasAttachment(m: JsonObject): Boolean {
        if ((m.get("attachmentList") as? JsonArray)?.size()?.let { it > 0 } == true) return true
        fun parts(o: JsonObject?): Boolean {
            val ps = o?.get("parts") as? JsonArray ?: return false
            return ps.any { p -> p.takeIf { it.isJsonObject }?.asJsonObject?.let { po -> str(po, "filename") != null || parts(po) } == true }
        }
        return parts(obj(m, "payload"))
    }

    internal fun parseCalendar(data: JsonElement): List<RawItem> {
        val r = root(data)
        return arr(r, "items", "events").mapNotNull { el ->
            val e = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = str(e, "id") ?: return@mapNotNull null
            val start = obj(e, "start"); val end = obj(e, "end")
            val s = parseTime(str(start, "dateTime", "date")) ?: return@mapNotNull null
            val en = parseTime(str(end, "dateTime", "date")) ?: (s + 3_600_000L)
            val organizer = obj(e, "organizer")
            val meet = str(e, "hangoutLink") ?: str(e, "conferenceLink")
            RawItem(
                app = LifeApp.GCAL.id, sourceId = id, type = RawType.EVENT, title = str(e, "summary", "title") ?: "(no title)",
                body = str(e, "description")?.take(1000) ?: "",
                author = str(organizer, "email", "displayName"),
                url = str(e, "htmlLink"),
                occurredAt = parseTime(str(e, "created")) ?: s,
                startAt = s, endAt = en, status = str(e, "status"), location = str(e, "location"), joinUrl = meet,
                updatedAt = parseTime(str(e, "updated")),
                attendeeCount = (e.get("attendees") as? JsonArray)?.size() ?: 0,
            )
        }
    }

    internal fun parseClickUpTeams(data: JsonElement): List<String> =
        arr(root(data), "teams", "workspaces", "items").mapNotNull { it.takeIf { x -> x.isJsonObject }?.asJsonObject?.let { o -> str(o, "id") } }

    internal fun parseClickUp(data: JsonElement): List<RawItem> =
        arr(root(data), "tasks", "items").mapNotNull { el ->
            val t = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = str(t, "id") ?: return@mapNotNull null
            val status = obj(t, "status")?.let { str(it, "status") } ?: str(t, "status") ?: "open"
            val assignees = (t.get("assignees") as? JsonArray)?.mapNotNull { a -> a.takeIf { x -> x.isJsonObject }?.asJsonObject?.let { o -> str(o, "username", "email") } }
            RawItem(
                app = LifeApp.CLICKUP.id, sourceId = id, type = RawType.TASK, title = str(t, "name", "title") ?: "(untitled task)",
                body = (str(t, "description", "text_content") ?: "").take(1000),
                author = assignees?.firstOrNull(),
                url = str(t, "url"),
                occurredAt = parseTime(str(t, "date_created")) ?: parseTime(str(t, "date_updated")) ?: 0L,
                dueAt = parseTime(str(t, "due_date")), status = status,
                listName = obj(t, "list")?.let { str(it, "name") },
                updatedAt = parseTime(str(t, "date_updated")),
            )
        }

    /** Pairs of (channel id, label). */
    internal fun parseSlackChannels(data: JsonElement): List<Pair<String, String>> =
        arr(root(data), "channels", "items").mapNotNull { el ->
            val c = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            if (c.get("is_archived")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) return@mapNotNull null
            val id = str(c, "id") ?: return@mapNotNull null
            id to (str(c, "name", "name_normalized", "user") ?: id)
        }

    internal fun parseSlack(data: JsonElement, channelId: String, channelName: String): List<RawItem> =
        arr(root(data), "messages", "items").mapNotNull { el ->
            val m = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val ts = str(m, "ts") ?: return@mapNotNull null
            val text = str(m, "text") ?: return@mapNotNull null
            val subtype = str(m, "subtype")
            RawItem(
                app = LifeApp.SLACK.id, sourceId = "$channelId:$ts", type = RawType.MESSAGE,
                title = text.lineSequence().first().take(120), body = text.take(2000),
                author = str(m, "user", "username", "bot_id"),
                url = null,
                occurredAt = parseTime(ts) ?: 0L,
                labels = listOfNotNull("#$channelName", subtype),
                isBot = m.has("bot_id") || subtype == "bot_message" || subtype == "channel_join" || subtype == "channel_leave",
            )
        }

    private const val DAY = 24L * 60 * 60 * 1000
    private fun startOfDay(at: Long): Long =
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/**
 * Read only, for ANY toolkit. The four fixture-backed tools above are always read. For everything else the slug is
 * judged by what it says it does: a write word anywhere in the name refuses it, and it must start (or carry) a read
 * verb. This is the gate every execution passes through, so a wrong guess can never send, delete or change anything.
 */
object ReadToolRules {
    private val WRITE_WORDS = listOf(
        "SEND", "CREATE", "UPDATE", "DELETE", "REMOVE", "INSERT", "POST", "PUT", "PATCH", "UPLOAD", "WRITE",
        "MOVE", "COPY", "ARCHIVE", "MARK", "REPLY", "FORWARD", "INVITE", "CANCEL", "APPROVE", "REJECT",
        "ASSIGN", "COMMENT", "EDIT", "RENAME", "SHARE", "PUBLISH", "SCHEDULE", "TRIGGER", "ENABLE", "DISABLE",
        "CLEAR", "REVOKE", "GRANT", "SUBSCRIBE", "EXECUTE", "ADD", "SET", "CLOSE", "MERGE", "CONVERT", "DUPLICATE",
        "START", "STOP", "RUN", "LAUNCH", "PIN", "STAR", "UNSTAR", "BLOCK", "UNBLOCK", "SUSPEND", "RESTORE",
    )
    private val READ_WORDS = listOf(
        "GET", "LIST", "FETCH", "SEARCH", "READ", "RETRIEVE", "DESCRIBE", "FIND", "QUERY", "LOOKUP", "CHECK",
        "COUNT", "DOWNLOAD", "EXPORT", "SUMMARY", "INFO", "STATUS", "HISTORY", "VIEW",
    )

    fun isReadTool(slug: String?): Boolean {
        val s = slug?.uppercase()?.trim().orEmpty()
        if (s.isEmpty()) return false
        if (s in ComposioCatalog.READ_TOOLS) return true
        val words = s.split("_", "-", ".")
        if (words.any { w -> WRITE_WORDS.any { w == it || w.startsWith(it) } }) return false
        return words.any { w -> READ_WORDS.any { w == it || w.startsWith(it) } }
    }
}

/**
 * The generic path: any read tool of any toolkit, turned into candidate items. Composio does not document the shape
 * inside `data`, so this walks whatever came back, finds the objects, and reads the usual field names tolerantly.
 * Quality is lower than the four hand-written parsers; it exists so a toolkit nobody wrote a parser for still works.
 */
object GenericParser {
    private const val MAX_ITEMS = 50

    fun items(toolkit: String, toolSlug: String, data: JsonElement?, now: Long): List<RawItem> {
        if (data == null || data.isJsonNull) return emptyList()
        val objects = ArrayList<JsonObject>()
        collect(data, objects, 0)
        return objects.take(MAX_ITEMS).mapNotNull { o -> toItem(toolkit, toolSlug, o, now) }
    }

    private fun collect(el: JsonElement, out: MutableList<JsonObject>, depth: Int) {
        if (depth > 4 || out.size >= MAX_ITEMS) return
        when {
            el.isJsonArray -> el.asJsonArray.forEach { collect(it, out, depth + 1) }
            el.isJsonObject -> {
                val o = el.asJsonObject
                if (looksLikeItem(o)) out += o
                else o.entrySet().forEach { (_, v) -> if (v.isJsonArray || v.isJsonObject) collect(v, out, depth + 1) }
            }
        }
    }

    private fun looksLikeItem(o: JsonObject): Boolean =
        o.entrySet().any { (k, v) -> v.isJsonPrimitive && k.lowercase() in TITLE_KEYS }

    private fun toItem(toolkit: String, toolSlug: String, o: JsonObject, now: Long): RawItem? {
        val title = firstText(o, TITLE_KEYS)?.trim()?.take(180)
        val body = firstText(o, BODY_KEYS)?.trim()?.take(600).orEmpty()
        if (title.isNullOrBlank() && body.isBlank()) return null
        val id = firstText(o, ID_KEYS) ?: (title + body).hashCode().toString()
        val at = firstTime(o, TIME_KEYS) ?: now
        val start = firstTime(o, listOf("start", "start_time", "startDate", "start_at"))
        val end = firstTime(o, listOf("end", "end_time", "endDate", "end_at"))
        val type = when {
            start != null || toolSlug.contains("CALENDAR", true) || toolSlug.contains("EVENT", true) -> RawType.EVENT
            toolSlug.contains("TASK", true) || toolSlug.contains("ISSUE", true) || toolSlug.contains("TODO", true) -> RawType.TASK
            toolSlug.contains("MESSAGE", true) || toolSlug.contains("EMAIL", true) || toolSlug.contains("MAIL", true) -> RawType.MESSAGE
            else -> RawType.MESSAGE
        }
        return RawItem(
            app = toolkit,
            sourceId = id,
            type = type,
            title = (title ?: body.take(80)).trim(),
            body = body,
            author = firstText(o, AUTHOR_KEYS) ?: personName(o.get("from")) ?: personName(o.get("sender")),
            url = firstText(o, URL_KEYS),
            occurredAt = at,
            labels = listOfNotNull(firstText(o, listOf("label", "labels", "tags"))),
            startAt = start,
            endAt = end,
            dueAt = firstTime(o, listOf("due", "due_at", "dueAt", "due_date", "dueDate", "deadline")),
            status = firstText(o, listOf("status", "state")),
            location = firstText(o, listOf("location", "place", "venue")),
            joinUrl = firstText(o, listOf("hangoutLink", "join_url", "joinUrl", "meeting_url", "conference_url")),
            updatedAt = firstTime(o, listOf("updated_at", "updatedAt", "modified", "last_modified")),
        )
    }

    private val TITLE_KEYS = listOf(
        "subject", "title", "name", "text", "message", "snippet", "summary", "headline", "display_name", "topic",
    )
    private val BODY_KEYS = listOf("snippet", "description", "body", "text", "content", "message", "summary", "notes", "preview")
    private val ID_KEYS = listOf("id", "uid", "key", "slug", "guid", "thread_id", "threadId", "message_id", "messageId")
    private val AUTHOR_KEYS = listOf("author", "from", "sender", "owner", "creator", "user", "username", "user_name", "email")
    private val URL_KEYS = listOf("url", "link", "htmlLink", "permalink", "web_url", "webUrl", "href", "html_url")
    private val TIME_KEYS = listOf(
        "timestamp", "date", "created_at", "createdAt", "internalDate", "received_at", "sent_at", "time", "updated_at",
    )

    private fun firstText(o: JsonObject, keys: List<String>): String? {
        val byLower = o.entrySet().associate { it.key.lowercase() to it.value }
        for (k in keys) {
            val v = byLower[k.lowercase()] ?: continue
            if (v.isJsonPrimitive) {
                val p = v.asJsonPrimitive
                if (p.isString && p.asString.isNotBlank()) return p.asString
                if (p.isNumber) return p.asString
            }
            if (v.isJsonObject) personName(v)?.let { return it }
        }
        return null
    }

    private fun personName(el: JsonElement?): String? {
        val o = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return el?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString
        val email = o.get("email")?.takeIf { it.isJsonPrimitive }?.asString ?: o.get("address")?.takeIf { it.isJsonPrimitive }?.asString
        return when {
            !name.isNullOrBlank() && !email.isNullOrBlank() -> "$name <$email>"
            !name.isNullOrBlank() -> name
            !email.isNullOrBlank() -> email
            else -> null
        }
    }

    private fun firstTime(o: JsonObject, keys: List<String>): Long? {
        val byLower = o.entrySet().associate { it.key.lowercase() to it.value }
        for (k in keys) {
            val v = byLower[k.lowercase()] ?: continue
            val t = when {
                v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> {
                    val n = v.asLong
                    when {
                        n <= 0L -> null
                        n < 100_000_000_000L -> n * 1000 // seconds
                        else -> n
                    }
                }
                v.isJsonPrimitive && v.asJsonPrimitive.isString -> parseStamp(v.asString)
                else -> null
            }
            if (t != null && t > 0L) return t
        }
        return null
    }

    private fun parseStamp(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        if (t.all { it.isDigit() }) return t.toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000 else it }
        return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(t).toEpochMilli() }
            .recoverCatching { LocalDate.parse(t).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrNull()
    }
}
