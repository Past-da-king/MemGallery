package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.composio.ComposioApi
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.ConnectedAccount
import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.composio.ToolPage
import com.example.memgallery.lifeagent.composio.ToolSpec
import com.example.memgallery.lifeagent.composio.ToolResult
import com.google.gson.JsonParser
import java.time.LocalDateTime
import java.time.ZoneId

/** Fictional data only (the same story as the design mocks). Never a real account. */
object Fixtures {
    val ZONE: ZoneId = ZoneId.of("Africa/Johannesburg")
    /** Friday 6 Nov 2026, 12:00. */
    val NOW: Long = LocalDateTime.of(2026, 11, 6, 12, 0).atZone(ZONE).toInstant().toEpochMilli()
    const val DAY = 24L * 60 * 60 * 1000

    fun at(m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(2026, m, d, h, min).atZone(ZONE).toInstant().toEpochMilli()

    fun mail(
        id: String, subject: String, body: String = "", from: String = "Nomsa Mahlangu <nomsa@umoya.example>",
        labels: List<String> = listOf("INBOX"), at: Long = NOW - 3_600_000L,
    ) = RawItem(
        app = "gmail", sourceId = id, type = RawType.EMAIL, title = subject, body = body, author = from,
        url = "https://mail.example/$id", occurredAt = at, labels = labels,
    )

    fun slack(id: String, text: String, user: String = "U1", bot: Boolean = false) = RawItem(
        app = "slack", sourceId = "C1:$id", type = RawType.MESSAGE, title = text.lines().first(), body = text,
        author = user, url = null, occurredAt = NOW - 1_000L, isBot = bot,
    )

    fun task(id: String, name: String, due: Long?, status: String = "open") = RawItem(
        app = "clickup", sourceId = id, type = RawType.TASK, title = name, body = "", author = null, url = "https://tasks.example/$id",
        occurredAt = NOW - DAY, dueAt = due, status = status, listName = "Studio", updatedAt = NOW - DAY,
    )

    fun event(id: String, title: String, start: Long, attendees: Int = 2, updated: Long = NOW - 1000, created: Long = NOW - 10 * DAY) = RawItem(
        app = "gcal", sourceId = id, type = RawType.EVENT, title = title, body = "", author = "sizwe@ndlovu.example", url = "https://cal.example/$id",
        occurredAt = created, startAt = start, endAt = start + 3_600_000L, status = "confirmed", updatedAt = updated, attendeeCount = attendees,
    )
}

/** A scriptable Composio: per read tool a handler. Records every call so a test can prove only reads happened. */
class FakeComposio(
    var accounts: List<ConnectedAccount> = emptyList(),
    var listFails: Boolean = false,
    /** The tools the catalog says each toolkit has, so discovery can be faked for any toolkit. */
    var toolSpecs: Map<String, List<String>> = emptyMap(),
    var toolsFail: Boolean = false,
    private val handlers: MutableMap<String, (Map<String, Any?>) -> ToolResult> = mutableMapOf(),
) : ComposioApi {
    val calls = mutableListOf<Pair<String, Map<String, Any?>>>()

    fun on(slug: String, h: (Map<String, Any?>) -> ToolResult) { handlers[slug] = h }
    fun fail(slug: String, kind: ComposioErrorKind = ComposioErrorKind.SERVER, msg: String = "Composio had a problem (503)") =
        on(slug) { throw ComposioException(kind, msg) }

    override suspend fun listConnectedAccounts(): List<ConnectedAccount> {
        if (listFails) throw ComposioException(ComposioErrorKind.NETWORK, "No connection to Composio")
        return accounts
    }

    override suspend fun listTools(toolkitSlug: String, cursor: String?, limit: Int): ToolPage {
        if (toolsFail) throw ComposioException(ComposioErrorKind.SERVER, "Composio had a problem (500)")
        return ToolPage((toolSpecs[toolkitSlug] ?: emptyList()).map { ToolSpec(it, it, null) }, null)
    }

    override suspend fun execute(slug: String, accountId: String, userId: String?, arguments: Map<String, Any?>): ToolResult {
        calls += slug to arguments
        // The same gate the real client uses: anything that is not a read is refused before a request.
        if (!ReadToolRules.isReadTool(slug)) throw ComposioException(ComposioErrorKind.BLOCKED, "Refused: $slug is not a read tool")
        return (handlers[slug] ?: { ToolResult(true, JsonParser.parseString("{}"), null) })(arguments)
    }

    companion object {
        fun ok(json: String) = ToolResult(true, JsonParser.parseString(json), null)
        fun account(id: String, toolkit: String) = ConnectedAccount(id, toolkit, "user-1", "ACTIVE", null)
    }
}
