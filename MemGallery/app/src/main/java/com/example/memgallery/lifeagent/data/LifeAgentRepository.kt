package com.example.memgallery.lifeagent.data

import android.util.Log
import com.example.memgallery.lifeagent.collect.CollectionProgressBus
import com.example.memgallery.lifeagent.collect.CollectionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The only thing the Life Agent screens and widgets talk to. READ ONLY towards every external app: nothing in
 * here can write to Gmail, Calendar, ClickUp or Slack. Local state (kept, dropped, done, snoozed) lives in Room.
 *
 * [onDataChanged] is a hook the widget owner sets to refresh Glance after every write and after a collection run.
 */
@Singleton
class LifeAgentRepository @Inject constructor(
    private val dao: LifeAgentDao,
    private val runner: Provider<CollectionRunner>,
    private val bus: CollectionProgressBus,
) {
    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var zone: ZoneId = ZoneId.systemDefault()

    /** Set by the widget owner: called after every local write and after a collection run. */
    @Volatile
    var onDataChanged: (suspend () -> Unit)? = null

    private suspend fun changed() {
        try { onDataChanged?.invoke() } catch (e: Exception) { Log.w(TAG, "onDataChanged failed", e) }
    }

    // ---- reads ----

    val connections: Flow<List<LifeConnection>> = dao.observeConnections().map { rows ->
        LifeApp.entries.map { app ->
            rows.firstOrNull { it.id == app.id }?.toModel()
                ?: LifeConnection(app.id, app.label, false, null, 0L, null)
        }
    }

    /** Proposals waiting for review (status proposed), newest first. */
    val proposals: Flow<List<LifeProposal>> = dao.observeProposed().map { it.map(LifeProposalEntity::toModel) }

    /** Every proposal, whatever its status. */
    val allProposals: Flow<List<LifeProposal>> = dao.observeAllProposals().map { it.map(LifeProposalEntity::toModel) }

    val proposedCount: Flow<Int> = dao.observeProposedCount()

    /** What the "caught up" screen says was learned from the user's decisions. */
    val learned: Flow<LearnedSummary> = combine(dao.observeDropCount(), dao.observeKeepCount(), dao.observeLatestRun()) { d, k, run ->
        LearnedSummary(d, k, run?.let { runStatsFromJson(it.appStatsJson).second } ?: emptyMap())
    }

    val people: Flow<List<LifePerson>> = combine(dao.observePeople(), dao.observeKept()) { people, kept ->
        people.map { p -> p.toModel(kept) }
    }

    fun person(name: String): Flow<PersonDetail> =
        combine(dao.observePerson(SenderKey.personId(name)), dao.observeKeptForPerson(name)) { p, kept ->
            val models = kept.map(LifeProposalEntity::toModel)
            PersonDetail(
                person = p?.toModel(kept),
                youOwe = models.filter { it.kind in YOU_OWE },
                owedToYou = models.filter { it.kind == LifeKind.AWAITING },
                recent = models,
            )
        }

    val tasks: Flow<List<LifeTask>> = dao.observeTasks().map { rows ->
        rows.filterNot { isClosed(it.status) }.map(LifeTaskEntity::toModel)
    }

    /** Today's meetings and the coming week. */
    val meetings: Flow<List<LifeMeeting>> = dao.observeMeetingsFrom(0L).map { rows ->
        rows.filter { it.endAt >= startOfDay(clock()) }.map(LifeMeetingEntity::toModel)
    }

    val latestRun: Flow<LifeRun?> = dao.observeLatestRun().map { it?.toModel() }

    val collectionProgress: StateFlow<CollectionProgress> get() = bus.progress

    fun dailyReport(): Flow<DailyReport> = combine(
        dao.observeTasks(),
        dao.observeKept(),
        dao.observeMeetingsFrom(0L),
        dao.observeLatestRun(),
        dao.observeProposedCount(),
    ) { tasks, kept, meetings, run, toReview ->
        buildReport(tasks, kept, meetings, run, toReview, clock())
    }

    internal fun buildReport(
        tasks: List<LifeTaskEntity>,
        kept: List<LifeProposalEntity>,
        meetings: List<LifeMeetingEntity>,
        run: LifeRunEntity?,
        toReview: Int,
        now: Long,
    ): DailyReport {
        val dayStart = startOfDay(now)
        val dayEnd = dayStart + DAY_MS
        val open = tasks.filter { !isClosed(it.status) && !it.doneLocal }
        val live = open.filter { (it.snoozedUntil ?: 0L) <= now }
        fun item(t: LifeTaskEntity) = ReportItem(t.id, t.title, t.listName, t.dueAt, true, t.source, t.url)
        val aware = kept
            .filter { it.collectedAt >= now - 7 * DAY_MS && (it.dueAt == null || it.dueAt >= dayStart) }
            .map { ReportItem(it.id, it.title, it.reason, it.dueAt, false, it.app, it.sourceUrl) }
        return DailyReport(
            date = dayStart,
            collectedAt = run?.finishedAt ?: run?.startedAt,
            dueToday = live.filter { it.dueAt != null && it.dueAt >= dayStart && it.dueAt < dayEnd }.sortedBy { it.dueAt }.map(::item),
            overdue = live.filter { it.dueAt != null && it.dueAt < dayStart }.sortedBy { it.dueAt }.map(::item),
            aware = aware,
            meetings = meetings.filter { it.endAt >= dayStart && it.startAt < dayStart + 7 * DAY_MS }.map(LifeMeetingEntity::toModel),
            open = open.sortedWith(compareBy({ it.dueAt == null }, { it.dueAt })).map(::item),
            toReview = toReview,
        )
    }

    // ---- connections ----

    /** Looks the app up in the user's Composio account and marks it connected. Read only. */
    suspend fun connect(app: LifeApp) {
        Log.i(TAG, "connect ${app.id} (wired in the Composio step)")
        val old = dao.getConnection(app.id)
        dao.upsertConnection(
            (old ?: LifeConnectionEntity(app.id, app.label, false, null, 0L, null, null, null))
                .copy(connected = true, lastError = null),
        )
        changed()
    }

    suspend fun connect(appId: String) { LifeApp.from(appId)?.let { connect(it) } }

    /** Stops pulling from this app. Nothing is changed in the app itself; already collected memories stay. */
    suspend fun disconnect(app: LifeApp) {
        val old = dao.getConnection(app.id) ?: return
        dao.upsertConnection(old.copy(connected = false, accountId = null, userId = null, lastError = null))
        changed()
    }

    suspend fun disconnect(appId: String) { LifeApp.from(appId)?.let { disconnect(it) } }

    // ---- collection ----

    suspend fun runCollection(): CollectionResult {
        val r = runner.get().run()
        changed()
        return r
    }

    // ---- decisions ----

    suspend fun keep(id: String) {
        val p = dao.getProposal(id) ?: return
        if (p.status != ProposalStatus.PROPOSED.key) return
        val now = clock()
        dao.setProposalStatus(id, ProposalStatus.KEPT.key, now)
        learn(p, "keep", now)
        p.personName?.takeIf { it.isNotBlank() }?.let { upsertPerson(it, p, now) }
        if (p.dueAt != null && p.matchedTaskId == null) {
            // A kept memory with a date becomes a local task, so it can be ticked, snoozed and nudged.
            dao.insertTask(LifeTaskEntity("p:$id", "memory", null, p.title, p.dueAt, "open", false, null, p.sourceUrl))
        }
        changed()
    }

    suspend fun drop(id: String) {
        val p = dao.getProposal(id) ?: return
        if (p.status != ProposalStatus.PROPOSED.key) return
        val now = clock()
        dao.setProposalStatus(id, ProposalStatus.DROPPED.key, now)
        learn(p, "drop", now)
        changed()
    }

    suspend fun keepAll() {
        dao.getProposed().forEach { keep(it.id) }
    }

    suspend fun markDone(taskId: String) { dao.setTaskDone(taskId, true); changed() }
    suspend fun markUndone(taskId: String) { dao.setTaskDone(taskId, false); changed() }
    suspend fun snooze(taskId: String, until: Long) { dao.setTaskSnooze(taskId, until); changed() }

    /** The one paragraph a chat question about this item is opened with. [id] is a proposal, a task or a meeting. */
    suspend fun askContext(id: String): String {
        dao.getProposal(id)?.let { p ->
            val src = dao.findItem(p.app, p.sourceId)
            return buildString {
                append(p.title).append('.')
                if (p.detail.isNotBlank()) append(' ').append(p.detail)
                p.personName?.let { append(" Person: $it.") }
                p.dueAt?.let { append(" Due: ${fmt(it)}.") }
                p.amountCents?.let { append(" Amount: ${it / 100.0}${p.currency?.let { c -> " $c" } ?: ""}.") }
                append(" Source: ${LifeApp.from(p.app)?.label ?: p.app}")
                if (p.sourceTitle.isNotBlank()) append(", \"${p.sourceTitle}\"")
                append('.')
                src?.snippet?.takeIf { it.isNotBlank() }?.let { append(" Excerpt: ${it.take(400)}") }
            }
        }
        dao.getTask(id)?.let { t ->
            return buildString {
                append("Task: ${t.title}.")
                t.listName?.let { append(" List: $it.") }
                t.dueAt?.let { append(" Due: ${fmt(it)}.") }
                append(" Status: ${if (t.doneLocal) "done (marked here)" else t.status}.")
            }
        }
        return ""
    }

    // ---- internals ----

    private suspend fun learn(p: LifeProposalEntity, decision: String, now: Long) {
        val author = dao.findItem(p.app, p.sourceId)?.author
        val key = SenderKey.of(author).ifBlank { SenderKey.personId(p.personName ?: "") }
        dao.insertLearning(LifeLearningEntity(app = p.app, senderKey = key, kind = p.kind, decision = decision, decidedAt = now))
    }

    private suspend fun upsertPerson(name: String, p: LifeProposalEntity, now: Long) {
        val id = SenderKey.personId(name)
        val old = dao.getPerson(id)
        val email = SenderKey.emailOf(dao.findItem(p.app, p.sourceId)?.author)
        dao.upsertPerson(
            (old ?: LifePersonEntity(id, name.trim(), null, null, null, now))
                .copy(email = old?.email ?: email, lastSeenAt = now),
        )
    }

    private fun fmt(at: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("EEE d MMM HH:mm", java.util.Locale.getDefault())
            .format(Instant.ofEpochMilli(at).atZone(zone))

    internal fun startOfDay(at: Long): Long =
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        private const val TAG = "LifeAgentRepo"
        const val DAY_MS = 24L * 60 * 60 * 1000
        private val YOU_OWE = setOf(LifeKind.COMMITMENT, LifeKind.DEADLINE, LifeKind.MONEY)
        private val OPEN_KINDS = setOf(LifeKind.COMMITMENT, LifeKind.DEADLINE, LifeKind.MONEY, LifeKind.AWAITING)
        private val CLOSED = setOf("complete", "completed", "closed", "done")
        fun isClosed(status: String) = status.trim().lowercase() in CLOSED

        internal fun LifePersonEntity.toModel(kept: List<LifeProposalEntity>): LifePerson {
            val mine = kept.filter { it.personName?.let { n -> SenderKey.personId(n) } == id }
            return LifePerson(
                id, name, role, email, phone, lastSeenAt,
                openCommitments = mine.count { LifeKind.from(it.kind) in OPEN_KINDS },
                lastMemory = mine.firstOrNull()?.title,
            )
        }
    }
}

internal fun LifeConnectionEntity.toModel() = LifeConnection(id, label, connected, accountLabel, lastPulledAt, lastError)

internal fun LifeProposalEntity.toModel() = LifeProposal(
    id, LifeKind.from(kind), title, detail, app, sourceId, sourceTitle, sourceUrl, personName, dueAt, amountCents,
    currency, confidence, ProposalStatus.from(status), reason, matchedTaskId, collectedAt, decidedAt,
)

internal fun LifeTaskEntity.toModel() = LifeTask(id, source, listName, title, dueAt, status, doneLocal, snoozedUntil, url)

internal fun LifeMeetingEntity.toModel() = LifeMeeting(id, title, startAt, endAt, location, joinUrl)

internal fun LifeRunEntity.toModel(): LifeRun {
    val (apps, noise) = runStatsFromJson(appStatsJson)
    return LifeRun(id, startedAt, finishedAt, status, apps, noise, noiseDropped, proposalsCreated)
}
