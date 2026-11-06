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
        val inner = o.get("data")
        return if (inner != null && inner.isJsonObject && o.entrySet().size <= 3 && !o.has("messages") && !o.has("items") && !o.has("tasks") && !o.has("channels") && !o.has("teams")) {
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
        val t = raw.trim()
        if (t.all { it.isDigit() }) {
            val n = t.toLong()
            return if (n < 100_000_000_000L) n * 1000 else n
        }
        if (t.matches(Regex("\\d+\\.\\d+"))) return (t.toDouble() * 1000).toLong() // Slack ts
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
            val subject = str(m, "subject") ?: str(preview, "subject") ?: "(no subject)"
            val body = str(m, "messageText", "snippet") ?: str(preview, "body") ?: ""
            val labels = (m.get("labelIds") as? JsonArray)?.mapNotNull { l -> l.takeIf { it.isJsonPrimitive }?.asString } ?: emptyList()
            val thread = str(m, "threadId") ?: id
            RawItem(
                app = LifeApp.GMAIL.id, sourceId = id, type = RawType.EMAIL, title = subject, body = body.take(2000),
                author = str(m, "sender", "from"),
                url = "https://mail.google.com/mail/u/0/#all/$thread",
                occurredAt = parseTime(str(m, "messageTimestamp", "internalDate", "date")) ?: 0L,
                labels = labels,
            )
        }
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
