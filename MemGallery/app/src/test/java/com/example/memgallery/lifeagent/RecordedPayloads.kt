package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ToolResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Recorded Composio v3 responses (src/test/resources/lifeagent). Realistic fictional data, shaped like what
 * POST /api/v3/tools/execute/{slug} and GET /api/v3/connected_accounts answer. Files only: no live account was read.
 */
object RecordedPayloads {
    fun text(name: String): String =
        (RecordedPayloads::class.java.getResourceAsStream("/lifeagent/$name") ?: error("missing fixture $name"))
            .readBytes().toString(Charsets.UTF_8)

    fun json(name: String): JsonObject = JsonParser.parseString(text(name)).asJsonObject

    /** The execute envelope {data, error, successful} exactly as the client turns it into a ToolResult. */
    fun result(name: String): ToolResult {
        val o = json(name)
        val err = o.get("error")?.takeIf { !it.isJsonNull }?.let { if (it.isJsonPrimitive) it.asString else it.toString() }
        return ToolResult(o.get("successful").asBoolean, o.get("data"), err)
    }

    const val GMAIL = "gmail_fetch_emails.json"
    const val CALENDAR = "calendar_events_list.json"
    const val CLICKUP_WORKSPACES = "clickup_authorized_workspaces.json"
    const val CLICKUP_TASKS = "clickup_filtered_team_tasks.json"
    const val SLACK_CHANNELS = "slack_list_all_channels.json"
    const val SLACK_HISTORY = "slack_conversation_history.json"
    const val ACCOUNTS = "connected_accounts.json"

    /** A FakeComposio that answers every read tool with its recording (Slack history only for #general). */
    fun fakeWithAllApps(): FakeComposio = FakeComposio(
        accounts = listOf(
            FakeComposio.account("ca_g", "gmail"), FakeComposio.account("ca_c", "googlecalendar"),
            FakeComposio.account("ca_k", "clickup"), FakeComposio.account("ca_s", "slack"),
        ),
    ).also { f ->
        f.on(ComposioCatalog.GMAIL_FETCH) { result(GMAIL) }
        f.on(ComposioCatalog.CAL_LIST) { result(CALENDAR) }
        f.on(ComposioCatalog.CLICKUP_WORKSPACES) { result(CLICKUP_WORKSPACES) }
        f.on(ComposioCatalog.CLICKUP_TASKS) { result(CLICKUP_TASKS) }
        f.on(ComposioCatalog.SLACK_CHANNELS) { result(SLACK_CHANNELS) }
        f.on(ComposioCatalog.SLACK_HISTORY) { a ->
            if (a["channel"] == "C01GENERAL") result(SLACK_HISTORY) else FakeComposio.ok("""{"ok":true,"messages":[]}""")
        }
    }
}
