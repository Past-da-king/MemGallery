package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.AiRefinement
import com.example.memgallery.lifeagent.collect.BuildContext
import com.example.memgallery.lifeagent.collect.LearningBias
import com.example.memgallery.lifeagent.collect.ProposalAi
import com.example.memgallery.lifeagent.collect.ProposalBuilder
import com.example.memgallery.lifeagent.collect.ProposalDraft
import com.example.memgallery.lifeagent.collect.ProviderProposalAi
import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.data.LifeKind
import com.example.memgallery.lifeagent.data.LifeLearningEntity
import com.example.memgallery.lifeagent.data.LifeTaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalBuilderTest {
    private val ctx = BuildContext(now = Fixtures.NOW, zone = Fixtures.ZONE)
    private fun build(i: RawItem, c: BuildContext = ctx, ai: ProposalAi? = null) = runBlocking { ProposalBuilder(ai).build(i, c) }

    @Test fun leaseRequestBecomesACommitmentOwedToNomsaWithTheDate() {
        val d = build(Fixtures.mail("m1", "Signed lease", "Hi, please send the signed lease by Sat 7 Nov 16:00. Thanks, Nomsa"))!!
        assertEquals(LifeKind.COMMITMENT, d.kind)
        assertEquals("Nomsa Mahlangu", d.personName)
        assertEquals(Fixtures.at(11, 7, 16), d.dueAt)
        assertTrue(d.title, d.title.startsWith("Nomsa Mahlangu is waiting on: Signed lease"))
        assertTrue(d.reason, d.reason.startsWith("names a person") && d.reason.contains("a date"))
        assertEquals("thing:nomsa mahlangu:lease", d.fingerprint)
    }

    @Test fun invoiceBecomesMoneyWithAmountCurrencyAndDueDate() {
        val d = build(Fixtures.mail("m2", "Invoice INV-2043", "Hi, invoice INV-2043 for R8,400.00 is attached, due 13 Nov.", from = "Anele Jacobs <anele@kloof.example>"))!!
        assertEquals(LifeKind.MONEY, d.kind)
        assertEquals(840000L, d.amountCents); assertEquals("ZAR", d.currency)
        assertEquals(Fixtures.at(11, 13, 23, 59), d.dueAt)
        assertEquals("Anele Jacobs sent invoice INV-2043, R8,400, due Fri 13 Nov", d.title)
        assertEquals("thing:anele jacobs:INV-2043", d.fingerprint)
    }

    @Test fun securityAlertWinsEvenWithAnAmountAndNoPerson() {
        val d = build(Fixtures.mail("m3", "Security alert: new sign-in from Chrome on Windows", "Durban. If this wasn't you, review it.", from = "no-reply@accounts.example"))!!
        assertEquals(LifeKind.SECURITY, d.kind); assertNull(d.personName)
        assertEquals("a security alert on your account", d.reason)
    }

    @Test fun slackPromiseIsAwaitingAndNeverNamesASlackId() {
        val d = build(Fixtures.slack("1", "I will send the retouched set today", user = "U123"))!!
        assertEquals(LifeKind.AWAITING, d.kind); assertNull(d.personName)
        assertNotNull(d.dueAt)
    }

    @Test fun chatterWithNothingToActOnIsNotProposed() {
        assertNull(build(Fixtures.mail("m4", "Lunch?", "Hey, see you at the usual spot, sounds good")))
        assertNull(build(Fixtures.slack("2", "lol ok")))
    }

    @Test fun aDateAloneIsADeadlineWithLowerConfidenceThanARequest() {
        val deadline = build(Fixtures.mail("m5", "Workshop flights", "Prices go up on 11 Nov"))!!
        assertEquals(LifeKind.DEADLINE, deadline.kind)
        val ask = build(Fixtures.mail("m6", "Q4 sheet", "Can you send the Q4 expense sheet by Mon 9 Nov?"))!!
        assertTrue(ask.confidence > deadline.confidence)
    }

    @Test fun relativeDatesResolveFromTheMessageTime() {
        val d = build(Fixtures.mail("m7", "Artwork", "Could you send the final artwork by tomorrow?"))!!
        assertEquals(Fixtures.at(11, 7, 17), d.dueAt)
        val w = build(Fixtures.mail("m8", "Report", "Please confirm the numbers by Tuesday"))!!
        assertEquals(Fixtures.at(11, 10, 17), w.dueAt)
    }

    @Test fun calendarChangeBecomesAnEventAndMovedMeetingsRefingerprint() {
        val start = Fixtures.at(11, 12, 10)
        val d = build(Fixtures.event("e1", "Ndlovu & Sons pitch", start))!!
        assertEquals(LifeKind.EVENT, d.kind); assertEquals(start, d.dueAt)
        assertEquals("a meeting was changed", d.reason)
        val moved = build(Fixtures.event("e1", "Ndlovu & Sons pitch", start + 86_400_000L))!!
        assertTrue(d.fingerprint != moved.fingerprint)
        assertNull(build(Fixtures.event("past", "Old", Fixtures.NOW - 1000)))
    }

    @Test fun newClickUpTaskWithNearDeadlineBecomesADeadlineOnlyOnce() {
        val t = Fixtures.task("t1", "Q4 expense sheet for Kagiso", Fixtures.at(11, 9, 12))
        val d = build(t)!!
        assertEquals(LifeKind.DEADLINE, d.kind); assertEquals("task:t1", d.fingerprint); assertEquals("t1", d.matchedTaskId)
        assertNull(build(t, ctx.copy(knownTaskIds = setOf("t1"))))
        assertNull(build(Fixtures.task("t2", "Far away", Fixtures.NOW + 60 * Fixtures.DAY)))
        assertNull(build(Fixtures.task("t3", "Done", Fixtures.at(11, 9), status = "complete")))
        assertNull(build(Fixtures.task("t4", "No date", null)))
    }

    @Test fun aMailAboutAnExistingTaskLinksToIt() {
        val task = LifeTaskEntity("ck1", "clickup", null, "Q4 expense sheet for Kagiso", Fixtures.at(11, 9), "open", false, null, null)
        val d = build(Fixtures.mail("m9", "Q4 expense sheet", "Please send the Q4 expense sheet for Kagiso by Mon 9 Nov"), ctx.copy(openTasks = listOf(task)))!!
        assertEquals("ck1", d.matchedTaskId)
    }

    @Test fun amountsParseInEveryCommonShape() {
        val b = ProposalBuilder()
        assertEquals(840000L, b.findMoney("R8,400")!!.cents)
        assertEquals(840000L, b.findMoney("R 8 400.00")!!.cents)
        assertEquals(12550L, b.findMoney("ZAR 125.50")!!.cents)
        assertEquals("USD", b.findMoney("$1,200")!!.currency)
        assertNull(b.findMoney("your 100 points"))
        assertNull(b.findMoney("no amount here"))
    }

    @Test fun learnedKindPenaltyDropsAWeakProposalAndKeepsAStrongOne() {
        val rows = (1..3).map { LifeLearningEntity(app = "gmail", senderKey = "x", kind = "deadline", decision = "drop", decidedAt = it.toLong()) }
        val biased = ctx.copy(bias = LearningBias(rows))
        assertNull(build(Fixtures.mail("m10", "Workshop flights", "Prices go up on 11 Nov"), biased))
        assertNotNull(build(Fixtures.mail("m11", "Invoice INV-9001", "Amount due R100, due 13 Nov"), biased))
    }

    @Test fun noAiConfiguredGivesTheSameCompleteDraft() {
        val m = Fixtures.mail("m12", "Signed lease", "Please send the signed lease by Sat 7 Nov")
        assertEquals(build(m), build(m, ai = null))
    }

    @Test fun aiMayRewordTheTitleAndPickTheKindButNotTheFacts() {
        val ai = object : ProposalAi {
            override suspend fun refine(item: RawItem, draft: ProposalDraft) = AiRefinement("Send Nomsa the signed lease by Saturday 16:00", LifeKind.DOCUMENT)
        }
        val d = build(Fixtures.mail("m13", "Signed lease", "Please send the signed lease by Sat 7 Nov 16:00"), ai = ai)!!
        assertEquals("Send Nomsa the signed lease by Saturday 16:00", d.title); assertEquals(LifeKind.DOCUMENT, d.kind)
        assertEquals(Fixtures.at(11, 7, 16), d.dueAt)
    }

    @Test fun aFailingOrNonsenseAiNeverBreaksTheDraft() {
        val boom = object : ProposalAi { override suspend fun refine(item: RawItem, draft: ProposalDraft): AiRefinement? = throw IllegalStateException("down") }
        val m = Fixtures.mail("m14", "Signed lease", "Please send the signed lease by Sat 7 Nov")
        assertEquals(LifeKind.COMMITMENT, build(m, ai = boom)!!.kind)
        val junk = object : ProposalAi { override suspend fun refine(item: RawItem, draft: ProposalDraft) = AiRefinement("x", null) }
        assertEquals(build(m)!!.title, build(m, ai = junk)!!.title)
    }

    @Test fun aiAnswerParsingIsTolerant() {
        val r = ProviderProposalAi.parse("Sure! {\"kind\":\"Money\",\"sentence\":\"Anele sent INV-2043 for R8,400\"} hope that helps")!!
        assertEquals(LifeKind.MONEY, r.kind); assertEquals("Anele sent INV-2043 for R8,400", r.title)
        assertNull(ProviderProposalAi.parse("no json here"))
        assertNull(ProviderProposalAi.parse("{\"kind\":\"nonsense\"}"))
    }
}
