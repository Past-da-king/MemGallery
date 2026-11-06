package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.data.LifeTaskEntity
import com.example.memgallery.lifeagent.work.LifeAgentSchedule
import com.example.memgallery.lifeagent.work.NudgePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeAndScheduleTest {
    private fun task(id: String, due: Long?, status: String = "open", done: Boolean = false, snooze: Long? = null) =
        LifeTaskEntity(id, "clickup", null, "Title $id", due, status, done, snooze, null)
    private val now = Fixtures.NOW
    private fun plan(vararg t: LifeTaskEntity, sent: Set<String> = emptySet()) = NudgePlanner.plan(t.toList(), now, sent)

    @Test fun dueSoonAndOverdueGetOneNudgeEachAndFarOrDoneOrClosedGetNone() {
        val n = plan(
            task("soon", now + 5 * 3_600_000L), task("over", now - 2 * Fixtures.DAY), task("far", now + 3 * Fixtures.DAY),
            task("done", now - 1000, done = true), task("closed", now - 1000, status = "Complete"), task("nodate", null),
        )
        assertEquals(listOf("over", "soon"), n.map { it.taskId })
        assertTrue(n.first { it.taskId == "over" }.overdue); assertTrue(!n.first { it.taskId == "soon" }.overdue)
    }

    @Test fun theSameStateIsNeverNudgedTwice() {
        val t = task("soon", now + 3_600_000L)
        val first = plan(t)
        assertEquals(1, first.size)
        assertEquals(0, plan(t, sent = setOf(first.single().key)).size)
    }

    @Test fun dueSoonThenOverdueAreTwoDifferentNudges() {
        val due = now + 3_600_000L
        val k1 = NudgePlanner.plan(listOf(task("x", due)), now, emptySet()).single().key
        val k2 = NudgePlanner.plan(listOf(task("x", due)), due + 1000, setOf(k1)).single()
        assertTrue(k2.overdue); assertTrue(k1 != k2.key)
    }

    @Test fun snoozedItemsWaitAndThenEarnOneMoreNudge() {
        val t = task("s", now - 1000, snooze = now + 3_600_000L)
        assertEquals(0, plan(t).size)
        val later = NudgePlanner.plan(listOf(t), now + 2 * 3_600_000L, emptySet())
        assertEquals(1, later.size)
        val sent = setOf(later.single().key)
        assertEquals(0, NudgePlanner.plan(listOf(t), now + 3 * 3_600_000L, sent).size)
        val resnoozed = t.copy(snoozedUntil = now + 4 * 3_600_000L)
        assertEquals(1, NudgePlanner.plan(listOf(resnoozed), now + 5 * 3_600_000L, sent).size)
    }

    @Test fun defaultRunIsTodayAt1700WhenItIsBeforeThenTomorrow() {
        val zone = Fixtures.ZONE
        val noon = Fixtures.NOW
        assertEquals(5 * 3_600_000L, LifeAgentSchedule.nextRunDelayMs(noon, 17, 0, zone))
        val evening = Fixtures.at(11, 6, 18)
        assertEquals(23 * 3_600_000L, LifeAgentSchedule.nextRunDelayMs(evening, 17, 0, zone))
        val exactly = Fixtures.at(11, 6, 17)
        assertEquals(24 * 3_600_000L, LifeAgentSchedule.nextRunDelayMs(exactly, 17, 0, zone))
    }

    @Test fun userTimeIsRespectedAndClamped() {
        assertEquals(30 * 60_000L, LifeAgentSchedule.nextRunDelayMs(Fixtures.at(11, 6, 6, 0), 6, 30, Fixtures.ZONE))
        assertEquals(LifeAgentSchedule.nextRunDelayMs(Fixtures.NOW, 23, 59, Fixtures.ZONE), LifeAgentSchedule.nextRunDelayMs(Fixtures.NOW, 99, 99, Fixtures.ZONE))
    }

    @Test fun slotsAlternate() {
        assertEquals(LifeAgentSchedule.SLOT_B, LifeAgentSchedule.otherSlot(LifeAgentSchedule.SLOT_A))
        assertEquals(LifeAgentSchedule.SLOT_A, LifeAgentSchedule.otherSlot(LifeAgentSchedule.SLOT_B))
    }
}
