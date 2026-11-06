package com.example.memgallery.lifeagent.collect

import android.util.Log
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.lifeagent.composio.ComposioApi
import com.example.memgallery.lifeagent.composio.ComposioCatalog
import com.example.memgallery.lifeagent.composio.ComposioErrorKind
import com.example.memgallery.lifeagent.composio.ComposioException
import com.example.memgallery.lifeagent.composio.GenericParser
import com.example.memgallery.lifeagent.composio.ReadToolRules
import com.example.memgallery.lifeagent.composio.RawItem
import com.example.memgallery.lifeagent.composio.RawType
import com.example.memgallery.lifeagent.data.AppProgress
import com.example.memgallery.lifeagent.data.AppRunStat
import com.example.memgallery.lifeagent.data.AppState
import com.example.memgallery.lifeagent.data.CollectionProgress
import com.example.memgallery.lifeagent.data.CollectionResult
import com.example.memgallery.lifeagent.data.LifeAgentDao
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import com.example.memgallery.lifeagent.data.LifeApp
import com.example.memgallery.lifeagent.data.LifeConnectionEntity
import com.example.memgallery.lifeagent.data.LifeItemEntity
import com.example.memgallery.lifeagent.data.LifeMeetingEntity
import com.example.memgallery.lifeagent.data.LifeProposalEntity
import com.example.memgallery.lifeagent.data.LifeRunEntity
import com.example.memgallery.lifeagent.data.LifeTaskEntity
import com.example.memgallery.lifeagent.data.ProposalStatus
import com.example.memgallery.lifeagent.data.runStatsToJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The two things the runner needs from settings. A tiny seam so the whole run is testable on the JVM. */
interface LifeAgentSettings {
    suspend fun composioKey(): String?
    suspend fun setLastRun(at: Long)
}

class SettingsAdapter(private val s: SettingsRepository) : LifeAgentSettings {
    override suspend fun composioKey(): String? = s.composioApiKey()
    override suspend fun setLastRun(at: Long) = s.setLifeAgentLastRun(at)
}

/**
 * One collection run: for each connected app, pull what is new (read only), drop the noise with a reason, turn what
 * is left into proposals, dedupe, and write the run. One app failing records its own lastError and never fails the
 * run. Only one run at a time.
 */
@Singleton
class CollectionRunner(
    private val dao: LifeAgentDao,
    private val api: ComposioApi,
    private val settings: LifeAgentSettings,
    private val ai: ProposalAi?,
    private val bus: CollectionProgressBus,
) {
    @Inject
    constructor(
        dao: LifeAgentDao,
        api: ComposioApi,
        settings: SettingsRepository,
        ai: ProviderProposalAi,
        bus: CollectionProgressBus,
    ) : this(dao, api, SettingsAdapter(settings), ai, bus)

    internal var clock: () -> Long = { System.currentTimeMillis() }
    internal var zone: ZoneId = ZoneId.systemDefault()
    private val mutex = Mutex()

    suspend fun run(): CollectionResult {
        if (!mutex.tryLock()) return skipped("A collection is already running")
        try {
            return doRun()
        } finally {
            mutex.unlock()
        }
    }

    private fun skipped(why: String) = CollectionResult("", "skipped", 0, 0, emptyList(), why)

    private suspend fun doRun(): CollectionResult {
        val started = clock()
        val key = settings.composioKey()
        if (key.isNullOrBlank()) return skipped("Add your Composio key to collect")
        val conns = dao.getConnections().filter { it.connected }.sortedBy { c -> LifeApp.entries.indexOfFirst { it.id == c.id } }
        if (conns.isEmpty()) return skipped("Connect an app first")

        val runId = UUID.randomUUID().toString()
        dao.upsertRun(LifeRunEntity(runId, started, null, "running", runStatsToJson(emptyList(), emptyMap()), 0, 0))
        val progress = LinkedHashMap<String, AppProgress>()
        conns.forEach { c -> progress[c.id] = AppProgress(c.id, c.label, 0, 0, AppState.WAITING, null) }
        bus.update(CollectionProgress(true, started, progress.values.toList()))

        val stats = ArrayList<AppRunStat>()
        val noise = LinkedHashMap<String, Int>()
        var created = 0
        var finished = false
        try {
            val accounts = try { api.listConnectedAccounts() } catch (e: ComposioException) { null }
            val bias = LearningBias(dao.getLearning())
            val tasks = dao.getTasks()
            val ctx = BuildContext(
                now = started, zone = zone,
                openTasks = tasks.filter { !LifeAgentRepository.isClosed(it.status) && !it.doneLocal },
                knownTaskIds = tasks.map { it.id }.toSet(), bias = bias,
            )
            val filter = NoiseFilter(bias)
            val builder = ProposalBuilder(ai)
            val seenProposalFps = HashSet<String>()

            for (conn in conns) {
                val known = LifeApp.from(conn.id)
                val label = displayLabel(conn)
                progress[conn.id] = progress.getValue(conn.id).copy(label = label, state = AppState.RUNNING)
                bus.update(CollectionProgress(true, started, progress.values.toList()))
                val stat = try {
                    // gcal is "googlecalendar" in Composio; an unknown toolkit's id is its own slug.
                    val toolkitSlug = known?.let { ComposioCatalog.toolkit(it) } ?: conn.id
                    val acc = accounts?.firstOrNull { it.active && it.toolkit.equals(toolkitSlug, true) }
                    val accountId = conn.accountId ?: acc?.id
                        ?: throw ComposioException(ComposioErrorKind.NOT_FOUND, "No $label account is connected in Composio")
                    val userId = conn.userId ?: acc?.userId
                    val r = if (known != null) {
                        val first = conn.lastPulledAt <= 0L
                        val since = if (first) started - 7 * DAY else conn.lastPulledAt
                        val sinceArg = if (known == LifeApp.CLICKUP && first) 0L else since
                        val raw = ComposioCatalog.pull(api, known, accountId, userId, sinceArg, started)
                        process(known.id, label, raw, since, ctx, filter, builder, seenProposalFps, started, noise)
                    } else {
                        // Any other connected toolkit: its own read tools, through the generic parser.
                        anyToolkit(conn.id, label, accountId, userId, ctx, filter, builder, seenProposalFps, started, noise)
                    }
                    created += r.proposed
                    dao.upsertConnection(conn.copy(lastPulledAt = started, lastError = null, accountId = accountId, userId = userId))
                    r
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = (e.message ?: "Could not read $label").take(160)
                    Log.w(TAG, "${conn.id} failed: $msg")
                    dao.upsertConnection(conn.copy(lastError = msg))
                    AppRunStat(conn.id, 0, 0, 0, "failed", msg)
                }
                stats += stat
                progress[conn.id] = AppProgress(
                    conn.id, label, stat.found, stat.dropped,
                    if (stat.state == "failed") AppState.FAILED else AppState.DONE, stat.note,
                )
                bus.update(CollectionProgress(true, started, progress.values.toList()))
            }

            val failedAll = stats.isNotEmpty() && stats.all { it.state == "failed" }
            val dropped = stats.sumOf { it.dropped }
            val status = if (failedAll) "failed" else "done"
            dao.upsertRun(LifeRunEntity(runId, started, clock(), status, runStatsToJson(stats, noise), dropped, created))
            if (!failedAll) settings.setLastRun(started)
            finished = true
            bus.update(CollectionProgress(false, started, progress.values.toList()))
            return CollectionResult(runId, status, created, dropped, stats, if (failedAll) stats.firstOrNull()?.note else null)
        } finally {
            if (!finished) {
                // Cancelled or crashed half way: never leave a run "running" for ever.
                withContext(NonCancellable) {
                    dao.upsertRun(LifeRunEntity(runId, started, clock(), "failed", runStatsToJson(stats, noise), stats.sumOf { it.dropped }, created))
                    bus.update(CollectionProgress(false, started, progress.values.toList()))
                }
            }
        }
    }

    private suspend fun process(
        toolkit: String,
        label: String,
        raw: List<RawItem>,
        since: Long,
        ctx: BuildContext,
        filter: NoiseFilter,
        builder: ProposalBuilder,
        seenProposalFps: MutableSet<String>,
        now: Long,
        noise: MutableMap<String, Int>,
    ): AppRunStat {
        // What each app writes locally, besides memories. An unknown toolkit writes nothing locally.
        val app = LifeApp.from(toolkit)
        var candidates = raw
        when (app) {
            LifeApp.GCAL -> {
                val live = raw.filter { !it.status.equals("cancelled", true) && it.startAt != null }
                dao.deleteMeetingsFrom(startOfDay(now))
                dao.upsertMeetings(live.map { LifeMeetingEntity(it.sourceId, it.title, it.startAt!!, it.endAt ?: (it.startAt + 3_600_000L), it.location, it.joinUrl) })
                // Only a booked or changed meeting with other people is a memory; the rest is just on the calendar.
                candidates = raw.filter { !it.status.equals("cancelled", true) && (it.updatedAt ?: it.occurredAt) >= since && (it.attendeeCount >= 2 || (it.updatedAt != null && it.updatedAt > it.occurredAt + 60_000)) }
            }
            LifeApp.CLICKUP -> {
                raw.forEach {
                    dao.upsertRemoteTask(LifeTaskEntity(it.sourceId, "clickup", it.listName, it.title, it.dueAt, it.status ?: "open", false, null, it.url))
                }
            }
            else -> {}
        }

        var found = 0
        var dropped = 0
        fun drop(reason: String, countable: Boolean) {
            if (!countable) return
            dropped++
            noise.merge(reason, 1, Int::plus)
        }

        val countable = { i: RawItem -> i.type != RawType.TASK }
        val seen = HashSet<String>()
        val fresh = ArrayList<RawItem>()
        val known = HashSet<String>()
        candidates.filter { it.type != RawType.TASK }.map { itemFp(it) }.chunked(400).forEach { known += dao.knownItemFingerprints(it) }
        for (item in candidates) {
            found++
            val fp = itemFp(item)
            if (item.type != RawType.TASK) {
                if (!seen.add(fp)) { drop(NoiseFilter.R_DUPLICATE, true); continue }
                if (fp in known) { drop(NoiseFilter.R_KNOWN, true); continue }
            }
            val v = filter.judge(item)
            if (v.drop) { drop(v.reason ?: NoiseFilter.R_NOTHING, countable(item)); continue }
            fresh += item
        }

        val drafts = ArrayList<Pair<RawItem, ProposalDraft>>()
        for (item in fresh) {
            val d = builder.build(item, ctx)
            if (d == null) { drop(NoiseFilter.R_NOTHING, countable(item) && item.type != RawType.EVENT); continue }
            drafts += item to d
        }

        // Remember what was read (not ClickUp tasks: they live in life_tasks), so it is never judged twice.
        dao.insertItems(
            fresh.filter { it.type != RawType.TASK }.map {
                LifeItemEntity(itemFp(it), it.app, it.sourceId, it.type.name.lowercase(), it.title.take(200), it.body.take(500), it.author, it.url, it.occurredAt, now, itemFp(it))
            },
        )

        // Same person plus same thing, or the same source item, never twice.
        val existing = HashSet<String>()
        drafts.map { it.second.fingerprint }.chunked(400).forEach { existing += dao.knownProposalFingerprints(it) }
        val entities = ArrayList<LifeProposalEntity>()
        for ((_, d) in drafts) {
            if (d.fingerprint in existing || !seenProposalFps.add(d.fingerprint)) { drop(NoiseFilter.R_DUPLICATE, d.kind.key != "deadline" || d.app != LifeApp.CLICKUP.id); continue }
            entities += LifeProposalEntity(
                id = UUID.randomUUID().toString(), kind = d.kind.key, title = d.title, detail = d.detail, app = d.app,
                sourceId = d.sourceId, sourceTitle = d.sourceTitle, sourceUrl = d.sourceUrl, personName = d.personName,
                dueAt = d.dueAt, amountCents = d.amountCents, currency = d.currency, confidence = d.confidence,
                status = ProposalStatus.PROPOSED.key, reason = d.reason, fingerprint = d.fingerprint,
                matchedTaskId = d.matchedTaskId, collectedAt = now, decidedAt = null,
            )
        }
        val proposed = if (entities.isEmpty()) 0 else dao.insertProposals(entities).count { it != -1L }
        Log.d(TAG, "$label: ${raw.size} read, $found found, $dropped dropped, $proposed proposed")
        return AppRunStat(toolkit, found, dropped, proposed, "done", null)
    }

    /**
     * Any connected toolkit with no hand-written parser: its own read tools with empty arguments, the generic parser,
     * then the same noise and proposal pipeline. At most [MAX_GENERIC_TOOLS] tools per run, and one tool failing never
     * stops the rest. A toolkit with no read tool at all fails on its own, with a note the Collecting screen shows.
     */
    private suspend fun anyToolkit(
        toolkit: String,
        label: String,
        accountId: String,
        userId: String?,
        ctx: BuildContext,
        filter: NoiseFilter,
        builder: ProposalBuilder,
        seenProposalFps: MutableSet<String>,
        now: Long,
        noise: MutableMap<String, Int>,
    ): AppRunStat {
        val tools = readToolsFor(toolkit).filter { it !in ComposioCatalog.READ_TOOLS }.take(MAX_GENERIC_TOOLS)
        if (tools.isEmpty()) throw ComposioException(ComposioErrorKind.NOT_FOUND, "No read tool found for $label")
        val raw = ArrayList<RawItem>()
        val notes = LinkedHashMap<String, String>()
        var answered = 0
        for (slug in tools) {
            val result = try {
                api.execute(slug, accountId, userId, emptyMap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notes[slug] = "$slug refused: ${(e.message ?: "no answer").take(60)}"
                continue
            }
            if (!result.successful) {
                notes[slug] = "$slug refused: ${(result.error ?: "no answer").take(60)}"
                continue
            }
            answered++
            val got = GenericParser.items(toolkit, slug, result.data, now)
            if (got.isEmpty()) notes[slug] = "$slug returned nothing" else raw += got
        }
        if (answered == 0) {
            throw ComposioException(ComposioErrorKind.TOOL_FAILED, notes.values.firstOrNull() ?: "$label refused every read")
        }
        val stat = process(toolkit, label, raw, now, ctx, filter, builder, seenProposalFps, now, noise)
        return stat.copy(note = notes.values.take(3).joinToString("; ").take(160).ifBlank { null })
    }

    /** The read tools of a toolkit: the cached row, or a fresh look at its catalog. Empty means none found. */
    private suspend fun readToolsFor(toolkit: String): List<String> {
        val cached = dao.getToolkit(toolkit)?.readTools?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        if (!cached.isNullOrEmpty()) return cached
        return try {
            val reads = api.listTools(toolkit, limit = 100).items.map { it.slug }
                .filter { ReadToolRules.isReadTool(it) }.distinct().take(MAX_READ_TOOLS)
            dao.setToolkitReadTools(toolkit, reads.takeIf { it.isNotEmpty() }?.joinToString(","))
            reads
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The name the Collecting screen shows: the connection's own label, else the cached toolkit row, else the slug. */
    private suspend fun displayLabel(conn: LifeConnectionEntity): String {
        val known = LifeApp.from(conn.id)
        if (known != null) return conn.label.ifBlank { known.label }
        val own = conn.label.takeIf { it.isNotBlank() && !it.equals(conn.id, true) }
        val cached = dao.getToolkit(conn.id)?.name?.takeIf { it.isNotBlank() }
        return own ?: cached ?: prettySlug(conn.id)
    }

    private fun prettySlug(slug: String): String = slug.split('_', '-', ' ').filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    private fun itemFp(i: RawItem): String =
        if (i.type == RawType.EVENT) "${i.app}:${i.sourceId}:${i.startAt}" else "${i.app}:${i.sourceId}"

    private fun startOfDay(at: Long): Long =
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        private const val TAG = "LifeAgentRunner"
        private const val DAY = 24L * 60 * 60 * 1000
        /** How many read tools of an unknown toolkit one run calls. */
        private const val MAX_GENERIC_TOOLS = 5
        private const val MAX_READ_TOOLS = 12
    }
}
