package com.example.memgallery.lifeagent

import android.app.Application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.example.memgallery.lifeagent.data.DailyReport
import com.example.memgallery.lifeagent.data.LifeKind
import com.example.memgallery.lifeagent.data.LifeMeeting
import com.example.memgallery.lifeagent.data.LifeProposal
import com.example.memgallery.lifeagent.data.ProposalStatus
import com.example.memgallery.lifeagent.data.ReportItem
import com.example.memgallery.lifeagent.widget.LifeAgentWidgetData
import com.example.memgallery.lifeagent.widget.LifeAgentWidgetContent
import com.example.memgallery.lifeagent.widget.LifeSnapshotKey
import com.example.memgallery.lifeagent.widget.LifeWidgetAskAction
import com.example.memgallery.lifeagent.widget.LifeWidgetDoneAction
import com.example.memgallery.lifeagent.widget.LifeWidgetDropAction
import com.example.memgallery.lifeagent.widget.LifeWidgetKeepAction
import com.example.memgallery.lifeagent.widget.LifeWidgetKeys
import com.example.memgallery.lifeagent.widget.LifeWidgetRefreshAction
import com.example.memgallery.lifeagent.widget.LifeWidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** The Life Agent widget: pure data shaping and the Glance layout at 2x2, 4x2 and 4x4 (fictional SAMPLE-DATA, Fri 6 Nov 2026 17:04). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetProofTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun ms(day: Int, h: Int, m: Int = 0, month: Int = 11) =
        LocalDateTime.of(2026, month, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = ms(6, 17, 4)

    private fun item(id: String, title: String, at: Long?, task: Boolean = true) =
        ReportItem(id = id, title = title, subtitle = null, dueAt = at, isTask = task, source = "clickup", url = null)

    private val report = DailyReport(
        date = ms(6, 0), collectedAt = now,
        dueToday = listOf(item("t-jaco", "Chase Jaco for the retouched set", ms(6, 17)), item("t-standup", "Standup notes for Thandeka", ms(6, 18))),
        overdue = listOf(item("t-flights", "Book the Durban workshop flights", ms(4, 9))),
        aware = listOf(item("a1", "Nomsa lease", ms(7, 16), task = false), item("a2", "INV-2043", ms(13, 9), task = false)),
        meetings = listOf(
            LifeMeeting("m-old", "Morning sync", ms(6, 8), ms(6, 8, 30), null, null),
            LifeMeeting("m1", "Call with Kagiso about Q4", ms(6, 15, 30), ms(6, 16), "Google Meet", "https://meet.example.com/x").copy(startAt = ms(6, 18), endAt = ms(6, 18, 30)),
            LifeMeeting("m2", "Studio standup", ms(9, 9), ms(9, 9, 30), "Google Meet", null),
        ),
        open = listOf(item("t-q4", "Q4 expense sheet for Kagiso", ms(9, 12)), item("t-inv", "Approve the INV-2043 payment", ms(13, 12))),
        toReview = 7,
    )
    private val proposal = LifeProposal(
        id = "p1", kind = LifeKind.DEADLINE, title = "Nomsa is waiting on the signed lease", detail = "", app = "gmail",
        sourceId = "s1", sourceTitle = "Signed lease, before the weekend", sourceUrl = null, personName = "Nomsa Mahlangu",
        dueAt = ms(7, 16), amountCents = null, currency = null, confidence = 0.9f, status = ProposalStatus.PROPOSED,
        reason = "Names a person, a document and a date", matchedTaskId = null, collectedAt = now, decidedAt = null,
    )
    private val snapshot = LifeAgentWidgetData.build(report, listOf(proposal), now, zone, themeMode = "LIGHT", dynamic = false)

    // ---- pure data ----

    @Test fun buildCountsAndOrder() {
        assertEquals(2, snapshot.dueToday)
        assertEquals(1, snapshot.overdue)
        assertEquals(2, snapshot.aware)
        assertEquals(7, snapshot.toReview)
        assertFalse(snapshot.empty)
        assertEquals(listOf("t-flights", "t-jaco", "t-standup", "m1", "m2", "t-q4", "t-inv"), snapshot.rows.map { it.id })
        assertEquals("Fri 6 Nov".lowercase(), snapshot.dateLabel.lowercase().replace("  ", " "))
    }

    @Test fun buildDropsMeetingsThatAlreadyEnded() {
        assertTrue(snapshot.meetingItems.none { it.id == "m-old" })
        assertEquals("m1", snapshot.nextMeeting?.id)
        assertEquals("video", snapshot.nextMeeting?.icon)
    }

    @Test fun buildProposalAndEmpty() {
        assertEquals("p1", snapshot.proposal?.id)
        assertEquals("proposal", snapshot.proposal?.kind)
        assertEquals("Gmail", snapshot.proposalSource)
        val none = LifeAgentWidgetData.build(null, emptyList(), now, zone)
        assertTrue(none.empty)
        val clear = LifeAgentWidgetData.build(report.copy(dueToday = emptyList(), overdue = emptyList(), meetings = emptyList(), open = emptyList(), aware = emptyList(), toReview = 0), emptyList(), now, zone)
        assertTrue(clear.empty)
    }

    @Test fun buildCapsRows() {
        val many = report.copy(open = (1..20).map { item("o$it", "Open $it", null) })
        assertEquals(6, LifeAgentWidgetData.build(many, emptyList(), now, zone).openItems.size)
    }

    @Test fun whenLabels() {
        fun w(at: Long?) = LifeAgentWidgetData.whenLabel(at, now, zone)
        assertEquals("16:00 tomorrow", w(ms(7, 16)))
        assertEquals("18:00 today", w(ms(6, 18)))
        assertEquals("2 days late", w(ms(4, 9)))
        assertEquals("1 day late", w(ms(5, 9)))
        assertEquals("was 08:00", w(ms(6, 8)))
        assertEquals("", w(null))
        assertEquals("Fri 13 Nov", w(ms(13, 9)))
    }

    @Test fun snapshotRoundTripsThroughJson() {
        val back = LifeWidgetSnapshot.fromJson(snapshot.toJson())
        assertEquals(snapshot, back)
        assertEquals(LifeWidgetSnapshot.Start, LifeWidgetSnapshot.fromJson(null))
        assertEquals(LifeWidgetSnapshot.Start, LifeWidgetSnapshot.fromJson("not json"))
    }

    // ---- Glance layout ----

    private val counts = "2 due today \u00b7 1 late"

    private fun glance(size: DpSize, snap: LifeWidgetSnapshot = snapshot, block: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(size)
            setState(mutablePreferencesOf(LifeSnapshotKey to snap.toJson()))
            provideComposable { LifeAgentWidgetContent() }
            block()
        }

    @Test fun small2x2ShowsCountsTopTaskTickAndAsk() = glance(DpSize(150.dp, 150.dp)) {
        onNode(hasText(counts)).assertExists()
        onNode(hasText("Book the Durban workshop flights")).assertExists()
        onNode(hasText("2 days late")).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetDoneAction>(actionParametersOf(LifeWidgetKeys.taskId to "t-flights"))).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetAskAction>(actionParametersOf(
            LifeWidgetKeys.itemTitle to "Book the Durban workshop flights", LifeWidgetKeys.itemId to "t-flights"))).assertExists()
    }

    @Test fun wide4x2ShowsDateRowsMeetingRefreshAndAsk() = glance(DpSize(320.dp, 150.dp)) {
        onNode(hasText(counts)).assertExists()
        onNode(hasText("Book the Durban workshop flights")).assertExists()
        onNode(hasText("Chase Jaco for the retouched set")).assertExists()
        onNode(hasText("Standup notes for Thandeka")).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetDoneAction>(actionParametersOf(LifeWidgetKeys.taskId to "t-jaco"))).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetRefreshAction>()).assertExists()
    }

    @Test fun large4x4HasSectionsProposalKeepDropRefreshAsk() = glance(DpSize(320.dp, 320.dp)) {
        onNode(hasText(counts)).assertExists()
        for (label in listOf("LATE", "DUE TODAY", "MEETINGS", "OPEN")) onNode(hasText(label)).assertExists()
        onNode(hasText("Call with Kagiso about Q4")).assertExists()
        onNode(hasText("Nomsa is waiting on the signed lease")).assertExists()
        onNode(hasText("Keep")).assertExists()
        onNode(hasText("Drop")).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetKeepAction>(actionParametersOf(LifeWidgetKeys.proposalId to "p1"))).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetDropAction>(actionParametersOf(LifeWidgetKeys.proposalId to "p1"))).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetRefreshAction>()).assertExists()
        onNode(hasRunCallbackClickAction<LifeWidgetDoneAction>(actionParametersOf(LifeWidgetKeys.taskId to "t-flights"))).assertExists()
    }

    @Test fun emptySnapshotShowsAllClear() = glance(DpSize(150.dp, 150.dp), LifeWidgetSnapshot.Start) {
        onNode(hasText("All clear")).assertExists()
    }
}
