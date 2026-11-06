package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.CollectionProgressBus
import com.example.memgallery.lifeagent.collect.CollectionRunner
import com.example.memgallery.lifeagent.collect.LifeAgentSettings
import com.example.memgallery.lifeagent.collect.NoiseFilter
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.data.AppState
import com.example.memgallery.lifeagent.data.LifeConnectionEntity
import com.example.memgallery.lifeagent.data.LifeLearningEntity
import com.example.memgallery.lifeagent.data.LifeToolkitEntity
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.lifeagent.data.runStatsFromJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectionRunnerTest : DbTestBase() {
    private class FakeSettings(var key: String? = "k") : LifeAgentSettings {
        var lastRun = 0L
        override suspend fun composioKey() = key
        override suspend fun setLastRun(at: Long) { lastRun = at }
    }

    private val settings = FakeSettings()
    private val bus = CollectionProgressBus()
    private lateinit var api: FakeComposio
    private lateinit var runner: CollectionRunner

    private val gmailJson = """{"messages":[
      {"messageId":"g1","subject":"Signed lease","sender":"Nomsa Mahlangu <nomsa@umoya.example>","messageText":"Please send the signed lease by Sat 7 Nov 16:00","messageTimestamp":"2026-11-06T09:00:00Z"},
      {"messageId":"g2","subject":"Invoice INV-2043","sender":"Anele Jacobs <anele@kloof.example>","messageText":"Invoice INV-2043 for R8,400 due 13 Nov","messageTimestamp":"2026-11-06T09:30:00Z"},
      {"messageId":"g3","subject":"Reminder: invoice INV-2043 R8,400 due 13 Nov","sender":"Anele Jacobs <anele@kloof.example>","messageText":"Second mail about INV-2043, amount due R8,400 by 13 Nov","messageTimestamp":"2026-11-06T09:45:00Z"},
      {"messageId":"g4","subject":"Takealot Black Friday, up to 60% off","sender":"deals@takealot.example","messageText":"Shop now","messageTimestamp":"2026-11-06T09:50:00Z"},
      {"messageId":"g5","subject":"Your Vodacom bill is ready","sender":"billing@vodacom.example","messageText":"View","messageTimestamp":"2026-11-06T09:51:00Z"},
      {"messageId":"g6","subject":"Security alert: new sign-in","sender":"no-reply@accounts.example","messageText":"Chrome on Windows","messageTimestamp":"2026-11-06T09:52:00Z"},
      {"messageId":"g7","subject":"Lunch?","sender":"Jaco Steyn <jaco@photo.example>","messageText":"sounds good see you","messageTimestamp":"2026-11-06T09:53:00Z"}]}"""

    @Before fun setUp() {
        api = FakeComposio(accounts = listOf(FakeComposio.account("ca_g", "gmail"), FakeComposio.account("ca_c", "googlecalendar")))
        api.on(ComposioCatalog.GMAIL_FETCH) { FakeComposio.ok(gmailJson) }
        runner = CollectionRunner(dao, api, settings, null, bus).apply { clock = { Fixtures.NOW }; zone = Fixtures.ZONE }
        runBlocking {
            dao.upsertConnection(LifeConnectionEntity("gmail", "Gmail", true, null, 0L, null, null, null))
            dao.upsertConnection(LifeConnectionEntity("gcal", "Google Calendar", true, null, 0L, null, null, null))
        }
    }

    @Test fun oneAppFailingNeverFailsTheRunAndKeepsItsOwnLastError() = runBlocking {
        api.fail(ComposioCatalog.CAL_LIST, ComposioErrorKind.SERVER, "Composio had a problem (503)")
        val r = runner.run()
        assertEquals("done", r.status)
        val gmail = r.apps.first { it.app == "gmail" }; val cal = r.apps.first { it.app == "gcal" }
        assertEquals("done", gmail.state); assertEquals("failed", cal.state)
        assertEquals("Composio had a problem (503)", cal.note)
        assertEquals("Composio had a problem (503)", dao.getConnection("gcal")!!.lastError)
        assertNull(dao.getConnection("gmail")!!.lastError)
        // Only the app that worked moves its pull time, so the failed one is retried from where it was.
        assertEquals(Fixtures.NOW, dao.getConnection("gmail")!!.lastPulledAt)
        assertEquals(0L, dao.getConnection("gcal")!!.lastPulledAt)
        assertEquals(Fixtures.NOW, settings.lastRun)
    }

    @Test fun theWholeStoryBecomesProposalsWithEveryDropCountedAndExplained() = runBlocking {
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        val r = runner.run()
        val kinds = dao.getProposed().associateBy { it.sourceId }
        assertEquals(setOf("g1", "g2", "g6"), kinds.keys)          // lease, one invoice, security
        assertEquals("commitment", kinds["g1"]!!.kind); assertEquals("money", kinds["g2"]!!.kind); assertEquals("security", kinds["g6"]!!.kind)
        assertEquals(3, r.proposalsCreated)
        val gmail = r.apps.first { it.app == "gmail" }
        assertEquals(7, gmail.found); assertEquals(4, gmail.dropped); assertEquals(3, gmail.proposed)
        val (apps, noise) = runStatsFromJson(dao.observeLatestRun().first()!!.appStatsJson)
        assertEquals(2, apps.size)
        assertEquals(1, noise[NoiseFilter.R_PROMO]); assertEquals(1, noise[NoiseFilter.R_RECEIPT])
        assertEquals(1, noise[NoiseFilter.R_DUPLICATE]); assertEquals(1, noise[NoiseFilter.R_NOTHING])
        assertEquals(4, r.noiseDropped)
    }

    @Test fun theSameThingMentionedTwiceIsOneMemory() = runBlocking {
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        runner.run()
        assertEquals(1, dao.getProposed().count { it.fingerprint.contains("INV-2043") })
    }

    @Test fun aSecondRunNeverProposesTheSameItemsAgainEvenAfterTheyAreDecided() = runBlocking {
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        runner.run()
        val first = dao.getProposed()
        dao.setProposalStatus(first.first().id, "dropped", 1L)
        val r2 = runner.run()
        assertEquals(0, r2.proposalsCreated)
        assertEquals(2, dao.getProposed().size)
        assertEquals(3, dao.observeAllProposals().first().size)
    }

    @Test fun calendarFillsMeetingsAndOnlyAChangedMeetingWithOthersIsProposed() = runBlocking {
        api.on(ComposioCatalog.GMAIL_FETCH) { FakeComposio.ok("""{"messages":[]}""") }
        api.on(ComposioCatalog.CAL_LIST) {
            FakeComposio.ok("""{"items":[
              {"id":"e1","summary":"Ndlovu pitch","start":{"dateTime":"2026-11-12T10:00:00+02:00"},"end":{"dateTime":"2026-11-12T11:00:00+02:00"},"attendees":[{},{}],"updated":"2026-11-06T11:59:00+02:00","created":"2026-11-01T09:00:00+02:00"},
              {"id":"e2","summary":"Focus time","start":{"dateTime":"2026-11-09T09:00:00+02:00"},"end":{"dateTime":"2026-11-09T10:00:00+02:00"},"updated":"2026-10-01T09:00:00+02:00","created":"2026-10-01T09:00:00+02:00"},
              {"id":"e3","summary":"Cancelled thing","status":"cancelled","start":{"dateTime":"2026-11-10T09:00:00+02:00"},"end":{"dateTime":"2026-11-10T10:00:00+02:00"}}]}""")
        }
        val r = runner.run()
        val meetings = dao.observeMeetingsFrom(0L).first()
        assertEquals(setOf("e1", "e2"), meetings.map { it.id }.toSet())
        assertEquals(listOf("e1"), dao.getProposed().map { it.sourceId })
        assertEquals(1, r.proposalsCreated)
    }

    @Test fun clickUpTasksLandLocallyAndKeepLocalDoneStateOnRefresh() = runBlocking {
        dao.upsertConnection(LifeConnectionEntity("clickup", "ClickUp", true, null, 0L, null, null, null))
        api.accounts = api.accounts + FakeComposio.account("ca_k", "clickup")
        api.on(ComposioCatalog.GMAIL_FETCH) { FakeComposio.ok("""{"messages":[]}""") }
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        api.on(ComposioCatalog.CLICKUP_WORKSPACES) { FakeComposio.ok("""{"teams":[{"id":"900"}]}""") }
        api.on(ComposioCatalog.CLICKUP_TASKS) {
            FakeComposio.ok("""{"tasks":[{"id":"t1","name":"Q4 expense sheet for Kagiso","status":{"status":"open"},"due_date":"${Fixtures.at(11, 9, 12)}","list":{"name":"Studio"}}]}""")
        }
        runner.run()
        assertEquals("Q4 expense sheet for Kagiso", dao.getTask("t1")!!.title)
        assertEquals(listOf("task:t1"), dao.getProposed().map { it.fingerprint })
        dao.setTaskDone("t1", true)
        runner.run()
        assertTrue(dao.getTask("t1")!!.doneLocal)
        assertEquals(1, dao.observeAllProposals().first().size)
    }

    @Test fun learningBiasAppliesFromTheNextRun() = runBlocking {
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        api.on(ComposioCatalog.GMAIL_FETCH) {
            FakeComposio.ok("""{"messages":[{"messageId":"x1","subject":"Studio notes","sender":"Thandeka <thandeka@studio.example>","messageText":"Prices go up on 11 Nov","messageTimestamp":"2026-11-06T09:00:00Z"}]}""")
        }
        repeat(3) { dao.insertLearning(LifeLearningEntity(app = "gmail", senderKey = "thandeka@studio.example", kind = "deadline", decision = "drop", decidedAt = it.toLong())) }
        val r = runner.run()
        assertEquals(0, r.proposalsCreated)
        val (_, noise) = runStatsFromJson(dao.observeLatestRun().first()!!.appStatsJson)
        assertEquals(1, noise[NoiseFilter.R_LEARNED_SENDER])
    }

    @Test fun noKeyOrNoConnectedAppIsASkippedRunNotAFailure() = runBlocking {
        settings.key = null
        val a = runner.run()
        assertEquals("skipped", a.status); assertEquals(0, api.calls.size)
        settings.key = "k"
        dao.upsertConnection(dao.getConnection("gmail")!!.copy(connected = false))
        dao.upsertConnection(dao.getConnection("gcal")!!.copy(connected = false))
        assertEquals("skipped", runner.run().status)
        assertNull(dao.observeLatestRun().first())
    }

    @Test fun everyAppFailingMakesTheRunFailedAndTheTimeIsNotAdvanced() = runBlocking {
        api.fail(ComposioCatalog.GMAIL_FETCH, ComposioErrorKind.AUTH, "Composio did not accept the key (401)")
        api.fail(ComposioCatalog.CAL_LIST, ComposioErrorKind.AUTH, "Composio did not accept the key (401)")
        val r = runner.run()
        assertEquals("failed", r.status); assertNotNull(r.error)
        assertEquals(0L, settings.lastRun)
        assertEquals("failed", dao.observeLatestRun().first()!!.status)
    }

    @Test fun anAppWithNoComposioAccountFailsAloneWithAClearMessage() = runBlocking {
        api.accounts = listOf(FakeComposio.account("ca_g", "gmail"))
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        val r = runner.run()
        assertEquals("failed", r.apps.first { it.app == "gcal" }.state)
        assertEquals("No Google Calendar account is connected in Composio", dao.getConnection("gcal")!!.lastError)
        assertEquals("done", r.status)
    }

    @Test fun theAccountListFailingStillUsesStoredAccountIds() = runBlocking {
        api.listFails = true
        dao.upsertConnection(dao.getConnection("gmail")!!.copy(accountId = "ca_g", userId = "u"))
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        val r = runner.run()
        assertEquals("done", r.apps.first { it.app == "gmail" }.state)
        assertEquals("failed", r.apps.first { it.app == "gcal" }.state)
    }

    // ---- any connected toolkit, not only the four hand-written ones ----

    private val notionSearch = """{"object":"list","results":[
      {"object":"page","id":"pg-1","title":"Studio lease renewal for Kagiso","text":"The landlord needs the signed lease back by Fri 13 Nov","created_time":"2026-11-06T07:10:00Z","url":"https://notion.example/pg-1"},
      {"object":"page","id":"pg-2","title":"Groceries","text":"milk, bread, coffee","created_time":"2026-11-06T07:20:00Z","url":"https://notion.example/pg-2"}]}"""

    private val notionDatabase = """{"results":[
      {"object":"page","id":"db-1","title":"Invoice INV-77 from Anele Jacobs","text":"Invoice INV-77 for R8,400 due 13 Nov","created_at":"2026-11-06T08:00:00Z","url":"https://notion.example/db-1"}]}"""

    private suspend fun connectNotion(label: String = "notion") {
        dao.upsertConnection(LifeConnectionEntity("notion", label, true, null, 0L, null, null, null))
        api.accounts = api.accounts + FakeComposio.account("ca_n", "notion")
        api.on(ComposioCatalog.GMAIL_FETCH) { FakeComposio.ok("""{"messages":[]}""") }
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
    }

    @Test fun anUnknownToolkitIsReadThroughItsOwnReadToolsAndBecomesProposals() = runBlocking {
        connectNotion(label = "notion") // no display name on the connection: the cached catalog row must name it
        dao.upsertToolkits(listOf(LifeToolkitEntity("notion", "Notion", null, null, null, null, false, null, null, Fixtures.NOW)))
        api.toolSpecs = mapOf("notion" to listOf("NOTION_SEARCH_NOTION_PAGE", "NOTION_GET_DATABASE", "NOTION_CREATE_PAGE"))
        api.on("NOTION_SEARCH_NOTION_PAGE") { FakeComposio.ok(notionSearch) }
        api.on("NOTION_GET_DATABASE") { FakeComposio.ok(notionDatabase) }

        val r = runner.run()
        val notion = r.apps.first { it.app == "notion" }
        assertEquals("done", notion.state)
        assertEquals(3, notion.found)
        assertEquals(1, notion.dropped) // the groceries page has nothing to act on
        assertEquals(2, notion.proposed)
        assertEquals(2, r.proposalsCreated)

        val kept = dao.getProposed().filter { it.app == "notion" }.associateBy { it.sourceId }
        assertEquals(setOf("pg-1", "db-1"), kept.keys)
        assertEquals("money", kept["db-1"]!!.kind)
        assertTrue(kept["pg-1"]!!.kind in setOf("commitment", "deadline", "document"))
        assertTrue(kept["pg-1"]!!.title.contains("lease", ignoreCase = true))

        // Only read tools ran, with empty arguments, and the write tool was never called.
        val notionCalls = api.calls.filter { it.first.startsWith("NOTION") }
        assertEquals(setOf("NOTION_SEARCH_NOTION_PAGE", "NOTION_GET_DATABASE"), notionCalls.map { it.first }.toSet())
        assertTrue(api.calls.all { ReadToolRules.isReadTool(it.first) })
        assertTrue(notionCalls.all { it.second.isEmpty() })

        // The Collecting screen tells the truth for a toolkit it has no parser for.
        val shown = bus.progress.value.apps.first { it.app == "notion" }
        assertEquals("Notion", shown.label)
        assertEquals(notion.found, shown.found); assertEquals(notion.dropped, shown.dropped)
        assertEquals(AppState.DONE, shown.state)
        assertNull(shown.note)
        assertEquals(Fixtures.NOW, dao.getConnection("notion")!!.lastPulledAt)
    }

    @Test fun aToolkitWithNoReadToolFailsAloneWithAClearNote() = runBlocking {
        connectNotion()
        api.toolSpecs = mapOf("notion" to listOf("NOTION_CREATE_PAGE", "NOTION_DELETE_PAGE"))
        val r = runner.run()
        assertEquals("done", r.status)
        val notion = r.apps.first { it.app == "notion" }
        assertEquals("failed", notion.state)
        assertEquals("No read tool found for Notion", notion.note)
        assertEquals("No read tool found for Notion", dao.getConnection("notion")!!.lastError)
        assertEquals(0L, dao.getConnection("notion")!!.lastPulledAt)
        assertTrue(api.calls.none { it.first.startsWith("NOTION") })
        assertEquals(AppState.FAILED, bus.progress.value.apps.first { it.app == "notion" }.state)
        assertEquals("done", r.apps.first { it.app == "gmail" }.state)
    }

    @Test fun oneToolFailingDoesNotStopTheOthersAndTheNoteSaysWhich() = runBlocking {
        connectNotion()
        api.toolSpecs = mapOf("notion" to listOf("NOTION_SEARCH_NOTION_PAGE", "NOTION_GET_DATABASE"))
        api.fail("NOTION_SEARCH_NOTION_PAGE", ComposioErrorKind.SERVER, "Composio had a problem (503)")
        api.on("NOTION_GET_DATABASE") { FakeComposio.ok(notionDatabase) }
        val r = runner.run()
        val notion = r.apps.first { it.app == "notion" }
        assertEquals("done", notion.state)
        assertEquals(1, notion.proposed)
        assertTrue(notion.note!!.contains("NOTION_SEARCH_NOTION_PAGE refused"))
        assertEquals("Invoice INV-77", dao.getProposed().first { it.app == "notion" }.sourceTitle.substringBefore(" from"))
        assertNull(dao.getConnection("notion")!!.lastError)
    }

    @Test fun aToolThatAnswersWithNothingIsNotedAndStillCountsAsDone() = runBlocking {
        connectNotion()
        api.toolSpecs = mapOf("notion" to listOf("NOTION_SEARCH_NOTION_PAGE"))
        api.on("NOTION_SEARCH_NOTION_PAGE") { FakeComposio.ok("""{"results":[]}""") }
        val r = runner.run()
        val notion = r.apps.first { it.app == "notion" }
        assertEquals("done", notion.state)
        assertEquals(0, notion.found)
        assertEquals("NOTION_SEARCH_NOTION_PAGE returned nothing", notion.note)
    }

    @Test fun aToolkitWhoseEveryReadToolFailsFailsAlone() = runBlocking {
        connectNotion()
        api.toolSpecs = mapOf("notion" to listOf("NOTION_SEARCH_NOTION_PAGE", "NOTION_GET_DATABASE"))
        api.fail("NOTION_SEARCH_NOTION_PAGE", ComposioErrorKind.TOOL_FAILED, "Token expired")
        api.fail("NOTION_GET_DATABASE", ComposioErrorKind.TOOL_FAILED, "Token expired")
        val r = runner.run()
        assertEquals("done", r.status)
        val notion = r.apps.first { it.app == "notion" }
        assertEquals("failed", notion.state)
        assertTrue(notion.note!!.contains("NOTION_SEARCH_NOTION_PAGE refused: Token expired"))
        assertEquals("done", r.apps.first { it.app == "gmail" }.state)
    }

    @Test fun onlyReadToolsAreEverCalledAndProgressEndsIdle() = runBlocking {
        api.on(ComposioCatalog.CAL_LIST) { FakeComposio.ok("""{"items":[]}""") }
        runner.run()
        assertTrue(api.calls.isNotEmpty())
        assertTrue(api.calls.all { it.first in ComposioCatalog.READ_TOOLS })
        val p = bus.progress.value
        assertFalse(p.running); assertTrue(p.apps.all { it.state == AppState.DONE })
    }
}
