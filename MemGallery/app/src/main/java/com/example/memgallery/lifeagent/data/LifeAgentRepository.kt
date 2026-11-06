package com.example.memgallery.lifeagent.data

import android.util.Log
import com.example.memgallery.lifeagent.collect.CollectionProgressBus
import com.example.memgallery.lifeagent.collect.CollectionRunner
import com.example.memgallery.lifeagent.composio.ComposioApi
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.ComposioProject
import com.example.memgallery.lifeagent.composio.KeyCheck
import com.example.memgallery.lifeagent.composio.KeyKind
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.data.repository.SettingsRepository
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
    private val composio: ComposioApi,
    private val settings: SettingsRepository,
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
        val known = LifeApp.entries.map { app ->
            rows.firstOrNull { it.id == app.id }?.toModel()
                ?: LifeConnection(app.id, app.label, false, null, 0L, null)
        }
        // Any other toolkit he connected lives beside the four, never instead of them.
        known + rows.filter { LifeApp.from(it.id) == null }.map { it.toModel() }
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

    // ---- connections: any toolkit, not only the four ----

    /** The cached Composio catalog joined with what is connected here. Connected toolkits come first. */
    val toolkits: Flow<List<LifeToolkit>> = combine(dao.observeToolkits(), dao.observeConnections()) { rows, conns ->
        val bySlug = conns.associateBy { it.id }
        rows.map { t ->
            val c = bySlug[t.slug]
            LifeToolkit(
                slug = t.slug,
                name = t.name,
                description = t.description,
                logoUrl = t.logoUrl,
                categories = t.categories?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                noAuth = t.noAuth,
                connected = c?.connected == true,
                accountLabel = c?.accountLabel,
                lastPulledAt = c?.lastPulledAt ?: 0L,
                lastError = c?.lastError,
            )
        }.sortedWith(compareByDescending<LifeToolkit> { it.connected }.thenBy { it.name.lowercase() })
    }

    /**
     * Refreshes the toolkit catalog from Composio (paged) and caches it. A cache younger than a day is used unless
     * [force] is set or the user is searching, so the screen opens instantly and works with no network.
     */
    suspend fun syncToolkits(force: Boolean = false, search: String? = null, category: String? = null): Int {
        val stamp = dao.toolkitCacheStamp() ?: 0L
        val fresh = System.currentTimeMillis() - stamp < DAY_MS
        if (!force && fresh && search.isNullOrBlank() && category.isNullOrBlank()) return dao.getToolkits().size
        val out = ArrayList<com.example.memgallery.lifeagent.data.LifeToolkitEntity>()
        var cursor: String? = null
        var pages = 0
        while (pages < MAX_TOOLKIT_PAGES) {
            val page = composio.listToolkits(cursor = cursor, search = search, category = category, limit = 100)
            out += page.items.map { t ->
                com.example.memgallery.lifeagent.data.LifeToolkitEntity(
                    slug = t.slug, name = t.name, description = t.description, logoUrl = t.logoUrl,
                    categories = t.categories.takeIf { it.isNotEmpty() }?.joinToString(","),
                    authSchemes = t.authSchemes.takeIf { it.isNotEmpty() }?.joinToString(","),
                    noAuth = t.noAuth, toolkitType = t.toolkitType,
                    readTools = dao.getToolkit(t.slug)?.readTools,
                    fetchedAt = System.currentTimeMillis(),
                )
            }
            cursor = page.nextCursor
            pages++
            if (cursor.isNullOrBlank()) break
        }
        if (out.isNotEmpty()) dao.upsertToolkits(out)
        return dao.getToolkits().size
    }

    /** Pulls the connected accounts from Composio and marks the matching toolkits connected or not. */
    suspend fun syncConnections(): List<ConnectOutcome> {
        val accounts = composio.listConnectedAccounts()
        val active = accounts.filter { it.active }
        val slugs = (dao.getToolkits().map { it.slug } + active.map { it.toolkit }).distinct()
        val now = System.currentTimeMillis()
        for (slug in slugs) {
            val acc = active.firstOrNull { it.toolkit.equals(slug, ignoreCase = true) }
            val old = dao.getConnection(slug)
            val name = dao.getToolkit(slug)?.name ?: LifeApp.from(slug)?.label ?: slug
            dao.upsertConnection(
                LifeConnectionEntity(
                    id = slug,
                    label = old?.label ?: name,
                    connected = acc != null,
                    accountLabel = acc?.label ?: old?.accountLabel,
                    lastPulledAt = old?.lastPulledAt ?: 0L,
                    lastError = if (acc == null) old?.lastError else null,
                    accountId = acc?.id,
                    userId = acc?.userId,
                )
            )
        }
        changed()
        return slugs.map { ConnectOutcome.Connected(dao.getConnection(it)?.accountLabel) }
    }

    /**
     * Connects any toolkit. Already connected in his Composio project: marks it here. Not yet: makes a connect link
     * for him to open, and the screen says "finish in the browser". Nothing is written to the outside app.
     */
    suspend fun connectToolkit(slug: String, displayName: String? = null): ConnectOutcome {
        val name = displayName ?: dao.getToolkit(slug)?.name ?: LifeApp.from(slug)?.label ?: slug
        val old = dao.getConnection(slug) ?: LifeConnectionEntity(slug, name, false, null, 0L, null, null, null)
        return try {
            val acc = composio.listConnectedAccounts().firstOrNull {
                it.active && it.toolkit.equals(slug, ignoreCase = true)
            }
            if (acc != null) {
                dao.upsertConnection(
                    old.copy(connected = true, lastError = null, accountId = acc.id, userId = acc.userId, accountLabel = acc.label ?: old.accountLabel)
                )
                cacheReadTools(slug)
                changed()
                ConnectOutcome.Connected(acc.label ?: old.accountLabel ?: name)
            } else {
                val link = composio.createConnectLink(slug, userId = MEMGALLERY_COMPOSIO_USER)
                ConnectOutcome.Browser(link.redirectUrl)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = (e.message ?: "Could not reach Composio").take(160)
            dao.upsertConnection(old.copy(connected = false, lastError = message))
            changed()
            ConnectOutcome.Failed(message)
        }
    }

    /** Stops pulling from this toolkit. Nothing is changed in the toolkit itself; collected memories stay. */
    suspend fun disconnectToolkit(slug: String) {
        val old = dao.getConnection(slug) ?: return
        dao.upsertConnection(old.copy(connected = false, accountId = null, userId = null, lastError = null))
        changed()
    }

    /** The read tools of a toolkit: the four hand-written ones, or whatever its own catalog says only reads. */
    suspend fun readTools(slug: String): List<String> {
        val known = knownReadTools(slug)
        if (known.isNotEmpty()) return known
        val cached = dao.getToolkit(slug)?.readTools?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        if (cached.isNotEmpty()) return cached
        return cacheReadTools(slug)
    }

    private fun knownReadTools(slug: String): List<String> = when (LifeApp.from(slug)) {
        LifeApp.GMAIL -> listOf(ComposioCatalog.GMAIL_FETCH)
        LifeApp.GCAL -> listOf(ComposioCatalog.CAL_LIST)
        LifeApp.CLICKUP -> listOf(ComposioCatalog.CLICKUP_WORKSPACES, ComposioCatalog.CLICKUP_TASKS)
        LifeApp.SLACK -> listOf(ComposioCatalog.SLACK_CHANNELS, ComposioCatalog.SLACK_HISTORY)
        null -> emptyList()
    }

    private suspend fun cacheReadTools(slug: String): List<String> {
        if (knownReadTools(slug).isNotEmpty()) return knownReadTools(slug)
        return try {
            val page = composio.listTools(slug, limit = 100)
            val reads = page.items.map { it.slug }.filter { ReadToolRules.isReadTool(it) }.take(MAX_READ_TOOLS).distinct()
            dao.setToolkitReadTools(slug, reads.takeIf { it.isNotEmpty() }?.joinToString(","))
            reads
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Checks the stored key with Composio and remembers which kind it is and the project it lands in. */
    suspend fun checkKey(): KeyCheck {
        val check = composio.checkKey()
        if (check.ok) {
            settings.saveComposioKeyKind(check.kind)
            if (check.projectId != null || check.projectName != null) {
                settings.saveComposioProject(check.projectId, check.projectName, check.orgId)
            }
        }
        changed()
        return check
    }

    /** The projects a personal key can see, for the picker. */
    suspend fun projects(): List<ComposioProject> = runCatching { composio.listProjects() }.getOrDefault(emptyList())

    suspend fun chooseProject(project: ComposioProject) {
        settings.saveComposioProject(project.id, project.name, project.orgId)
        settings.saveComposioKeyKind(KeyKind.USER)
        changed()
    }

    /** Keeps the screenshot deck honest about a connected app with no memories yet. */
    suspend fun connectionFor(slug: String): LifeConnectionEntity? = dao.getConnection(slug)

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
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_TOOLKIT_PAGES = 8
        const val MAX_READ_TOOLS = 12
        /** The user id MemGallery uses inside his own Composio project. Stable, so a reconnect finds the same account. */
        const val MEMGALLERY_COMPOSIO_USER = "memgallery"
        private const val TAG = "LifeAgentRepo"
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
