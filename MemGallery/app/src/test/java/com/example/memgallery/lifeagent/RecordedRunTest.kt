package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.CollectionProgressBus
import com.example.memgallery.lifeagent.collect.CollectionRunner
import com.example.memgallery.lifeagent.collect.LifeAgentSettings
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioClient
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.data.LifeConnectionEntity
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The whole run, with the REAL ComposioClient talking to a MockWebServer that serves the recorded bodies and error
 * bodies. Nothing here touches a real account or a real key.
 */
class RecordedRunTest : DbTestBase() {
    private class FakeSettings : LifeAgentSettings {
        var lastRun = 0L
        override suspend fun composioKey() = "test-key-not-real"
        override suspend fun setLastRun(at: Long) { lastRun = at }
    }

    /** slug -> how this tool answers (status, body). Anything not listed answers its recording with 200. */
    private val overrides = HashMap<String, Pair<Int, String>>()
    private val hits = HashMap<String, Int>()
    private lateinit var server: MockWebServer
    private val settings = FakeSettings()
    private lateinit var runner: CollectionRunner

    private val recording = mapOf(
        ComposioCatalog.GMAIL_FETCH to RecordedPayloads.GMAIL,
        ComposioCatalog.CAL_LIST to RecordedPayloads.CALENDAR,
        ComposioCatalog.CLICKUP_WORKSPACES to RecordedPayloads.CLICKUP_WORKSPACES,
        ComposioCatalog.CLICKUP_TASKS to RecordedPayloads.CLICKUP_TASKS,
        ComposioCatalog.SLACK_CHANNELS to RecordedPayloads.SLACK_CHANNELS,
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            @Synchronized override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.startsWith("/api/v3/connected_accounts")) return MockResponse().setBody(RecordedPayloads.text(RecordedPayloads.ACCOUNTS))
                val slug = path.substringAfter("/api/v3/tools/execute/").substringBefore('?')
                hits.merge(slug, 1, Int::plus)
                overrides[slug]?.let { (code, body) -> return MockResponse().setResponseCode(code).setBody(body) }
                if (slug == ComposioCatalog.SLACK_HISTORY) {
                    val ch = JsonParser.parseString(request.body.readUtf8()).asJsonObject.getAsJsonObject("arguments").get("channel").asString
                    return MockResponse().setBody(if (ch == "C01GENERAL") RecordedPayloads.text(RecordedPayloads.SLACK_HISTORY) else """{"data":{"ok":true,"messages":[]},"successful":true,"error":null}""")
                }
                val file = recording[slug] ?: return MockResponse().setResponseCode(404).setBody("{}")
                return MockResponse().setBody(RecordedPayloads.text(file))
            }
        }
        server.start()
        val client = ComposioClient(
            keyProvider = { "test-key-not-real" }, baseUrl = server.url("/api/v3").toString().trimEnd('/'),
            http = OkHttpClient(), maxAttempts = 2, backoff = { },
        )
        runner = CollectionRunner(dao, client, settings, null, CollectionProgressBus()).apply { clock = { Fixtures.NOW }; zone = Fixtures.ZONE }
        runBlocking {
            listOf("gmail" to "Gmail", "gcal" to "Google Calendar", "clickup" to "ClickUp", "slack" to "Slack").forEach { (id, label) ->
                dao.upsertConnection(LifeConnectionEntity(id, label, true, null, 0L, null, null, null))
            }
        }
    }

    @After fun tearDown() { server.shutdown() }

    // The recorded accounts list has an INITIATED ClickUp and an EXPIRED Slack account; the runner must still read
    // them when the connection stored an id, and must say so clearly when it did not. Here: no stored ids.
    @Test fun theAccountMatcherUsesOnlyActiveAccountsAndSurvivesRowsWithMissingFields() {
        val accounts = runBlocking {
            ComposioClient({ "k" }, server.url("/api/v3").toString().trimEnd('/'), OkHttpClient(), 1, { }).listConnectedAccounts()
        }
        // the row without an id is skipped; the one with null toolkit and status comes back inert
        assertEquals(listOf("ca_gm1AbCdEf", "ca_gc2GhIjKl", "ca_cu3MnOpQr", "ca_sl4StUvWx", "ca_nulltk"), accounts.map { it.id })
        assertEquals(listOf(true, true, false, false, false), accounts.map { it.active })
        assertEquals("", accounts.last().toolkit)
        assertEquals("user_demo_1", accounts[0].userId)
        assertNull(accounts[3].userId)
    }

    @Test fun fullRunOverTheRecordingsGivesTheRightNumbersAndProposals() = runBlocking {
        val r = runner.run()
        val byApp = r.apps.associateBy { it.app }
        // ClickUp and Slack have no ACTIVE account in the recording: they fail alone with a clear note.
        assertEquals("failed", byApp.getValue("clickup").state)
        assertEquals("No ClickUp account is connected in Composio", byApp.getValue("clickup").note)
        assertEquals("failed", byApp.getValue("slack").state)
        assertEquals("done", r.status)

        val gmail = byApp.getValue("gmail")
        assertEquals(5, gmail.found); assertEquals(3, gmail.dropped); assertEquals(2, gmail.proposed)
        val gcal = byApp.getValue("gcal")
        assertEquals("done", gcal.state)

        val proposals = dao.getProposed()
        val gm = proposals.filter { it.app == "gmail" }.associateBy { it.kind }
        assertEquals(setOf("commitment", "money"), gm.keys)
        assertEquals("19a4c0de01aa0001", gm.getValue("commitment").sourceId)
        assertEquals(840_000L, gm.getValue("money").amountCents)
        assertEquals(listOf("ndlovupitch2026"), proposals.filter { it.app == "gcal" }.map { it.sourceId })
        // calendar meetings written locally: the cancelled one is not a meeting
        assertEquals(setOf("ndlovupitch2026", "momsbirthday2026", "weeklystandup_20261109T060000Z"), dao.observeMeetingsFrom(0L).first().map { it.id }.toSet())
        assertTrue(hits.keys.all { it in ComposioCatalog.READ_TOOLS })
    }

    @Test fun withStoredAccountIdsEveryAppReadsItsRecordingEndToEnd() = runBlocking {
        for (id in listOf("clickup" to "ca_k", "slack" to "ca_s", "gmail" to "ca_g", "gcal" to "ca_c")) {
            dao.upsertConnection(dao.getConnection(id.first)!!.copy(accountId = id.second, userId = "user_demo_1"))
        }
        val r = runner.run()
        assertEquals("done", r.status)
        assertTrue(r.apps.all { it.state == "done" })
        val clickup = r.apps.first { it.app == "clickup" }
        assertEquals(5, clickup.found)
        assertEquals(5, dao.getTasks().size)
        assertEquals(setOf("86abc001", "86abc002", "86abc004"), dao.getProposed().filter { it.app == "clickup" }.map { it.sourceId }.toSet())
        assertEquals("complete", dao.getTask("86abc005")!!.status)
        assertNull(dao.getTask("86abc003")!!.dueAt)
        val slack = r.apps.first { it.app == "slack" }
        assertEquals(6, slack.found)
        val sp = dao.getProposed().filter { it.app == "slack" }
        assertTrue(sp.any { it.kind == "commitment" && it.sourceId == "C01GENERAL:1793948400.000100" })
        assertTrue(sp.none { it.sourceId.endsWith("1793951000.000500") || it.sourceId.endsWith("1793951500.000600") })   // bot, join
        // a second run proposes nothing twice
        val again = runner.run()
        assertEquals(0, again.proposalsCreated)
    }

    // ---- failures: one app marked failed, never the run ----

    private fun failing(code: Int, bodyFile: String) = code to RecordedPayloads.text(bodyFile)

    @Test fun each401429500AndGarbageMarksOnlyThatAppFailed() = runBlocking {
        for (id in listOf("slack" to "ca_s", "clickup" to "ca_k", "gmail" to "ca_g", "gcal" to "ca_c")) {
            dao.upsertConnection(dao.getConnection(id.first)!!.copy(accountId = id.second, userId = "u"))
        }
        overrides[ComposioCatalog.CAL_LIST] = failing(401, "error_401.json")
        overrides[ComposioCatalog.CLICKUP_WORKSPACES] = failing(500, "error_500.json")
        overrides[ComposioCatalog.SLACK_CHANNELS] = 200 to RecordedPayloads.text("not_json.html")
        val r = runner.run()
        assertEquals("done", r.status)                          // gmail worked, so the run did not fail
        val m = r.apps.associateBy { it.app }
        assertEquals("done", m.getValue("gmail").state)
        assertEquals("failed", m.getValue("gcal").state)
        assertEquals("Composio did not accept the key (401)", m.getValue("gcal").note)
        assertEquals("Composio had a problem (500)", m.getValue("clickup").note)
        assertEquals("Composio sent something unreadable", m.getValue("slack").note)
        assertEquals(2, hits[ComposioCatalog.CLICKUP_WORKSPACES])   // 500 retried once (maxAttempts 2)
        assertEquals(1, hits[ComposioCatalog.CAL_LIST])             // 401 never retried
        assertEquals("Composio did not accept the key (401)", dao.getConnection("gcal")!!.lastError)
        assertNull(dao.getConnection("gmail")!!.lastError)
        assertEquals(0L, dao.getConnection("gcal")!!.lastPulledAt)
        assertEquals(Fixtures.NOW, dao.getConnection("gmail")!!.lastPulledAt)
        assertEquals(2, dao.getProposed().count { it.app == "gmail" })   // the good app's proposals are kept
        assertEquals(Fixtures.NOW, settings.lastRun)
    }

    @Test fun aRateLimitIsRetriedThenReportedWithTheStatusAndTheRunSurvives() = runBlocking {
        dao.upsertConnection(dao.getConnection("gmail")!!.copy(accountId = "ca_g"))
        overrides[ComposioCatalog.GMAIL_FETCH] = failing(429, "error_429.json")
        val r = runner.run()
        val gmail = r.apps.first { it.app == "gmail" }
        assertEquals("failed", gmail.state)
        assertEquals("Composio is rate limiting, try later (429)", gmail.note)
        assertEquals(2, hits[ComposioCatalog.GMAIL_FETCH])
    }

    @Test fun everyAppFailingFailsTheRunButNeverThrows() = runBlocking {
        for (id in listOf("gmail" to "ca_g", "gcal" to "ca_c", "clickup" to "ca_k", "slack" to "ca_s")) {
            dao.upsertConnection(dao.getConnection(id.first)!!.copy(accountId = id.second))
        }
        for (slug in listOf(ComposioCatalog.GMAIL_FETCH, ComposioCatalog.CAL_LIST, ComposioCatalog.CLICKUP_WORKSPACES, ComposioCatalog.SLACK_CHANNELS)) {
            overrides[slug] = failing(401, "error_401.json")
        }
        val r = runner.run()
        assertEquals("failed", r.status)
        assertTrue(r.apps.all { it.state == "failed" })
        assertEquals(0L, settings.lastRun)
        assertTrue(dao.getConnections().all { it.lastError != null })
    }

    @Test fun aToolLevelAuthErrorOnA200IsAFailedAppWithAShortReadableNote() = runBlocking {
        dao.upsertConnection(dao.getConnection("gmail")!!.copy(accountId = "ca_g"))
        overrides[ComposioCatalog.GMAIL_FETCH] = 200 to RecordedPayloads.text("tool_error_auth.json")
        val gmail = runner.run().apps.first { it.app == "gmail" }
        assertEquals("failed", gmail.state)
        assertTrue(gmail.note!!.startsWith("401 Client Error"))
        assertTrue(gmail.note!!.length <= 160)
    }

    @Test fun aSuccessfulAnswerWithNullDataIsAnEmptyPullNotAFailure() = runBlocking {
        dao.upsertConnection(dao.getConnection("gmail")!!.copy(accountId = "ca_g"))
        overrides[ComposioCatalog.GMAIL_FETCH] = 200 to RecordedPayloads.text("tool_data_variants.json")
        val gmail = runner.run().apps.first { it.app == "gmail" }
        assertEquals("done", gmail.state); assertEquals(0, gmail.found)
    }

    // ---- the read-only guarantee, through the real client ----

    @Test fun noWriteToolCanBeSentAndNothingReachesTheServer() = runBlocking {
        val client = ComposioClient({ "k" }, server.url("/api/v3").toString().trimEnd('/'), OkHttpClient(), 1, { })
        val writes = listOf(
            "GMAIL_SEND_EMAIL", "GMAIL_CREATE_EMAIL_DRAFT", "GMAIL_DELETE_MESSAGE", "GMAIL_MOVE_TO_TRASH", "GMAIL_ADD_LABEL_TO_EMAIL",
            "GOOGLECALENDAR_CREATE_EVENT", "GOOGLECALENDAR_DELETE_EVENT", "GOOGLECALENDAR_UPDATE_EVENT",
            "CLICKUP_CREATE_TASK", "CLICKUP_UPDATE_TASK", "CLICKUP_DELETE_TASK",
            "SLACK_SEND_MESSAGE", "SLACK_DELETE_A_MESSAGE_FROM_A_CHAT", "SLACK_SENDS_A_MESSAGE_TO_A_SLACK_CHANNEL",
            // near misses must not slip through
            "GMAIL_FETCH_EMAILS/../GMAIL_SEND_EMAIL", "",
        )
        for (slug in writes) {
            try { client.execute(slug, "ca", null, emptyMap()); fail("[$slug] ran") }
            catch (e: ComposioException) { assertEquals(slug, ComposioErrorKind.BLOCKED, e.kind) }
        }
        assertEquals(0, server.requestCount)
        // the gate reads the slug by name, so case and stray spaces do not turn a read into a block
        for (slug in listOf("gmail_fetch_emails", "GMAIL_FETCH_EMAILS ", " GMAIL_FETCH_EMAILS")) {
            assertTrue("[$slug] is a read", ReadToolRules.isReadTool(slug))
        }
        // and the whole allow-list is reads only
        val bad = Regex("(SEND|CREATE|DELETE|UPDATE|REMOVE|PATCH|POST|REPLY|FORWARD|MOVE|TRASH|ADD|INSERT|MODIFY|ARCHIVE)")
        assertTrue(ComposioCatalog.READ_TOOLS.toString(), ComposioCatalog.READ_TOOLS.none { bad.containsMatchIn(it) })
        assertFalse(ComposioCatalog.READ_TOOLS.isEmpty())
    }
}
