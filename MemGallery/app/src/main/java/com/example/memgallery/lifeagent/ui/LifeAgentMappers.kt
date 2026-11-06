package com.example.memgallery.lifeagent.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.example.memgallery.R
import com.example.memgallery.lifeagent.data.AppProgress
import com.example.memgallery.lifeagent.data.AppState
import com.example.memgallery.lifeagent.data.CollectionProgress
import com.example.memgallery.lifeagent.data.DailyReport
import com.example.memgallery.lifeagent.data.LearnedSummary
import com.example.memgallery.lifeagent.data.LifeApp
import com.example.memgallery.lifeagent.data.LifeConnection
import com.example.memgallery.lifeagent.data.LifeKind
import com.example.memgallery.lifeagent.data.LifeMeeting
import com.example.memgallery.lifeagent.data.LifePerson
import com.example.memgallery.lifeagent.data.LifeProposal
import com.example.memgallery.lifeagent.data.LifeRun
import com.example.memgallery.lifeagent.data.LifeTask
import com.example.memgallery.lifeagent.data.PersonDetail
import com.example.memgallery.lifeagent.data.ReportItem
import com.example.memgallery.lifeagent.data.SenderKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the mappers get their words. The ViewModels use [ContextText]; tests can use the same on a Robolectric context. */
interface LifeText {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

class ContextText(private val context: Context) : LifeText {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
    override fun plural(id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)
}

/** Dates, zone and words for one mapping pass. A fixed [now] and [zone] make every mapping deterministic. */
class LifeCtx(val text: LifeText, val now: Long, val zone: ZoneId = ZoneId.systemDefault(), val locale: Locale = Locale.getDefault()) {
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayShort = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)

    val today: LocalDate get() = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private fun date(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun clock(at: Long): String = time.format(Instant.ofEpochMilli(at).atZone(zone))
    fun dayOf(at: Long): String = day.format(Instant.ofEpochMilli(at).atZone(zone))
    fun dayLong(at: Long): String = dayLong.format(Instant.ofEpochMilli(at).atZone(zone))
    fun daysFromToday(at: Long): Long = java.time.temporal.ChronoUnit.DAYS.between(today, date(at))

    /** "17:04" for today, "Thu 5 Nov" otherwise. */
    fun clockOrDay(at: Long): String = if (daysFromToday(at) == 0L) clock(at) else dayOf(at)

    /** "Today", "Yesterday" or "6 Nov". */
    fun relativeDay(at: Long): String = when (daysFromToday(at)) {
        0L -> text.get(R.string.la_word_today)
        -1L -> text.get(R.string.la_word_yesterday)
        else -> dayShort.format(Instant.ofEpochMilli(at).atZone(zone))
    }

    /** "Sat 7 Nov, 16:00". */
    fun dayAndTime(at: Long): String = dayOf(at) + ", " + clock(at)

    /** "today 17:00", "tomorrow 09:00", "Mon 9 Nov" or "2 days late". Used on report rows. */
    fun whenLabel(at: Long?): String {
        if (at == null) return text.get(R.string.la_when_none)
        val d = daysFromToday(at)
        return when {
            d < 0 -> text.plural(R.plurals.la_when_late, (-d).toInt(), (-d).toInt())
            d == 0L -> text.get(R.string.la_when_today_at, clock(at))
            d == 1L -> text.get(R.string.la_when_tomorrow_at, clock(at))
            else -> dayOf(at)
        }
    }
}

private fun cleanKey(key: String) = key.trim().lowercase()

// ---- Connections ----

fun mapConnections(
    conns: List<LifeConnection>,
    key: String?,
    enabled: Boolean,
    hour: Int,
    minute: Int,
    busy: Set<LifeAppId>,
    keyError: String?,
    keySaving: Boolean,
    ctx: LifeCtx,
): ConnectionsUiState {
    val rows = LifeAppId.entries.map { app ->
        val c = conns.firstOrNull { it.id == app.id }
        ConnectionRowUi(
            app = app,
            connected = c?.connected == true,
            lastPulled = c?.lastPulledAt?.takeIf { it > 0L }?.let { ctx.clockOrDay(it) },
            error = c?.lastError?.takeIf { it.isNotBlank() },
            busy = app in busy,
        )
    }
    return ConnectionsUiState(
        keySet = !key.isNullOrBlank(),
        maskedKey = maskKey(key),
        rows = rows,
        keyError = keyError,
        keySaving = keySaving,
        collectionEnabled = enabled,
        hour = hour,
        minute = minute,
    )
}

/** Dots plus the last four characters. Never anything else of the key. */
fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return ""
    val tail = if (key.length > 8) key.takeLast(4) else ""
    return "•".repeat(15) + tail
}

// ---- Collecting ----

fun mapCollecting(progress: CollectionProgress, latest: LifeRun?, finishedThisSession: Boolean, connected: List<LifeAppId>, ctx: LifeCtx): CollectingUiState {
    if (progress.running) {
        val apps = progress.apps.mapNotNull { it.toUi() }
        val counted = apps.filter { it.state != StepState.Skipped }
        val step = (counted.indexOfFirst { it.state == StepState.Running }.let { if (it < 0) counted.count { a -> a.state == StepState.Done || a.state == StepState.Failed } else it } + 1)
            .coerceIn(1, counted.size.coerceAtLeast(1))
        return CollectingUiState(
            running = true, step = step, steps = counted.size,
            startedLabel = progress.startedAt?.let { ctx.clock(it) }.orEmpty(),
            found = apps.sumOf { it.found }, dropped = apps.sumOf { it.dropped }, apps = apps,
        )
    }
    if (finishedThisSession && latest != null) {
        val apps = latest.apps.mapNotNull { st ->
            val app = LifeAppId.from(st.app) ?: return@mapNotNull null
            CollectAppUi(
                app = app,
                state = when (st.state) { "failed" -> StepState.Failed; "skipped" -> StepState.Skipped; else -> StepState.Done },
                found = st.found, dropped = st.dropped,
                reasons = if (st.state == "failed") emptyList() else listOfNotNull(st.note?.takeIf { it.isNotBlank() }),
                note = if (st.state == "failed") st.note else null,
            )
        }
        return CollectingUiState(
            running = false, finished = true, step = apps.size, steps = apps.size,
            startedLabel = ctx.clock(latest.startedAt),
            found = apps.sumOf { it.found }, dropped = latest.noiseDropped, apps = apps, proposalsCreated = latest.proposalsCreated,
        )
    }
    // Idle: [apps] only says which apps are connected, so the screen can tell "no apps" from "ready".
    return CollectingUiState(apps = connected.map { CollectAppUi(it, StepState.Queued) })
}

private fun AppProgress.toUi(): CollectAppUi? {
    val id = LifeAppId.from(app) ?: return null
    return CollectAppUi(
        app = id,
        state = when (state) {
            AppState.WAITING -> StepState.Queued
            AppState.RUNNING -> StepState.Running
            AppState.DONE -> StepState.Done
            AppState.FAILED -> StepState.Failed
            AppState.SKIPPED -> StepState.Skipped
        },
        found = found, dropped = dropped,
        reasons = if (state == AppState.DONE) listOfNotNull(note?.takeIf { it.isNotBlank() }) else emptyList(),
        note = if (state == AppState.DONE) null else note,
    )
}

// ---- Review ----

fun mapCard(p: LifeProposal, tasks: Map<String, LifeTask>, people: List<LifePerson>, ctx: LifeCtx): ReviewCardUi {
    val app = LifeAppId.from(p.app)
    val role = p.personName?.let { n -> people.firstOrNull { SenderKey.personId(it.name) == SenderKey.personId(n) }?.role }
    var label: String? = null
    var tag: String? = null
    var tone = DateTone.Upcoming
    p.dueAt?.let { due ->
        val d = ctx.daysFromToday(due)
        label = when (d) {
            0L -> ctx.text.get(R.string.la_date_today, ctx.dayOf(due) + " · " + ctx.clock(due))
            1L -> ctx.text.get(R.string.la_date_tomorrow, ctx.dayOf(due) + " · " + ctx.clock(due))
            else -> ctx.dayOf(due) + " · " + ctx.clock(due)
        }
        when {
            d < 0 -> { tag = ctx.text.get(R.string.la_tag_overdue); tone = DateTone.Overdue }
            d == 0L -> { tag = ctx.text.get(R.string.la_tag_due_today); tone = DateTone.Today }
            d == 1L -> { tag = ctx.text.get(R.string.la_tag_tomorrow); tone = DateTone.Today }
            else -> tag = ctx.text.get(R.string.la_tag_in_days, d.toInt())
        }
    }
    val sourceName = app?.let { LifeApp.from(it.id)?.label } ?: p.app
    return ReviewCardUi(
        id = p.id, app = app, appLabel = sourceName, title = p.title.trim(),
        personName = p.personName?.takeIf { it.isNotBlank() }, personRole = role,
        why = p.reason.ifBlank { p.detail },
        dateLabel = label, dateTag = tag, dateTone = tone,
        matchedTask = p.matchedTaskId?.let { tasks[it]?.title },
        sourceTitle = p.sourceTitle,
        sourceSub = listOfNotNull(sourceName, p.personName?.takeIf { it.isNotBlank() }, ctx.relativeDay(p.collectedAt)).joinToString(" · "),
        sourceUrl = p.sourceUrl?.takeIf { it.isNotBlank() },
        suggestKeep = p.confidence >= KEEP_THRESHOLD,
    )
}

/** At or above this confidence MemGallery suggests keeping a proposal; below it suggests dropping. */
const val KEEP_THRESHOLD = 0.5f

fun mapLearned(learned: LearnedSummary, ctx: LifeCtx): Pair<List<LearnedCountUi>, Int> {
    val rows = learned.byReason.entries.sortedByDescending { it.value }.map { (reason, n) ->
        val k = cleanKey(reason)
        val (kind, res) = when {
            k.contains("newsletter") -> "newsletter" to R.string.la_reason_newsletter
            k.contains("promo") -> "promotion" to R.string.la_reason_promotion
            k.contains("receipt") -> "receipt" to R.string.la_reason_receipt
            k.contains("social") -> "social" to R.string.la_reason_social
            k.contains("duplicate") || k.contains("repeat") || k.contains("known") -> "duplicate" to R.string.la_reason_duplicate
            else -> "other" to R.string.la_reason_other
        }
        LearnedCountUi(kind, ctx.text.get(res), n)
    }
    // Several raw reasons can map to one label; add them up.
    val merged = rows.groupBy { it.kind }.map { (kind, list) -> LearnedCountUi(kind, list.first().label, list.sumOf { it.count }) }
        .sortedByDescending { it.count }
    return merged to merged.sumOf { it.count }
}

fun nextCollectionLabel(enabled: Boolean, hour: Int, minute: Int, ctx: LifeCtx): String {
    if (!enabled) return ctx.text.get(R.string.la_next_off)
    val zone = ctx.zone
    val nowZ = Instant.ofEpochMilli(ctx.now).atZone(zone)
    var next = nowZ.toLocalDate().atTime(hour, minute).atZone(zone)
    if (!next.isAfter(nowZ)) next = next.plusDays(1)
    val at = next.toInstant().toEpochMilli()
    val time = ctx.clock(at)
    return if (ctx.daysFromToday(at) == 0L) ctx.text.get(R.string.la_next_today, time)
    else ctx.text.get(R.string.la_next_tomorrow, ctx.dayOf(at), time)
}

// ---- People ----

private fun commitmentOf(p: LifeProposal): CommitmentKind? = when (p.kind) {
    LifeKind.COMMITMENT, LifeKind.DEADLINE, LifeKind.MONEY -> CommitmentKind.YouOwe
    LifeKind.AWAITING -> if (p.dueAt != null) CommitmentKind.OwesYou else CommitmentKind.Waiting
    LifeKind.EVENT -> CommitmentKind.Meeting
    else -> null
}

fun mapPeople(people: List<LifePerson>, kept: List<LifeProposal>, ctx: LifeCtx): List<PersonRowUi> = people.map { p ->
    val mine = kept.filter { it.personName?.let { n -> SenderKey.personId(n) } == SenderKey.personId(p.name) }
    val open = mine.firstOrNull { commitmentOf(it) != null }
    PersonRowUi(
        id = p.id, name = p.name, role = p.role.orEmpty(),
        lastLabel = ctx.relativeDay(p.lastSeenAt),
        commitmentKind = open?.let { commitmentOf(it) },
        commitmentText = open?.title ?: p.lastMemory,
        hasOpen = p.openCommitments > 0,
        sortKey = p.lastSeenAt,
    )
}

fun mapPerson(detail: PersonDetail, tasks: Map<String, LifeTask>, ctx: LifeCtx): PersonUiState {
    val person = detail.person ?: return PersonUiState(found = false)
    fun commitment(p: LifeProposal, kind: CommitmentKind): CommitmentUi {
        val taskId = p.matchedTaskId ?: p.dueAt?.let { "p:${p.id}" }
        val task = taskId?.let { tasks[it] }
        return CommitmentUi(
            key = p.id, kind = kind, title = p.title, app = LifeAppId.from(p.app),
            dateLabel = p.dueAt?.let { ctx.dayAndTime(it) },
            taskId = task?.let { taskId }, done = task?.doneLocal == true,
        )
    }
    val commitments = detail.youOwe.map { commitment(it, CommitmentKind.YouOwe) } +
        detail.owedToYou.map { commitment(it, if (it.dueAt != null) CommitmentKind.OwesYou else CommitmentKind.Waiting) }
    return PersonUiState(
        found = true, name = person.name, role = person.role.orEmpty(), lastLabel = ctx.relativeDay(person.lastSeenAt),
        commitments = commitments,
        memories = detail.recent.take(6).map {
            PersonMemoryUi(it.id, it.title, LifeAppId.from(it.app), it.app, ctx.relativeDay(it.collectedAt))
        },
        email = person.email, phone = person.phone,
    )
}

// ---- Daily report ----

private fun awareKind(k: LifeKind?): AwareKind = when (k) {
    LifeKind.DEADLINE -> AwareKind.Deadline
    LifeKind.MONEY -> AwareKind.Money
    LifeKind.EVENT -> AwareKind.Meeting
    LifeKind.SECURITY -> AwareKind.Security
    LifeKind.PERSON, LifeKind.COMMITMENT -> AwareKind.Person
    LifeKind.AWAITING -> AwareKind.Awaiting
    else -> AwareKind.Other
}

fun mapReport(report: DailyReport, proposals: List<LifeProposal>, anyConnected: Boolean, ctx: LifeCtx): DailyReportUiState {
    val byId = proposals.associateBy { it.id }
    fun task(i: ReportItem, overdue: Boolean) = ReportTaskUi(
        taskId = i.id, title = i.title, app = LifeAppId.from(i.source) ?: if (i.source == "clickup") LifeAppId.CLICKUP else null,
        whenLabel = ctx.whenLabel(i.dueAt), overdue = overdue,
    )
    return DailyReportUiState(
        dateLabel = ctx.dayLong(report.date),
        collectedLabel = report.collectedAt?.let { ctx.clock(it) },
        aware = report.aware.map { i ->
            val p = byId[i.id]
            AwareRowUi(
                key = i.id, kind = awareKind(p?.kind), title = i.title,
                app = LifeAppId.from(i.source), appLabel = i.source.orEmpty(),
                sub = i.dueAt?.let { ctx.dayAndTime(it) } ?: p?.personName ?: i.subtitle.orEmpty(),
                url = i.url?.takeIf { it.isNotBlank() }, proposalId = i.id,
            )
        },
        dueToday = report.dueToday.map { task(it, false) },
        overdue = report.overdue.map { task(it, true) },
        meetings = report.meetings.map { it.toRow(ctx) },
        open = report.open.map { ReportOpenUi(it.id, it.title) },
        toReview = report.toReview,
        anyConnected = anyConnected,
    )
}

private fun LifeMeeting.toRow(ctx: LifeCtx): ReportMeetingUi {
    val ended = endAt < ctx.now
    val dayPrefix = if (ctx.daysFromToday(startAt) == 0L) "" else ctx.dayOf(startAt) + ", "
    return ReportMeetingUi(
        key = id, timeLabel = ctx.clock(startAt), title = title, app = LifeAppId.GCAL,
        sub = if (ended) ctx.text.get(R.string.la_report_ended_at, ctx.clock(endAt)) else (dayPrefix + location.orEmpty()).trim().trim(',').trim(),
        ended = ended, joinUrl = joinUrl?.takeIf { it.isNotBlank() },
    )
}
