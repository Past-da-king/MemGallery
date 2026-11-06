package com.example.memgallery.lifeagent.collect

import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.data.LifeLearningEntity
import com.example.memgallery.lifeagent.data.SenderKey

/** The verdict on one pulled item: keep going, or drop with a one-line reason the user can read. */
data class NoiseVerdict(val drop: Boolean, val reason: String?) {
    companion object {
        val KEEP = NoiseVerdict(false, null)
        fun drop(reason: String) = NoiseVerdict(true, reason)
    }
}

/** What the user's own keep and drop decisions say, built from life_learning. A sender or kind dropped 3 times is proposed less. */
class LearningBias(rows: List<LifeLearningEntity>) {
    private val senderDrops = HashMap<String, Int>()
    private val senderKeeps = HashMap<String, Int>()
    private val kindDrops = HashMap<String, Int>()
    private val kindKeeps = HashMap<String, Int>()

    init {
        for (r in rows) {
            val drop = r.decision == "drop"
            if (r.senderKey.isNotBlank()) {
                val k = "${r.app}|${r.senderKey}"
                (if (drop) senderDrops else senderKeeps).merge(k, 1, Int::plus)
            }
            (if (drop) kindDrops else kindKeeps).merge(r.kind, 1, Int::plus)
        }
    }

    /** True when this sender has been dropped at least [THRESHOLD] times and kept less often than that. */
    fun senderDropped(app: String, senderKey: String): Boolean {
        if (senderKey.isBlank()) return false
        val k = "$app|$senderKey"
        val d = senderDrops[k] ?: 0
        return d >= THRESHOLD && (senderKeeps[k] ?: 0) < d
    }

    /** How much to lower the confidence of a proposal of this kind. */
    fun kindPenalty(kind: String): Float {
        val d = kindDrops[kind] ?: 0
        return if (d >= THRESHOLD && (kindKeeps[kind] ?: 0) < d) 0.25f else 0f
    }

    companion object {
        const val THRESHOLD = 3
        val NONE = LearningBias(emptyList())
    }
}

/**
 * First pass over everything pulled: promotions, newsletters, social pings, receipts with nothing to do, bots.
 * Pure rules, no network, no AI. Every drop has a reason. Security alerts and anything with something to do
 * (a due date, an amount owed, a request) are never dropped by a rule.
 */
class NoiseFilter(private val bias: LearningBias = LearningBias.NONE) {

    fun judge(item: RawItem): NoiseVerdict {
        when (item.type) {
            RawType.EVENT -> return if (item.status.equals("cancelled", true)) NoiseVerdict.drop(R_CANCELLED) else NoiseVerdict.KEEP
            RawType.TASK -> return NoiseVerdict.KEEP
            RawType.MESSAGE -> if (item.isBot) return NoiseVerdict.drop(R_AUTOMATED)
            RawType.EMAIL -> {}
        }
        val text = "${item.title}\n${item.body}"
        val lower = text.lowercase()
        val sender = (item.author ?: "").lowercase()
        if (SECURITY.containsMatchIn(lower)) return NoiseVerdict.KEEP

        if (bias.senderDropped(item.app, SenderKey.of(item.author)) && !hasAction(lower)) return NoiseVerdict.drop(R_LEARNED_SENDER)

        val labels = item.labels.map { it.uppercase() }
        if ("SPAM" in labels || "TRASH" in labels) return NoiseVerdict.drop(R_PROMO)
        if ("CATEGORY_PROMOTIONS" in labels && !hasAction(lower)) return NoiseVerdict.drop(R_PROMO)
        if ("CATEGORY_SOCIAL" in labels) return NoiseVerdict.drop(R_SOCIAL)
        if (SOCIAL_SENDER.containsMatchIn(sender) || SOCIAL_SUBJECT.containsMatchIn(lower)) return NoiseVerdict.drop(R_SOCIAL)
        if (PROMO.containsMatchIn(item.title.lowercase()) && !hasAction(lower)) return NoiseVerdict.drop(R_PROMO)
        if (RECEIPT.containsMatchIn(item.title.lowercase()) && !hasAction(lower)) return NoiseVerdict.drop(R_RECEIPT)
        if ((NEWSLETTER_SENDER.containsMatchIn(sender) || NEWSLETTER_BODY.containsMatchIn(lower) || "CATEGORY_FORUMS" in labels) && !hasAction(lower)) {
            return NoiseVerdict.drop(R_NEWSLETTER)
        }
        if (AUTOMATED_SENDER.containsMatchIn(sender) && !hasAction(lower)) return NoiseVerdict.drop(R_AUTOMATED)
        if (item.type == RawType.MESSAGE && item.body.trim().length < 3) return NoiseVerdict.drop(R_NOTHING)
        return NoiseVerdict.KEEP
    }

    /** Something to do: a request, a due date or an amount still owed. Stops a rule dropping a real task. */
    private fun hasAction(lower: String): Boolean =
        ACTION.containsMatchIn(lower)

    companion object {
        const val R_PROMO = "promotion"
        const val R_NEWSLETTER = "newsletter"
        const val R_SOCIAL = "social notification"
        const val R_RECEIPT = "receipt with nothing to do"
        const val R_AUTOMATED = "automated message"
        const val R_CANCELLED = "cancelled event"
        const val R_NOTHING = "nothing to act on"
        const val R_DUPLICATE = "duplicate"
        const val R_KNOWN = "already known"
        const val R_LEARNED_SENDER = "you usually drop this sender"
        const val R_LEARNED_KIND = "you usually drop this kind"

        internal val SECURITY = Regex("(new sign-?in|security alert|suspicious (activity|sign|login)|unusual (activity|sign)|verify (it'?s|that it'?s) you|password (was )?(changed|reset)|login attempt|someone (tried|signed))")
        private val ACTION = Regex("(\\bdue\\b|overdue|\\bdeadline\\b|\\bby (mon|tue|wed|thu|fri|sat|sun|tomorrow|today|end of)|please (send|sign|confirm|review|reply|pay|approve|share)|can you|could you|need you to|kindly|action required|payment (is )?(due|overdue)|\\bamount due\\b|\\bpay by\\b|\\bremind)")
        private val PROMO = Regex("(\\d{1,3}% off|up to \\d{1,3}%|black friday|cyber monday|\\bsale\\b|limited time|exclusive (offer|deal)|\\bvoucher\\b|\\bdiscount\\b|free delivery|\\bdeals?\\b|just for you|don'?t miss)")
        private val RECEIPT = Regex("(\\breceipt\\b|bill is ready|statement is ready|trip with|order confirmation|your order|payment (received|successful|confirmation)|thank you for (your )?(order|purchase|payment)|tax invoice for your)")
        private val SOCIAL_SENDER = Regex("(facebookmail|@instagram|mail\\.instagram|linkedin\\.com|twitter\\.com|@x\\.com|tiktok|pinterest|snapchat|@quora)")
        private val SOCIAL_SUBJECT = Regex("(new followers?|liked your|commented on your|tagged you|connection request|viewed your profile|mentioned you in a post|people you may know)")
        private val NEWSLETTER_SENDER = Regex("(newsletter|news@|digest|mailer|updates@|marketing@|hello@mail|@mail\\.|info@news)")
        private val NEWSLETTER_BODY = Regex("(unsubscribe|view (this email )?in (your )?browser|manage (your )?(email )?preferences)")
        private val AUTOMATED_SENDER = Regex("(no-?reply|do-?not-?reply|donotreply|notifications?@|alerts?@|mailer-daemon)")
    }
}
