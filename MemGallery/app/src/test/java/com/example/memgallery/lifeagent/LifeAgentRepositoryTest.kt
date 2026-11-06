package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.CollectionProgressBus
import com.example.memgallery.lifeagent.collect.CollectionRunner
import com.example.memgallery.lifeagent.collect.LifeAgentSettings
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import com.example.memgallery.lifeagent.data.LifeApp
import com.example.memgallery.lifeagent.data.LifeConnectionEntity
import com.example.memgallery.lifeagent.data.LifeKind
import com.example.memgallery.lifeagent.data.LifeMeetingEntity
import com.example.memgallery.lifeagent.data.LifeProposalEntity
import com.example.memgallery.lifeagent.data.LifeTaskEntity
import com.example.memgallery.lifeagent.data.ProposalStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LifeAgentRepositoryTest : DbTestBase() {
    private lateinit var repo: LifeAgentRepository
    private var hookCalls = 0
    private val api = FakeComposio()

    @Before fun setUp() {
        val settings = object : LifeAgentSettings {
            override suspend fun composioKey() = "k"
            override suspend fun setLastRun(at: Long) {}
        }
        val runner = CollectionRunner(dao, FakeComposio(), settings, null, CollectionProgressBus())
        repo = LifeAgentRepository(dao, { runner }, CollectionProgressBus(), api).apply { clock = { Fixtures.NOW }; zone = Fixtures.ZONE }
        repo.onDataChanged = { hookCalls++ }
    }

    private fun proposal(id: String, kind: String = "commitment", person: String? = "Nomsa Mahlangu", due: Long? = Fixtures.at(11, 7, 16), fp: String = id, title: String = "Nomsa is waiting on: Signed lease") =
        LifeProposalEntity(id, kind, title, "detail", "gmail", "src-$id", "Signed lease", "https://m/$id", person, due, null, null, 0.8f, "proposed", "names a person and a date", fp, null, Fixtures.NOW - 3_600_000L, null)

    @Test fun connectionsAlwaysListTheFourAppsEvenWithNothingStored() = runBlocking {
        val c = repo.connections.first()
        assertEquals(listOf("gmail", "gcal", "clickup", "slack"), c.map { it.id })
        assertTrue(c.none { it.connected })
        dao.upsertConnection(LifeConnectionEntity("slack", "Slack", true, "team", 5L, "oops", "ca", "u"))
        val slack = repo.connections.first().first { it.id == "slack" }
        assertTrue(slack.connected); assertEquals("oops", slack.lastError); assertEquals(5L, slack.lastPulledAt)
    }

    @Test fun connectFindsTheActiveAccountAndStoresItsIdAndFailsClearlyWhenThereIsNone() = runBlocking {
        api.accounts = listOf(
            com.example.memgallery.lifeagent.composio.ConnectedAccount("old", "gmail", "u0", "EXPIRED", null),
            com.example.memgallery.lifeagent.composio.ConnectedAccount("ca_g", "gmail", "u1", "ACTIVE", "work"),
        )
        repo.connectToolkit("gmail")
        val g = dao.getConnection("gmail")!!
        assertTrue(g.connected); assertEquals("ca_g", g.accountId); assertEquals("u1", g.userId); assertEquals("work", g.accountLabel); assertNull(g.lastError)
        // Nothing connected in his Composio project: he gets a connect link, or a plain failure when Composio cannot
        // make one. Either way the row stays not connected and the reason is kept for the screen.
        val slackOutcome = repo.connectToolkit("slack")
        val s = dao.getConnection("slack")!!
        assertFalse(s.connected)
        when (slackOutcome) {
            is com.example.memgallery.lifeagent.data.ConnectOutcome.Browser -> assertTrue(slackOutcome.url.isNotBlank())
            is com.example.memgallery.lifeagent.data.ConnectOutcome.Failed -> assertEquals(slackOutcome.message, s.lastError)
            is com.example.memgallery.lifeagent.data.ConnectOutcome.Connected -> error("slack must not read as connected")
        }
        api.listFails = true
        repo.connectToolkit("gmail")
        assertFalse(dao.getConnection("gmail")!!.connected)
        assertEquals("No connection to Composio", dao.getConnection("gmail")!!.lastError)
    }

    @Test fun disconnectStopsPullingButKeepsTheRowAndClearsTheAccount() = runBlocking {
        dao.upsertConnection(LifeConnectionEntity("gmail", "Gmail", true, null, 9L, "x", "ca", "u"))
        repo.disconnectToolkit("gmail")
        val row = dao.getConnection("gmail")!!
        assertFalse(row.connected); assertNull(row.accountId); assertNull(row.lastError); assertEquals(9L, row.lastPulledAt)
    }

    @Test fun keepMakesATaskAPersonAndALearningRowAndLeavesTheDeck() = runBlocking {
        dao.insertProposals(listOf(proposal("p1")))
        assertEquals(1, repo.proposedCount.first())
        repo.keep("p1")
        assertEquals(0, repo.proposedCount.first()); assertTrue(repo.proposals.first().isEmpty())
        assertEquals(ProposalStatus.KEPT, repo.allProposals.first().single().status)
        val task = dao.getTask("p:p1")!!
        assertEquals("memory", task.source); assertEquals(Fixtures.at(11, 7, 16), task.dueAt)
        val person = repo.people.first().single()
        assertEquals("Nomsa Mahlangu", person.name); assertEquals(1, person.openCommitments)
        assertEquals(1, dao.getLearning().count { it.decision == "keep" && it.kind == "commitment" })
        assertTrue(hookCalls > 0)
    }

    @Test fun keepingTwiceOrAfterDroppingDoesNothingTheSecondTime() = runBlocking {
        dao.insertProposals(listOf(proposal("p1")))
        repo.keep("p1"); repo.keep("p1"); repo.drop("p1")
        assertEquals(1, dao.getLearning().size)
        assertEquals(ProposalStatus.KEPT, repo.allProposals.first().single().status)
    }

    @Test fun dropRecordsTheSenderKeyFromTheSourceItemForLearning() = runBlocking {
        dao.insertItems(listOf(com.example.memgallery.lifeagent.data.LifeItemEntity("gmail:src-p2", "gmail", "src-p2", "email", "t", "s", "Anele <ANELE@kloof.example>", null, 1L, 1L, "gmail:src-p2")))
        dao.insertProposals(listOf(proposal("p2", fp = "fp2")))
        repo.drop("p2")
        val l = dao.getLearning().single()
        assertEquals("drop", l.decision); assertEquals("anele@kloof.example", l.senderKey)
        assertEquals(ProposalStatus.DROPPED, repo.allProposals.first().single().status)
        assertNull(dao.getTask("p:p2"))
    }

    @Test fun keepAllKeepsEveryProposedOne() = runBlocking {
        dao.insertProposals(listOf(proposal("a", fp = "fa"), proposal("b", fp = "fb", person = null, due = null), proposal("c", fp = "fc")))
        repo.keepAll()
        assertEquals(0, repo.proposedCount.first())
        assertEquals(3, dao.getLearning().size)
        assertNull(dao.getTask("p:b"))
    }

    @Test fun markDoneUndoneAndSnoozeAreLocalAndFlowThrough() = runBlocking {
        dao.insertTask(LifeTaskEntity("t1", "clickup", "Studio", "Chase Jaco", Fixtures.at(11, 6, 17), "open", false, null, null))
        repo.markDone("t1")
        assertTrue(repo.tasks.first().single().doneLocal)
        repo.markUndone("t1")
        assertFalse(repo.tasks.first().single().doneLocal)
        repo.snooze("t1", Fixtures.NOW + 3_600_000L)
        assertEquals(Fixtures.NOW + 3_600_000L, repo.tasks.first().single().snoozedUntil)
    }

    @Test fun closedClickUpTasksLeaveTheOpenList() = runBlocking {
        dao.insertTask(LifeTaskEntity("t1", "clickup", null, "Old", null, "Complete", false, null, null))
        assertTrue(repo.tasks.first().isEmpty())
    }

    @Test fun dailyReportSortsTheStoryIntoDueOverdueAwareMeetingsAndOpen() = runBlocking {
        // Friday 6 Nov 2026, as in the design mocks.
        dao.insertTask(LifeTaskEntity("t-jaco", "clickup", null, "Chase Jaco for the retouched set", Fixtures.at(11, 6, 17), "open", false, null, null))
        dao.insertTask(LifeTaskEntity("t-flights", "clickup", null, "Book the Durban workshop flights", Fixtures.at(11, 4, 12), "open", false, null, null))
        dao.insertTask(LifeTaskEntity("t-q4", "clickup", null, "Q4 expense sheet for Kagiso", Fixtures.at(11, 9), "open", false, null, null))
        dao.insertTask(LifeTaskEntity("t-done", "clickup", null, "Already done", Fixtures.at(11, 6, 9), "open", true, null, null))
        dao.insertTask(LifeTaskEntity("t-snz", "clickup", null, "Snoozed overdue", Fixtures.at(11, 3), "open", false, Fixtures.NOW + 1000, null))
        dao.insertTask(LifeTaskEntity("t-closed", "clickup", null, "Closed", Fixtures.at(11, 6, 8), "complete", false, null, null))
        dao.upsertMeetings(listOf(
            LifeMeetingEntity("m1", "Call with Kagiso about Q4", Fixtures.at(11, 6, 15, 30), Fixtures.at(11, 6, 16), null, "https://meet.example"),
            LifeMeetingEntity("m2", "Ndlovu & Sons pitch", Fixtures.at(11, 12, 10), Fixtures.at(11, 12, 11), "Studio", null),
            LifeMeetingEntity("m3", "Last week", Fixtures.at(10, 30, 10), Fixtures.at(10, 30, 11), null, null),
            LifeMeetingEntity("m4", "Far future", Fixtures.at(12, 20, 10), Fixtures.at(12, 20, 11), null, null),
        ))
        dao.insertProposals(listOf(
            proposal("k1", title = "Nomsa is waiting on the signed lease").copy(status = "kept"),
            proposal("k-old", fp = "old").copy(status = "kept", collectedAt = Fixtures.NOW - 20 * Fixtures.DAY),
            proposal("k-past", fp = "past", due = Fixtures.at(11, 1)).copy(status = "kept"),
            proposal("waiting", fp = "w"),
        ))
        val r = repo.dailyReport().first()
        assertEquals(listOf("t-jaco"), r.dueToday.map { it.id })
        assertEquals(listOf("t-flights"), r.overdue.map { it.id })                 // snoozed and done and closed stay out
        assertEquals(listOf("k1"), r.aware.map { it.id })                           // fresh, kept, not already past
        assertEquals(listOf("m1", "m2"), r.meetings.map { it.id })                 // today and the coming week only
        assertEquals(listOf("t-snz", "t-flights", "t-jaco", "t-q4"), r.open.map { it.id })
        assertEquals(1, r.toReview)
        assertEquals(Fixtures.at(11, 6), r.date)
    }

    @Test fun askContextDescribesAProposalAndATask() = runBlocking {
        dao.insertItems(listOf(com.example.memgallery.lifeagent.data.LifeItemEntity("gmail:src-p1", "gmail", "src-p1", "email", "t", "Please send the lease", "Nomsa <n@x.example>", null, 1L, 1L, "gmail:src-p1")))
        dao.insertProposals(listOf(proposal("p1")))
        dao.insertTask(LifeTaskEntity("t1", "clickup", "Studio", "Chase Jaco", Fixtures.at(11, 6, 17), "open", false, null, null))
        val p = repo.askContext("p1")
        assertTrue(p, p.contains("Nomsa") && p.contains("Gmail") && p.contains("Please send the lease") && p.contains("Sat 7 Nov"))
        val t = repo.askContext("t1")
        assertTrue(t, t.contains("Chase Jaco") && t.contains("Studio"))
        assertEquals("", repo.askContext("nope"))
    }

    @Test fun personPageSplitsWhatYouOweFromWhatIsOwedToYou() = runBlocking {
        dao.insertProposals(listOf(
            proposal("a", "commitment", "Jaco Steyn", fp = "fa"),
            proposal("b", "awaiting", "Jaco Steyn", fp = "fb"),
            proposal("c", "money", "Anele Jacobs", fp = "fc"),
        ))
        repo.keepAll()
        val d = repo.person("jaco steyn").first()
        assertEquals(listOf(LifeKind.COMMITMENT), d.youOwe.map { it.kind })
        assertEquals(listOf(LifeKind.AWAITING), d.owedToYou.map { it.kind })
        assertEquals(2, d.recent.size)
        assertNotNull(d.person); assertEquals(2, d.person!!.openCommitments)
    }

    @Test fun learnedSummaryReflectsDecisionsAndTheLastRunNoise() = runBlocking {
        dao.insertProposals(listOf(proposal("a", fp = "fa"), proposal("b", fp = "fb")))
        repo.keep("a"); repo.drop("b")
        val l = repo.learned.first()
        assertEquals(1, l.kept); assertEquals(1, l.dropped)
    }
}
