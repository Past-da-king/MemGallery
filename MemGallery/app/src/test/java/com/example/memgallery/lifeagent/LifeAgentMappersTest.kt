package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.data.*
import com.example.memgallery.lifeagent.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * State mapping of every Life Agent ViewModel, with fake repository data (the real models, no database, no network).
 * Today is Fri 6 Nov 2026, 17:09 in Johannesburg.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LifeAgentMappersTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 11): Long =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(6, 17, 9)
    private val ctx by lazy { LifeCtx(ContextText(RuntimeEnvironment.getApplication()), now, zone, Locale.ENGLISH) }

    private fun proposal(
        id: String, kind: LifeKind, title: String, app: String = "gmail", person: String? = null, due: Long? = null,
        confidence: Float = 0.9f, reason: String = "names a person and a date", task: String? = null, status: ProposalStatus = ProposalStatus.PROPOSED,
        collected: Long = at(6, 17, 4),
    ) = LifeProposal(id, kind, title, "", app, "s-$id", "Source of $id", "https://example.com/$id", person, due, null, null, confidence, status, reason, task, collected, null)

    // ---- Connections ----

    @Test fun connectionsStateKeepsEveryToolkitAndHidesTheKey() {
        val s = ConnectionsUiState(
            keySet = true,
            maskedKey = maskKey("ak_live_abcdef123456"),
            connected = listOf(ToolkitRowUi("gmail", "Gmail", connected = true, lastPulled = "17:04", known = true)),
            others = listOf(ToolkitRowUi("notion", "Notion", error = "rate limited")),
        )
        assertTrue(s.keySet)
        assertTrue(s.maskedKey.endsWith("3456"))
        assertFalse("the key must never appear", s.maskedKey.contains("abcdef"))
        assertEquals("17:04", s.connected.single().lastPulled)
        assertEquals("rate limited", s.others.single().error)
    }

    @Test fun connectionsWithNoKeyIsAClearEmptyState() {
        val s = ConnectionsUiState()
        assertFalse(s.keySet)
        assertEquals("", s.maskedKey)
        assertTrue(s.connected.isEmpty() && s.others.isEmpty())
    }

    @Test fun shortKeysShowOnlyDots() {
        assertEquals("•".repeat(15), maskKey("short"))
        assertEquals("", maskKey(null))
    }

    // ---- Collecting ----

    @Test fun collectingRunningCountsStepsAndTotals() {
        val p = CollectionProgress(true, at(6, 17, 0), listOf(
            AppProgress("gmail", "Gmail", 34, 12, AppState.DONE, "8 promotions"),
            AppProgress("gcal", "Google Calendar", 4, 2, AppState.DONE, null),
            AppProgress("clickup", "ClickUp", 3, 0, AppState.RUNNING, "Reading Q4 sheet"),
            AppProgress("slack", "Slack", 0, 0, AppState.WAITING, null),
        ))
        val s = mapCollecting(p, null, false, emptyList(), ctx)
        assertTrue(s.running)
        assertEquals(3, s.step)
        assertEquals(4, s.steps)
        assertEquals("17:00", s.startedLabel)
        assertEquals(41, s.found)
        assertEquals(14, s.dropped)
        assertEquals(listOf("8 promotions"), s.apps[0].reasons)
        assertEquals(StepState.Running, s.apps[2].state)
        assertEquals("Reading Q4 sheet", s.apps[2].note)
    }

    @Test fun collectingSkippedAppsDoNotCountAsSteps() {
        val p = CollectionProgress(true, now, listOf(
            AppProgress("gmail", "Gmail", 0, 0, AppState.RUNNING, null),
            AppProgress("slack", "Slack", 0, 0, AppState.SKIPPED, null),
        ))
        val s = mapCollecting(p, null, false, emptyList(), ctx)
        assertEquals(1, s.steps)
    }

    @Test fun collectingFinishedUsesTheLatestRunAndShowsFailures() {
        val run = LifeRun("r1", at(6, 17, 0), at(6, 17, 4), "done", listOf(
            AppRunStat("gmail", 34, 12, 6, "done", "8 promotions"),
            AppRunStat("slack", 0, 0, 0, "failed", "Composio refused the read"),
        ), mapOf("promotion" to 8), 12, 6)
        val s = mapCollecting(CollectionProgress.IDLE, run, true, emptyList(), ctx)
        assertTrue(s.finished)
        assertEquals(6, s.proposalsCreated)
        assertEquals(StepState.Failed, s.apps.first { it.app == LifeAppId.SLACK }.state)
        assertEquals("Composio refused the read", s.apps.first { it.app == LifeAppId.SLACK }.note)
    }

    @Test fun collectingIdleSaysWhichAppsAreConnected() {
        assertTrue(mapCollecting(CollectionProgress.IDLE, null, false, emptyList(), ctx).apps.isEmpty())
        assertEquals(1, mapCollecting(CollectionProgress.IDLE, null, false, listOf(LifeAppId.GMAIL), ctx).apps.size)
    }

    // ---- Review ----

    @Test fun cardCarriesWhyDateTaskAndSuggestion() {
        val task = LifeTask("t1", "clickup", null, "Chase Jaco for the set", at(6, 17), "open", false, null, null)
        val people = listOf(LifePerson("jaco steyn", "Jaco Steyn", "Freelance photographer", null, null, now, 1, null))
        val c = mapCard(proposal("p4", LifeKind.AWAITING, "Jaco promised the set today", "slack", "Jaco Steyn", at(6, 17), 0.9f, "A commitment owed to you", "t1"), mapOf("t1" to task), people, ctx)
        assertEquals(LifeAppId.SLACK, c.app)
        assertEquals("Freelance photographer", c.personRole)
        assertEquals("A commitment owed to you", c.why)
        assertEquals("Chase Jaco for the set", c.matchedTask)
        assertEquals("Due today", c.dateTag)
        assertEquals(DateTone.Today, c.dateTone)
        assertEquals("Today, Fri 6 Nov · 17:00", c.dateLabel)
        assertTrue(c.suggestKeep)
    }

    @Test fun lowConfidenceSuggestsDropAndNoDateMeansNoDateRow() {
        val c = mapCard(proposal("p8", LifeKind.OTHER, "Takealot Black Friday", confidence = 0.2f), emptyMap(), emptyList(), ctx)
        assertFalse(c.suggestKeep)
        assertNull(c.dateLabel)
        assertNull(c.dateTag)
    }

    @Test fun overdueAndFutureDatesGetTheirTags() {
        val late = mapCard(proposal("a", LifeKind.DEADLINE, "x", due = at(4, 9)), emptyMap(), emptyList(), ctx)
        assertEquals(DateTone.Overdue, late.dateTone)
        val soon = mapCard(proposal("b", LifeKind.MONEY, "y", due = at(13, 9)), emptyMap(), emptyList(), ctx)
        assertEquals("In 7 days", soon.dateTag)
        assertEquals(DateTone.Upcoming, soon.dateTone)
    }

    @Test fun reviewStateCountsPendingAsDecidedAndAllowsUndoOnlyWhilePending() {
        val cards = listOf(mapCard(proposal("a", LifeKind.OTHER, "A"), emptyMap(), emptyList(), ctx))
        val waiting = mapReview(cards, 2, 1, true, "17:04", emptyList(), 0, "")
        assertEquals(3, waiting.decided)
        assertEquals(4, waiting.total)
        assertEquals(2, waiting.keptSoFar)
        assertTrue(waiting.canUndo)
        val none = mapReview(cards, 2, 1, null, "17:04", emptyList(), 0, "")
        assertFalse(none.canUndo)
        assertEquals(3, none.total)
    }

    @Test fun learnedMergesReasonsIntoLabelsAndSumsDrops() {
        val (rows, total) = mapLearned(LearnedSummary(6, 7, mapOf("newsletter" to 6, "promotion" to 2, "promotions_known" to 1, "receipt" to 2)), ctx)
        assertEquals(11, total)
        assertEquals("Newsletters", rows.first().label)
        assertEquals(3, rows.first { it.kind == "promotion" }.count)
    }

    @Test fun nextCollectionIsTomorrowAfterTheTimeHasPassed() {
        assertEquals("Tomorrow, Sat 7 Nov · 17:00", nextCollectionLabel(true, 17, 0, ctx))
        assertEquals("Today · 18:30", nextCollectionLabel(true, 18, 30, ctx))
        assertEquals("Daily collection is off", nextCollectionLabel(false, 17, 0, ctx))
    }

    // ---- People and person ----

    @Test fun peopleRowsShowTheOpenCommitmentBothWays() {
        val people = listOf(
            LifePerson("nomsa mahlangu", "Nomsa Mahlangu", "Client, Umoya Lodge", "n@x.example", null, now, 1, "Signed lease"),
            LifePerson("jaco steyn", "Jaco Steyn", null, null, null, at(5, 9), 1, null),
        )
        val kept = listOf(
            proposal("p1", LifeKind.COMMITMENT, "Signed lease, Sat 16:00", person = "Nomsa Mahlangu", status = ProposalStatus.KEPT),
            proposal("p4", LifeKind.AWAITING, "Retouched set", "slack", "Jaco Steyn", due = at(6, 17), status = ProposalStatus.KEPT),
        )
        val rows = mapPeople(people, kept, ctx)
        assertEquals(CommitmentKind.YouOwe, rows[0].commitmentKind)
        assertEquals("Today", rows[0].lastLabel)
        assertEquals(CommitmentKind.OwesYou, rows[1].commitmentKind)
        assertEquals("Yesterday", rows[1].lastLabel)
        assertTrue(rows.all { it.hasOpen })
    }

    @Test fun peopleSearchAndOrderingWork() {
        val s = PeopleUiState(listOf(
            PersonRowUi("b", "Bea", "Landlord", "", sortKey = 1, hasOpen = false),
            PersonRowUi("a", "Ann", "Client", "", sortKey = 3, hasOpen = true),
            PersonRowUi("c", "Cy", "Supplier", "", sortKey = 2, hasOpen = true),
        ))
        assertEquals(listOf("Ann", "Cy", "Bea"), s.visible.map { it.name })
        assertEquals(listOf("Ann", "Bea", "Cy"), s.copy(sort = PeopleSort.AtoZ).visible.map { it.name })
        assertEquals(listOf("Ann", "Cy"), s.copy(sort = PeopleSort.Open).visible.map { it.name })
        assertEquals(listOf("Bea"), s.copy(query = "land").visible.map { it.name })
        assertTrue(s.copy(query = "zzz").visible.isEmpty())
    }

    @Test fun personPageSplitsCommitmentsAndLinksTasks() {
        val person = LifePerson("nomsa mahlangu", "Nomsa Mahlangu", "Client", "n@x.example", "+27 31 555 0142", now, 2, null)
        val owe = proposal("p1", LifeKind.COMMITMENT, "Send the lease", person = "Nomsa Mahlangu", due = at(7, 16), status = ProposalStatus.KEPT)
        val wait = proposal("p6", LifeKind.AWAITING, "Her reply", person = "Nomsa Mahlangu", status = ProposalStatus.KEPT)
        val tasks = mapOf("p:p1" to LifeTask("p:p1", "memory", null, "Send the lease", at(7, 16), "open", true, null, null))
        val s = mapPerson(PersonDetail(person, listOf(owe), listOf(wait), listOf(owe, wait)), tasks, ctx)
        assertTrue(s.found)
        assertEquals(0, s.youOwe)
        assertEquals(1, s.waiting)
        assertEquals("p:p1", s.commitments[0].taskId)
        assertTrue(s.commitments[0].done)
        assertEquals(CommitmentKind.Waiting, s.commitments[1].kind)
        assertNull(s.commitments[1].taskId)
        assertEquals("Sat 7 Nov, 16:00", s.commitments[0].dateLabel)
        assertEquals("n@x.example", s.email)
        assertEquals(2, s.memories.size)
    }

    @Test fun missingPersonIsNotFound() {
        assertFalse(mapPerson(PersonDetail(null, emptyList(), emptyList(), emptyList()), emptyMap(), ctx).found)
    }

    // ---- Daily report ----

    @Test fun reportGroupsAndLabelsEverySection() {
        val aware = proposal("a1", LifeKind.MONEY, "INV-2043, R8,400", person = "Anele Jacobs", due = at(13, 9), status = ProposalStatus.KEPT)
        val report = DailyReport(
            date = at(6, 0), collectedAt = at(6, 17, 4),
            dueToday = listOf(ReportItem("t1", "Chase Jaco", "List", at(6, 17), true, "clickup", null)),
            overdue = listOf(ReportItem("t4", "Book flights", null, at(4, 12), true, "clickup", null)),
            aware = listOf(ReportItem("a1", "INV-2043, R8,400", "why", at(13, 9), false, "gmail", "https://example.com/a1")),
            meetings = listOf(
                LifeMeeting("e1", "Call with Kagiso", at(6, 15, 30), at(6, 16), null, null),
                LifeMeeting("e2", "Studio standup", at(9, 9), at(9, 9, 30), "Google Meet", "https://meet.example/x"),
            ),
            open = listOf(ReportItem("t1", "Chase Jaco", null, at(6, 17), true, "clickup", null)),
            toReview = 8,
        )
        val s = mapReport(report, listOf(aware), true, ctx)
        assertEquals("Friday 6 November", s.dateLabel)
        assertEquals("17:04", s.collectedLabel)
        assertEquals(AwareKind.Money, s.aware[0].kind)
        assertEquals("Fri 13 Nov, 09:00", s.aware[0].sub)
        assertEquals("today 17:00", s.dueToday[0].whenLabel)
        assertEquals(LifeAppId.CLICKUP, s.dueToday[0].app)
        assertEquals("2 days late", s.overdue[0].whenLabel)
        assertTrue(s.overdue[0].overdue)
        assertTrue(s.meetings[0].ended)
        assertEquals("Ended 16:00", s.meetings[0].sub)
        assertFalse(s.meetings[1].ended)
        assertEquals("Mon 9 Nov, Google Meet", s.meetings[1].sub)
        assertEquals(8, s.toReview)
        assertFalse(s.isEmpty)
    }

    @Test fun emptyReportIsEmpty() {
        val r = DailyReport(at(6, 0), null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        val s = mapReport(r, emptyList(), false, ctx)
        assertTrue(s.isEmpty)
        assertNull(s.collectedLabel)
        assertFalse(s.anyConnected)
    }

    @Test fun snoozeChoicesBecomeRealTimes() {
        assertEquals(now + 3_600_000L, snoozeUntil(SnoozeChoice.OneHour, ctx))
        assertEquals(at(7, 8, 0), snoozeUntil(SnoozeChoice.Tomorrow, ctx))
    }

    // ---- Chat sources ----

    @Test fun onlyCitedSourcesAreShown() {
        val cited = citedSources("Pay INV-2043 [2]. Lease [1].", LifeFixtures.chatSources)
        assertEquals(listOf(1, 2), cited.map { it.number })
        assertTrue(citedSources("No numbers here", LifeFixtures.chatSources).isEmpty())
    }

    // ---- Review decisions: undo window ----

    @Test fun lastDecisionWaitsForUndoThenIsWritten() = runBlocking<Unit> {
        val written = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deck = DeckSession(scope, 150) { id, keep -> written += id to keep }
        deck.decide("a", true)
        assertEquals(DeckSession.Decision("a", true), deck.pending.value)
        assertTrue("nothing written inside the undo window", written.isEmpty())
        deck.decide("b", false) // the next decision writes the one before it
        kotlinx.coroutines.delay(60)
        assertEquals(listOf("a" to true), written.toList())
        deck.undo() // undo takes b back: it is never written
        assertNull(deck.pending.value)
        kotlinx.coroutines.delay(300)
        assertEquals(listOf("a" to true), written.toList())
        deck.decide("c", true)
        kotlinx.coroutines.delay(400)
        assertEquals(listOf("a" to true, "c" to true), written.toList())
        assertEquals(2, deck.committedTotal.value)
        assertEquals(2, deck.committedKept.value)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun approveAllWritesEveryPickOnce() = runBlocking<Unit> {
        val written = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deck = DeckSession(scope, 10_000) { id, keep -> written += id to keep }
        deck.decide("a", true)
        deck.approve(listOf("b" to true, "c" to false))
        kotlinx.coroutines.delay(200)
        assertEquals(setOf("a" to true, "b" to true, "c" to false), written.toSet())
        assertEquals(3, deck.committedTotal.value)
        assertEquals(2, deck.committedKept.value)
        assertNotNull(deck)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
