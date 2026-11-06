package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.collect.LearningBias
import com.example.memgallery.lifeagent.collect.NoiseFilter
import com.example.memgallery.lifeagent.data.LifeLearningEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseFilterTest {
    private val f = NoiseFilter()
    private fun reason(i: com.example.memgallery.lifeagent.composio.RawItem) = f.judge(i).let { if (it.drop) it.reason else null }

    @Test fun dropsThePromotionsAndReceiptsFromTheSampleStory() {
        assertEquals(NoiseFilter.R_PROMO, reason(Fixtures.mail("1", "Takealot Black Friday, up to 60% off", "Shop now", from = "deals@takealot.example")))
        assertEquals(NoiseFilter.R_RECEIPT, reason(Fixtures.mail("2", "Your Vodacom bill is ready", "View your bill", from = "billing@vodacom.example")))
        assertEquals(NoiseFilter.R_RECEIPT, reason(Fixtures.mail("3", "Your Uber trip receipt R86", "Thanks for riding", from = "uber@uber.example")))
        assertEquals(NoiseFilter.R_SOCIAL, reason(Fixtures.mail("4", "You have 3 new followers", "", from = "no-reply@mail.instagram.com")))
    }

    @Test fun dropsNewslettersByLabelSenderOrUnsubscribeFooter() {
        assertEquals(NoiseFilter.R_PROMO, reason(Fixtures.mail("5", "Weekly picks", "x", labels = listOf("CATEGORY_PROMOTIONS"))))
        assertEquals(NoiseFilter.R_SOCIAL, reason(Fixtures.mail("6", "Ping", "x", labels = listOf("CATEGORY_SOCIAL"))))
        assertEquals(NoiseFilter.R_NEWSLETTER, reason(Fixtures.mail("7", "Design digest", "Read more. Unsubscribe here.", from = "news@designweekly.example")))
        assertEquals(NoiseFilter.R_NEWSLETTER, reason(Fixtures.mail("8", "Ideas", "Great reads. Manage your preferences.", from = "Someone <s@x.example>")))
    }

    @Test fun neverDropsSecurityAlertsEvenFromNoReply() {
        assertFalse(f.judge(Fixtures.mail("9", "Security alert: new sign-in to your account", "Chrome on Windows", from = "no-reply@accounts.example")).drop)
    }

    @Test fun keepsAnythingWithSomethingToDoEvenIfItLooksAutomated() {
        assertFalse(f.judge(Fixtures.mail("10", "Invoice INV-2043 is due by Fri 13 Nov", "Amount due R8,400. Unsubscribe", from = "billing@kloof.example")).drop)
        assertFalse(f.judge(Fixtures.mail("11", "Please sign the lease", "By Saturday", labels = listOf("CATEGORY_PROMOTIONS"))).drop)
        assertFalse(f.judge(Fixtures.mail("12", "Question about the deposit", "Can you reply by tomorrow?")).drop)
    }

    @Test fun dropsBotChatterAndCancelledEvents() {
        assertEquals(NoiseFilter.R_AUTOMATED, reason(Fixtures.slack("1", "build passed", bot = true)))
        assertFalse(f.judge(Fixtures.slack("2", "I will send the set today")).drop)
        assertEquals(NoiseFilter.R_CANCELLED, reason(Fixtures.event("e", "Standup", Fixtures.NOW + 1000).copy(status = "cancelled")))
        assertFalse(f.judge(Fixtures.task("t", "Anything", null)).drop)
    }

    private fun learning(app: String, key: String, decision: String, n: Int, kind: String = "other") =
        (1..n).map { LifeLearningEntity(app = app, senderKey = key, kind = kind, decision = decision, decidedAt = it.toLong()) }

    @Test fun aSenderDroppedThreeTimesIsProposedLessButOnlyTwoDropsIsNot() {
        val mail = Fixtures.mail("20", "Studio notes for the week", "Some notes", from = "Thandeka <thandeka@studio.example>")
        assertFalse(NoiseFilter(LearningBias(learning("gmail", "thandeka@studio.example", "drop", 2))).judge(mail).drop)
        val v = NoiseFilter(LearningBias(learning("gmail", "thandeka@studio.example", "drop", 3))).judge(mail)
        assertTrue(v.drop); assertEquals(NoiseFilter.R_LEARNED_SENDER, v.reason)
    }

    @Test fun keepsOutweighingDropsCancelsTheLearning() {
        val rows = learning("gmail", "a@x.example", "drop", 3) + learning("gmail", "a@x.example", "keep", 4)
        assertFalse(NoiseFilter(LearningBias(rows)).judge(Fixtures.mail("21", "Hello", "hi", from = "A <a@x.example>")).drop)
    }

    @Test fun aLearnedSenderStillGetsThroughWithARealRequest() {
        val bias = LearningBias(learning("gmail", "a@x.example", "drop", 5))
        assertFalse(NoiseFilter(bias).judge(Fixtures.mail("22", "Please send the file", "Due Mon 9 Nov", from = "A <a@x.example>")).drop)
    }

    @Test fun kindPenaltyAppliesAfterThreeDropsOfThatKind() {
        assertEquals(0f, LearningBias(learning("gmail", "s", "drop", 2, "document")).kindPenalty("document"), 0f)
        assertEquals(0.25f, LearningBias(learning("gmail", "s", "drop", 3, "document")).kindPenalty("document"), 0f)
        assertEquals(0f, LearningBias(learning("gmail", "s", "drop", 3, "document")).kindPenalty("money"), 0f)
    }
}
