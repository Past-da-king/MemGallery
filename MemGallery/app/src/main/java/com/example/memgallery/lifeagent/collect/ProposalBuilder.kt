package com.example.memgallery.lifeagent.collect

import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.data.LifeKind
import com.example.memgallery.lifeagent.data.LifeTaskEntity
import com.example.memgallery.lifeagent.data.SenderKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the builder hands the runner. Becomes a life_proposals row once the fingerprint is checked. */
data class ProposalDraft(
    val kind: LifeKind,
    val title: String,
    val detail: String,
    val app: String,
    val sourceId: String,
    val sourceTitle: String,
    val sourceUrl: String?,
    val personName: String?,
    val dueAt: Long?,
    val amountCents: Long?,
    val currency: String?,
    val confidence: Float,
    val reason: String,
    val fingerprint: String,
    val matchedTaskId: String?,
)

/** What the builder knows about the user's world while it works. */
data class BuildContext(
    val now: Long,
    val zone: ZoneId = ZoneId.systemDefault(),
    val openTasks: List<LifeTaskEntity> = emptyList(),
    val knownTaskIds: Set<String> = emptyList<String>().toSet(),
    val bias: LearningBias = LearningBias.NONE,
)

/** Optional AI polish for one sentence and the kind. Never required: the rule-based draft is always complete. */
interface ProposalAi {
    /** Returns a better title and kind, or null when no provider is set up or the answer is unusable. Never throws. */
    suspend fun refine(item: RawItem, draft: ProposalDraft): AiRefinement?
}

data class AiRefinement(val title: String?, val kind: LifeKind?)

/**
 * Turns what survived the noise filter into a proposal, or null when there is nothing to act on.
 * The rules decide; [ai] may only reword the title and re-pick the kind. With no AI configured the feature is the same.
 */
class ProposalBuilder(private val ai: ProposalAi? = null) {

    suspend fun build(item: RawItem, ctx: BuildContext): ProposalDraft? {
        val base = when (item.type) {
            RawType.EVENT -> fromEvent(item, ctx)
            RawType.TASK -> fromTask(item, ctx)
            RawType.EMAIL, RawType.MESSAGE -> fromText(item, ctx)
        } ?: return null
        var draft = applyBias(base, ctx)
        if (draft.confidence < MIN_CONFIDENCE) return null
        val refined = try { ai?.refine(item, draft) } catch (e: Exception) { null }
        if (refined != null) {
            draft = draft.copy(
                title = refined.title?.trim()?.takeIf { it.length in 6..200 } ?: draft.title,
                kind = refined.kind ?: draft.kind,
            )
        }
        return draft
    }

    private fun applyBias(d: ProposalDraft, ctx: BuildContext): ProposalDraft {
        val penalty = ctx.bias.kindPenalty(d.kind.key)
        if (penalty == 0f) return d
        return d.copy(confidence = d.confidence - penalty, reason = d.reason + "; you often drop ${d.kind.label.lowercase()} items")
    }

    // ---- calendar ----

    private fun fromEvent(item: RawItem, ctx: BuildContext): ProposalDraft? {
        val start = item.startAt ?: return null
        if (start < ctx.now) return null
        val updated = item.updatedAt ?: item.occurredAt
        // The caller passes only events changed since the last pull; a plain unchanged event is not a memory.
        return ProposalDraft(
            kind = LifeKind.EVENT,
            title = "${item.title} on ${fmt(start, ctx.zone)}",
            detail = listOfNotNull(item.location, item.body.takeIf { it.isNotBlank() }).joinToString(". ").take(240),
            app = item.app, sourceId = item.sourceId, sourceTitle = item.title, sourceUrl = item.url,
            personName = null, dueAt = start, amountCents = null, currency = null,
            confidence = if (updated > item.occurredAt + 60_000) 0.75f else 0.6f,
            reason = if (updated > item.occurredAt + 60_000) "a meeting was changed" else "a meeting was booked",
            fingerprint = "event:${item.sourceId}:$start", matchedTaskId = null,
        )
    }

    // ---- ClickUp ----

    private fun fromTask(item: RawItem, ctx: BuildContext): ProposalDraft? {
        if (item.id in ctx.knownTaskIds) return null
        val due = item.dueAt ?: return null
        if (item.status != null && item.status.lowercase() in CLOSED) return null
        if (due > ctx.now + 21 * DAY) return null
        return ProposalDraft(
            kind = LifeKind.DEADLINE,
            title = "${item.title}, due ${fmt(due, ctx.zone)}",
            detail = listOfNotNull(item.listName, item.body.takeIf { it.isNotBlank() }).joinToString(". ").take(240),
            app = item.app, sourceId = item.sourceId, sourceTitle = item.title, sourceUrl = item.url,
            personName = null, dueAt = due, amountCents = null, currency = null,
            confidence = 0.65f, reason = "a task with a deadline",
            fingerprint = "task:${item.sourceId}", matchedTaskId = item.sourceId,
        )
    }

    private val RawItem.id: String get() = sourceId

    // ---- mail and chat ----

    private fun fromText(item: RawItem, ctx: BuildContext): ProposalDraft? {
        val text = "${item.title}\n${item.body}"
        val lower = text.lowercase()
        val person = personOf(item)
        val security = NoiseFilter.SECURITY.containsMatchIn(lower)
        val money = findMoney(text)
        val ref = REF.find(text)?.value?.uppercase()?.replace(Regex("[\\s#]+"), "-")
        val due = findDue(text, item.occurredAt.takeIf { it > 0 } ?: ctx.now, ctx.zone)
        val ask = ASK.containsMatchIn(lower)
        val awaiting = AWAITING.containsMatchIn(lower)
        val decision = DECISION.containsMatchIn(lower)
        val meeting = MEETING.containsMatchIn(lower) && due != null
        val doc = DOCUMENT.find(lower)?.value
        val invoice = INVOICE.containsMatchIn(lower)

        // The strongest signal decides the kind.
        data class Cand(val kind: LifeKind, val score: Float)
        val cands = ArrayList<Cand>()
        if (security) cands += Cand(LifeKind.SECURITY, 0.9f)
        if (money != null && (invoice || due != null || ask)) cands += Cand(LifeKind.MONEY, 0.7f + if (due != null) 0.1f else 0f)
        if (ask) cands += Cand(LifeKind.COMMITMENT, 0.6f + if (due != null) 0.15f else 0f)
        if (awaiting) cands += Cand(LifeKind.AWAITING, 0.6f + if (due != null) 0.1f else 0f)
        if (meeting) cands += Cand(LifeKind.EVENT, 0.6f)
        if (decision) cands += Cand(LifeKind.DECISION, 0.5f)
        if (doc != null && (ask || due != null)) cands += Cand(LifeKind.DOCUMENT, 0.5f)
        if (due != null && cands.none { it.kind == LifeKind.COMMITMENT }) cands += Cand(LifeKind.DEADLINE, 0.45f + if (person != null) 0.1f else 0f)
        val best = cands.maxByOrNull { it.score } ?: return null
        val boost = (if (person != null) 0.05f else 0f) + (if (cands.size > 1) 0.05f else 0f)
        val confidence = (best.score + boost).coerceAtMost(0.95f)

        val who = person ?: "Someone"
        val title = when (best.kind) {
            LifeKind.SECURITY -> item.title.trim().take(160)
            LifeKind.MONEY -> buildString {
                append(who).append(if (invoice) " sent " else " asked for ")
                append(ref?.let { "invoice $it" } ?: "a payment")
                money?.let { append(", ").append(it.display) }
                due?.let { append(", due ").append(fmt(it, ctx.zone)) }
            }
            LifeKind.COMMITMENT -> buildString {
                append(who).append(" is waiting on: ").append(cleanSubject(item.title))
                due?.let { append(", due ").append(fmt(it, ctx.zone)) }
            }
            LifeKind.AWAITING -> buildString {
                append(who).append(" promised: ").append(cleanSubject(item.title))
                due?.let { append(", by ").append(fmt(it, ctx.zone)) }
            }
            LifeKind.EVENT -> "${cleanSubject(item.title)}, ${due?.let { fmt(it, ctx.zone) } ?: "date to confirm"}"
            else -> cleanSubject(item.title) + (due?.let { ", due ${fmt(it, ctx.zone)}" } ?: "")
        }

        val parts = ArrayList<String>()
        if (person != null) parts += "a person"
        if (doc != null) parts += "a document"
        if (money != null) parts += "an amount"
        if (due != null) parts += "a date"
        if (ask) parts += "a request"
        val reason = when (best.kind) {
            LifeKind.SECURITY -> "a security alert on your account"
            LifeKind.MONEY -> "money with ${if (due != null) "a date" else "an amount"}"
            else -> if (parts.isEmpty()) "worth remembering" else "names " + joinNice(parts)
        }

        // Same person plus same thing (an invoice number, a lease) is one memory, however many mails mention it.
        val thing = ref ?: THING.find(lower)?.value
        val fp = if (thing != null && person != null) "thing:${SenderKey.personId(person)}:$thing" else "src:${item.app}:${item.sourceId}"

        return ProposalDraft(
            kind = best.kind, title = title, detail = snippet(item.body), app = item.app, sourceId = item.sourceId,
            sourceTitle = item.title.take(120), sourceUrl = item.url, personName = person, dueAt = due,
            amountCents = money?.cents, currency = money?.currency, confidence = confidence, reason = reason,
            fingerprint = fp, matchedTaskId = matchTask(text, ref, ctx),
        )
    }

    private fun personOf(item: RawItem): String? {
        val a = item.author ?: return null
        val lowerA = a.lowercase()
        if (Regex("(no-?reply|do-?not-?reply|notifications?@|alerts?@|support@|billing@|info@|accounts?@)").containsMatchIn(lowerA)) return null
        if (item.app == "slack") return null // Slack authors arrive as ids, not names
        return SenderKey.displayNameOf(a)
    }

    private fun matchTask(text: String, ref: String?, ctx: BuildContext): String? {
        if (ctx.openTasks.isEmpty()) return null
        val words = tokens(text)
        var best: LifeTaskEntity? = null
        var bestScore = 0.0
        for (t in ctx.openTasks) {
            if (ref != null && t.title.uppercase().replace(Regex("[\\s#]+"), "-").contains(ref)) return t.id
            val tw = tokens(t.title)
            if (tw.size < 2) continue
            val shared = tw.count { it in words }.toDouble() / tw.size
            if (shared > bestScore) { bestScore = shared; best = t }
        }
        return if (bestScore >= 0.6) best?.id else null
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 && it !in STOP }.toSet()

    private fun cleanSubject(s: String): String =
        s.trim().replace(Regex("^(re|fw|fwd):\\s*", RegexOption.IGNORE_CASE), "").take(140)

    private fun snippet(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(200)

    private fun joinNice(parts: List<String>): String = when (parts.size) {
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    // ---- extraction helpers ----

    internal data class Money(val cents: Long, val currency: String, val display: String)

    internal fun findMoney(text: String): Money? {
        val m = MONEY.find(text) ?: return null
        val symbol = m.groupValues[1].uppercase()
        val raw = m.groupValues[2].replace(" ", "").trimEnd('.', ',')
        // "8,400.00" and "8 400" and "8.400,50": last separator with 1 or 2 digits after is the decimal.
        val dec = Regex("[.,](\\d{1,2})$").find(raw)
        val whole = (if (dec != null) raw.dropLast(dec.value.length) else raw).replace(".", "").replace(",", "")
        val cents = (whole.toLongOrNull() ?: return null) * 100 + (dec?.groupValues?.get(1)?.padEnd(2, '0')?.toLong() ?: 0L)
        if (cents <= 0) return null
        val cur = when (symbol) { "R", "ZAR" -> "ZAR"; "$", "USD" -> "USD"; "€", "EUR" -> "EUR"; "£", "GBP" -> "GBP"; else -> symbol }
        val shown = when (cur) { "ZAR" -> "R"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "$cur " } + String.format(Locale.US, "%,d", cents / 100) + if (cents % 100 != 0L) String.format(Locale.US, ".%02d", cents % 100) else ""
        return Money(cents, cur, shown)
    }

    /** Finds a due date in free text: "by Mon 9 Nov", "Sat 7 Nov 16:00", "due 13 Nov", "tomorrow", "by Friday". */
    internal fun findDue(text: String, anchor: Long, zone: ZoneId): Long? {
        val lower = text.lowercase()
        val base = Instant.ofEpochMilli(anchor).atZone(zone).toLocalDate()
        DATE.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = MONTHS[m.groupValues[2].take(3)] ?: return@let
            val time = m.groupValues[3].takeIf { it.isNotEmpty() }
            val h = time?.substringBefore(':')?.toIntOrNull() ?: 23
            val min = time?.substringAfter(':', "59")?.toIntOrNull() ?: 59
            var year = base.year
            var d = try { LocalDate.of(year, month, day) } catch (e: Exception) { return@let }
            if (d.isBefore(base.minusDays(30))) d = d.plusYears(1)
            return LocalDateTime.of(d, java.time.LocalTime.of(h.coerceIn(0, 23), min.coerceIn(0, 59))).atZone(zone).toInstant().toEpochMilli()
        }
        ISO.find(lower)?.let { m ->
            return try { LocalDate.parse(m.value).atTime(23, 59).atZone(zone).toInstant().toEpochMilli() } catch (e: Exception) { null }
        }
        if (Regex("\\b(tomorrow)\\b").containsMatchIn(lower)) return base.plusDays(1).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        if (Regex("\\b(today|tonight|end of day|eod)\\b").containsMatchIn(lower)) return base.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        WEEKDAY.find(lower)?.let { m ->
            val dow = WEEKDAYS[m.groupValues[1].take(3)] ?: return null
            var d = base.plusDays(1)
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            return d.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    private fun fmt(at: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(Instant.ofEpochMilli(at).atZone(zone)) +
            Instant.ofEpochMilli(at).atZone(zone).let { z -> if (z.hour == 23 && z.minute == 59) "" else DateTimeFormatter.ofPattern(" HH:mm").format(z) }

    companion object {
        const val MIN_CONFIDENCE = 0.45f
        private const val DAY = 24L * 60 * 60 * 1000
        private val CLOSED = setOf("complete", "completed", "closed", "done")
        private val STOP = setOf("with", "from", "that", "this", "have", "your", "send", "please", "about", "will", "need")

        private val MONTH_RX = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
        private val DATE = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+$MONTH_RX\\b(?:\\s+\\d{4})?(?:\\s+(?:at\\s+)?(\\d{1,2}:\\d{2}))?")
        private val ISO = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
        private val WEEKDAY = Regex("\\b(?:by|before|on|this|next|due)\\s+(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\b")
        private val MONTHS = mapOf(
            "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH, "apr" to Month.APRIL, "may" to Month.MAY,
            "jun" to Month.JUNE, "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER, "oct" to Month.OCTOBER,
            "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER,
        )
        private val WEEKDAYS = mapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
        )

        private val MONEY = Regex("(?i)(?<![A-Za-z])(R|ZAR|USD|EUR|GBP|\\$|\\u20ac|\\u00a3)\\s?(\\d[\\d ,.]*\\d|\\d)")
        private val REF = Regex("(?i)\\b(?:INV|PO|QUOTE|ORDER|REF)[-\\s#]?\\d{2,}\\b|(?i)\\binvoice\\s*#?\\s*\\d{3,}\\b")
        private val INVOICE = Regex("\\b(invoice|inv[-\\s#]?\\d|amount due|payment (is )?(due|overdue)|please pay|statement)\\b")
        private val ASK = Regex("(please (send|sign|confirm|review|reply|pay|approve|share|submit|check)|can you|could you|would you|need you to|kindly|waiting (on|for) you|require[sd]? (your|you)|action required|asking for|\\bremind)")
        private val AWAITING = Regex("(i'?ll (send|get|share|have|do|come back)|i will (send|get|share)|will send (you )?|promised|should have (it|them) (to you )?|by tomorrow i|on my way with)")
        private val DECISION = Regex("(we('ve| have)? (decided|agreed)|\\bapproved\\b|go ahead|signed off|sign-off|confirmed that)")
        private val MEETING = Regex("(meeting|\\bcall\\b|reschedul|moved to|invite|appointment|catch-?up|workshop)")
        private val DOCUMENT = Regex("\\b(lease|contract|agreement|proposal|quote|quotation|certificate|statement|nda|policy|permit|passport|licen[cs]e)\\b")
        private val THING = Regex("\\b(lease|contract|agreement|refund|deposit|quotation|quote|nda)\\b")
    }
}
