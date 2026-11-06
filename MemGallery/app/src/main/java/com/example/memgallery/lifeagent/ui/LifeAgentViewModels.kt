package com.example.memgallery.lifeagent.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.lifeagent.data.LifeAgentControls
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import com.example.memgallery.lifeagent.data.LifeProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Work that must outlive a screen (a collection that keeps running in the background, a decision that is still in its
 * undo window) runs here instead of in viewModelScope.
 */
object LifeAgentUiScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The collection started from the UI, so Cancel can stop it. */
    @Volatile var collectionJob: Job? = null
}

/** A question waiting to be put in the chat input (set by "Ask about", read once by the chat screen). */
object LifeChatBridge {
    val pending = MutableStateFlow<String?>(null)

    /** The numbered sources the question cites ([1], [2]...). The chat shows them as rows under the answer. */
    val sources = MutableStateFlow<List<LifeSourceUi>>(emptyList())

    fun ask(question: String, withSources: List<LifeSourceUi> = emptyList()) {
        sources.value = withSources
        pending.value = question
    }
    fun consume(): String? = pending.value.also { pending.value = null }
}

private const val STATE_WAIT_MS = 5_000L
private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(STATE_WAIT_MS), initial)

private fun ctxFor(context: Context) = LifeCtx(ContextText(context), System.currentTimeMillis())

/** Turns a snooze choice into a time: one hour from now, or 08:00 tomorrow in the user's zone. */
fun snoozeUntil(choice: SnoozeChoice, ctx: LifeCtx): Long = when (choice) {
    SnoozeChoice.OneHour -> ctx.now + 60 * 60 * 1000L
    SnoozeChoice.Tomorrow -> Instant.ofEpochMilli(ctx.now).atZone(ctx.zone).toLocalDate().plusDays(1).atTime(8, 0).atZone(ctx.zone).toInstant().toEpochMilli()
}

// ---- Connections ----

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    private val settings: SettingsRepository,
    private val controls: LifeAgentControls,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val loading = MutableStateFlow(false)
    private val catalogError = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val checking = MutableStateFlow(false)
    private val checkOk = MutableStateFlow<Boolean?>(null)
    private val checkMessage = MutableStateFlow<String?>(null)
    private val projects = MutableStateFlow<List<ProjectRowUi>>(emptyList())
    private val showProjectSheet = MutableStateFlow(false)
    private val connectMessage = MutableStateFlow<String?>(null)
    private val connectUrl = MutableStateFlow<String?>(null)
    private val keyError = MutableStateFlow<String?>(null)
    private val keySaving = MutableStateFlow(false)

    private val prefs = combine(
        settings.composioApiKeyFlow,
        settings.composioKeyKindFlow,
        settings.composioProjectNameFlow,
        settings.lifeAgentEnabledFlow,
        settings.lifeAgentHourFlow,
        settings.lifeAgentMinuteFlow,
    ) { values -> Prefs(values[0] as String?, values[1] as String?, values[2] as String?, values[3] as Boolean, values[4] as Int, values[5] as Int) }

    private val transient = combine(
        combine(busy, loading, catalogError) { b, l, e -> Triple(b, l, e) },
        combine(query, category, connectMessage) { q, c, m -> Triple(q, c, m) },
        combine(checking, checkOk, checkMessage) { c, ok, msg -> Triple(c, ok, msg) },
        combine(projects, showProjectSheet, connectUrl) { p, sh, url -> Triple(p, sh, url) },
        combine(keyError, keySaving) { e, sv -> e to sv },
    ) { a, b, c, d, e -> Transient(a, b, c, d, e.first, e.second) }

    val state: StateFlow<ConnectionsUiState> =
        combine(repo.toolkits, prefs, transient) { toolkits, p, t ->
            buildState(toolkits, p, t)
        }.asState(viewModelScope, ConnectionsUiState(loading = true))

    init {
        viewModelScope.launch { controls.ensureScheduled() }
        refresh(force = false)
    }

    private data class Prefs(
        val key: String?,
        val kind: String?,
        val project: String?,
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
    )

    private data class Transient(
        val busy: Triple<Set<String>, Boolean, String?>,
        val filter: Triple<String, String?, String?>,
        val check: Triple<Boolean, Boolean?, String?>,
        val sheet: Triple<List<ProjectRowUi>, Boolean, String?>,
        val key: String?,
        val keySaving: Boolean,
    )

    private fun buildState(toolkits: List<com.example.memgallery.lifeagent.data.LifeToolkit>, p: Prefs, t: Transient): ConnectionsUiState {
        val ctx = ctxFor(context)
        val rows = toolkits.map { tk ->
            ToolkitRowUi(
                slug = tk.slug,
                name = tk.name,
                description = tk.description,
                logoUrl = tk.logoUrl,
                categories = tk.categories,
                connected = tk.connected,
                lastPulled = tk.lastPulledAt.takeIf { it > 0L }?.let { ctx.clockOrDay(it) },
                error = tk.lastError?.takeIf { it.isNotBlank() },
                busy = tk.slug in t.busy.first,
                known = tk.known,
            )
        }
        val q = t.filter.first.trim()
        val cat = t.filter.second
        val filtered = rows.filter { r ->
            (cat == null || r.categories.any { it.equals(cat, ignoreCase = true) }) &&
                (q.isBlank() || r.name.contains(q, ignoreCase = true) || r.slug.contains(q, ignoreCase = true) ||
                    r.description?.contains(q, ignoreCase = true) == true)
        }
        val cats = rows.flatMap { it.categories }.distinct().sorted().take(12)
        return ConnectionsUiState(
            keySet = !p.key.isNullOrBlank(),
            maskedKey = maskKey(p.key),
            keyKind = p.kind?.let { runCatching { com.example.memgallery.lifeagent.composio.KeyKind.valueOf(it) }.getOrNull() }
                ?.let { if (it == com.example.memgallery.lifeagent.composio.KeyKind.USER) context.getString(R.string.la_key_kind_user) else context.getString(R.string.la_key_kind_project) },
            projectName = p.project,
            checking = t.check.first,
            keyCheckOk = t.check.second,
            keyCheckMessage = t.check.third,
            projects = t.sheet.first,
            showProjectSheet = t.sheet.second,
            query = t.filter.first,
            categories = cats,
            selectedCategory = cat,
            connected = filtered.filter { it.connected },
            others = filtered.filter { !it.connected },
            loading = t.busy.second,
            catalogError = t.busy.third,
            connectMessage = t.filter.third,
            connectUrl = t.sheet.third,
            keyError = t.key,
            keySaving = t.keySaving,
            collectionEnabled = p.enabled,
            hour = p.hour,
            minute = p.minute,
        )
    }

    fun setQuery(text: String) { query.value = text }
    fun selectCategory(value: String?) { category.value = if (category.value == value) null else value }

    /** Pulls the catalog from Composio. The first open does this once; the cache keeps the screen instant after. */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            loading.value = true
            catalogError.value = null
            try {
                repo.syncToolkits(force = force)
                if (force) runCatching { repo.syncConnections() }
            } catch (e: Exception) {
                catalogError.value = (e.message ?: context.getString(R.string.la_catalog_failed)).take(180)
            } finally {
                loading.value = false
            }
        }
    }

    fun saveKey(key: String) {
        val k = key.trim()
        if (k.length < 8) { keyError.value = context.getString(R.string.la_key_too_short); return }
        keyError.value = null
        viewModelScope.launch {
            keySaving.value = true
            try {
                controls.saveKey(k)
                testKey()
            } finally {
                keySaving.value = false
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            controls.clearKey()
            checkOk.value = null
            checkMessage.value = null
            projects.value = emptyList()
        }
    }

    /** Asks Composio what it thinks of the key and shows its own words. Also names the project for a personal key. */
    fun testKey() {
        viewModelScope.launch {
            checking.value = true
            checkOk.value = null
            checkMessage.value = null
            try {
                val check = repo.checkKey()
                checkOk.value = check.ok
                checkMessage.value = if (check.ok) {
                    buildString {
                        append(context.getString(R.string.la_key_ok))
                        check.projectName?.let { append(" · "); append(it) }
                        check.orgName?.let { append(" · "); append(it) }
                    }
                } else {
                    check.error
                }
                if (check.ok) {
                    refresh(force = true)
                    val list = repo.projects()
                    projects.value = list.map { ProjectRowUi(it.id, it.name, it.orgId) }
                    showProjectSheet.value = list.size > 1
                }
            } catch (e: Exception) {
                checkOk.value = false
                checkMessage.value = e.message?.take(180)
            } finally {
                checking.value = false
            }
        }
    }

    fun chooseProject(row: ProjectRowUi) {
        viewModelScope.launch {
            repo.chooseProject(com.example.memgallery.lifeagent.composio.ComposioProject(row.id, row.name, row.orgId))
            showProjectSheet.value = false
            testKey()
        }
    }

    fun dismissProjectSheet() { showProjectSheet.value = false }

    /** Connect any toolkit: already connected in Composio marks it here, otherwise it makes a link for the browser. */
    fun connect(row: ToolkitRowUi) {
        viewModelScope.launch {
            busy.update { it + row.slug }
            connectMessage.value = null
            connectUrl.value = null
            try {
                when (val outcome = repo.connectToolkit(row.slug, row.name)) {
                    is com.example.memgallery.lifeagent.data.ConnectOutcome.Connected ->
                        connectMessage.value = context.getString(R.string.la_connected_named, row.name)
                    is com.example.memgallery.lifeagent.data.ConnectOutcome.Browser -> {
                        connectUrl.value = outcome.url
                        connectMessage.value = context.getString(R.string.la_finish_in_browser, row.name)
                    }
                    is com.example.memgallery.lifeagent.data.ConnectOutcome.Failed ->
                        connectMessage.value = outcome.message
                }
            } finally {
                busy.update { it - row.slug }
            }
        }
    }

    /** Called when he comes back from the browser: pulls the connected accounts again and marks what landed. */
    fun connectFinished(row: ToolkitRowUi) {
        viewModelScope.launch {
            busy.update { it + row.slug }
            try {
                repo.syncConnections()
                val now = repo.connectionFor(row.slug)
                connectMessage.value = if (now?.connected == true) {
                    context.getString(R.string.la_connected_named, row.name)
                } else {
                    context.getString(R.string.la_still_not_connected, row.name)
                }
            } finally {
                busy.update { it - row.slug }
            }
        }
    }

    fun disconnect(row: ToolkitRowUi) {
        viewModelScope.launch {
            busy.update { it + row.slug }
            try { repo.disconnectToolkit(row.slug) } finally { busy.update { it - row.slug } }
        }
    }

    fun setEnabled(on: Boolean) { viewModelScope.launch { controls.setEnabled(on) } }
    fun setTime(hour: Int, minute: Int) { viewModelScope.launch { controls.setTime(hour, minute) } }
    fun collectNow() = startCollection(repo)
}

/** Starts a collection that survives the screen, once at a time. */
fun startCollection(repo: LifeAgentRepository) {
    if (LifeAgentUiScope.collectionJob?.isActive == true) return
    LifeAgentUiScope.collectionJob = LifeAgentUiScope.scope.launch { repo.runCollection() }
}

// ---- Collecting ----

@HiltViewModel
class CollectingViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val sawRunning = MutableStateFlow(false)

    val state: StateFlow<CollectingUiState> = combine(repo.collectionProgress, repo.latestRun, repo.connections, sawRunning) { prog, run, conns, saw ->
        if (prog.running && !saw) sawRunning.value = true
        mapCollecting(prog, run, saw, conns.filter { it.connected }.mapNotNull { LifeAppId.from(it.id) }, ctxFor(context))
    }.asState(viewModelScope, CollectingUiState())

    fun start() = startCollection(repo)
    fun cancel() { LifeAgentUiScope.collectionJob?.cancel() }
}

// ---- Review ----

/**
 * The swipe deck's decisions. The last decision waits [undoWindowMs] so it can be undone; the next decision, leaving
 * the screen or Approve all writes it. Plain class so it can be tested with a fake [write] and virtual time.
 */
class DeckSession(
    private val scope: CoroutineScope,
    private val undoWindowMs: Long,
    private val write: suspend (id: String, keep: Boolean) -> Unit,
) {
    data class Decision(val id: String, val keep: Boolean)

    val pending = MutableStateFlow<Decision?>(null)
    val committedKept = MutableStateFlow(0)
    val committedTotal = MutableStateFlow(0)
    private var commitJob: Job? = null

    fun decide(id: String, keep: Boolean) {
        commitPending()
        pending.value = Decision(id, keep)
        commitJob = scope.launch {
            delay(undoWindowMs)
            commitPending()
        }
    }

    /** Brings the last decision back to the top of the deck, if it is still in its undo window. */
    fun undo() {
        commitJob?.cancel()
        pending.value = null
    }

    /** Writes the decision that is still waiting. */
    fun commitPending() {
        commitJob?.cancel()
        val d = pending.value ?: return
        pending.value = null
        committedTotal.update { it + 1 }
        if (d.keep) committedKept.update { it + 1 }
        scope.launch { write(d.id, d.keep) }
    }

    /** Applies a pick to every card that is left: (id, keep). */
    fun approve(picks: List<Pair<String, Boolean>>) {
        commitPending()
        committedTotal.update { it + picks.size }
        committedKept.update { it + picks.count { p -> p.second } }
        scope.launch { picks.forEach { (id, keep) -> write(id, keep) } }
    }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val session = DeckSession(LifeAgentUiScope.scope, UNDO_WINDOW_MS) { id, keep -> if (keep) repo.keep(id) else repo.drop(id) }

    private val deck = combine(repo.proposals, repo.tasks, repo.people, session.pending) { proposals, tasks, people, pend ->
        Deck(proposals, tasks.associateBy { it.id }, people, pend)
    }
    private class Deck(
        val proposals: List<LifeProposal>,
        val tasks: Map<String, com.example.memgallery.lifeagent.data.LifeTask>,
        val people: List<com.example.memgallery.lifeagent.data.LifePerson>,
        val pending: DeckSession.Decision?,
    )

    private val prefs = combine(settings.lifeAgentEnabledFlow, settings.lifeAgentHourFlow, settings.lifeAgentMinuteFlow) { e, h, m -> Triple(e, h, m) }
    private val counts = combine(session.committedKept, session.committedTotal) { k, t -> k to t }

    val state: StateFlow<ReviewUiState> = combine(deck, counts, repo.learned, repo.latestRun, prefs) { d, c, learned, run, p ->
        val ctx = ctxFor(context)
        val remaining = d.proposals.filter { it.id != d.pending?.id }.map { mapCard(it, d.tasks, d.people, ctx) }
        val (rows, droppedTotal) = mapLearned(learned, ctx)
        mapReview(
            remaining = remaining, committedTotal = c.second, committedKept = c.first, pendingKeep = d.pending?.keep,
            collectedLabel = run?.let { ctx.clock(it.finishedAt ?: it.startedAt) }.orEmpty(),
            learned = rows, droppedTotal = droppedTotal, next = nextCollectionLabel(p.first, p.second, p.third, ctx),
        )
    }.asState(viewModelScope, ReviewUiState())

    fun keep(id: String) = session.decide(id, true)
    fun drop(id: String) = session.decide(id, false)
    fun undo() = session.undo()
    fun commitPending() = session.commitPending()
    fun approveAll() = session.approve(state.value.remaining.map { it.id to it.suggestKeep })

    /**
     * Ask about the card in the chat. The card's own source becomes [1] through askContext, so the answer can cite it
     * and the chat shows the source row, exactly like a person page.
     */
    fun ask(id: String, onReady: () -> Unit) {
        LifeAgentUiScope.scope.launch {
            val proposal = runCatching { repo.allProposals.first().firstOrNull { it.id == id } }.getOrNull()
            val facts = runCatching { repo.askContext(id) }.getOrDefault("")
            val source = proposal?.let {
                LifeSourceUi(1, it.sourceTitle.ifBlank { it.title }, LifeAppId.from(it.app), it.app, it.personName.orEmpty(), it.sourceUrl)
            }
            val question = if (facts.isBlank()) context.getString(R.string.la_ask_card_plain)
            else context.getString(R.string.la_ask_card, facts)
            LifeChatBridge.ask(question, listOfNotNull(source))
            onReady()
        }
    }

    override fun onCleared() {
        // The undo window ends with the screen: write what is waiting.
        session.commitPending()
        super.onCleared()
    }

    companion object { const val UNDO_WINDOW_MS = 6_000L }
}

/** The review state from the cards left and the decisions made so far. */
fun mapReview(
    remaining: List<ReviewCardUi>, committedTotal: Int, committedKept: Int, pendingKeep: Boolean?,
    collectedLabel: String, learned: List<LearnedCountUi>, droppedTotal: Int, next: String,
): ReviewUiState {
    val decided = committedTotal + if (pendingKeep != null) 1 else 0
    val kept = committedKept + if (pendingKeep == true) 1 else 0
    return ReviewUiState(
        remaining = remaining, decided = decided, total = decided + remaining.size, keptSoFar = kept,
        collectedLabel = collectedLabel, canUndo = pendingKeep != null, learned = learned, droppedTotal = droppedTotal,
        nextCollectionLabel = next,
    )
}

// ---- People ----

@HiltViewModel
class PeopleViewModel @Inject constructor(
    repo: LifeAgentRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(PeopleSort.Recent)

    val state: StateFlow<PeopleUiState> = combine(repo.people, repo.allProposals, query, sort) { people, all, q, s ->
        val kept = all.filter { it.status == com.example.memgallery.lifeagent.data.ProposalStatus.KEPT }
        PeopleUiState(mapPeople(people, kept, ctxFor(context)), q, s)
    }.asState(viewModelScope, PeopleUiState())

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: PeopleSort) { sort.value = s }
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val name: String = savedState.get<String>("name").orEmpty()

    val state: StateFlow<PersonUiState> = combine(repo.person(name), repo.tasks) { detail, tasks ->
        mapPerson(detail, tasks.associateBy { it.id }, ctxFor(context))
    }.asState(viewModelScope, PersonUiState())

    fun tick(taskId: String, done: Boolean) { viewModelScope.launch { if (done) repo.markDone(taskId) else repo.markUndone(taskId) } }
    fun snooze(taskId: String, choice: SnoozeChoice) { viewModelScope.launch { repo.snooze(taskId, snoozeUntil(choice, ctxFor(context))) } }

    /**
     * Opens a chat question about this person. The facts come from the repository's askContext (one paragraph per
     * item) and are numbered, so the answer can cite them as [1], [2] and the chat shows each as a source row.
     */
    fun ask(onReady: () -> Unit) {
        viewModelScope.launch {
            val detail = repo.person(name).first()
            val items = (detail.youOwe + detail.owedToYou + detail.recent).distinctBy { it.id }.take(4)
            val sources = items.mapIndexed { i, p ->
                LifeSourceUi(i + 1, p.sourceTitle.ifBlank { p.title }, LifeAppId.from(p.app), p.app, p.personName.orEmpty(), p.sourceUrl)
            }
            val facts = items.mapIndexed { i, p -> "[${i + 1}] ${repo.askContext(p.id)}" }.joinToString(" ")
            val q = if (items.isEmpty()) context.getString(R.string.la_ask_person, name)
            else context.getString(R.string.la_ask_person_facts, name, facts)
            LifeChatBridge.ask(q, sources)
            onReady()
        }
    }
}

// ---- Daily report ----

@HiltViewModel
class DailyReportViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<DailyReportUiState> = combine(repo.dailyReport(), repo.allProposals, repo.connections) { report, all, conns ->
        mapReport(report, all, conns.any { it.connected }, ctxFor(context))
    }.asState(viewModelScope, DailyReportUiState())

    fun tick(taskId: String, done: Boolean) { viewModelScope.launch { if (done) repo.markDone(taskId) else repo.markUndone(taskId) } }
    fun snooze(taskId: String, choice: SnoozeChoice) { viewModelScope.launch { repo.snooze(taskId, snoozeUntil(choice, ctxFor(context))) } }
    fun refresh() = startCollection(repo)

    /** Number of proposed memories waiting, for the Home entry. */
    val toReview: StateFlow<Int> = repo.proposedCount.asState(viewModelScope, 0)
}

/** The small state the Home entry card needs. */
@HiltViewModel
class LifeEntryViewModel @Inject constructor(
    repo: LifeAgentRepository,
) : ViewModel() {
    val toReview: StateFlow<Int> = repo.proposedCount.asState(viewModelScope, 0)
    val anyConnected: StateFlow<Boolean> = repo.connections.map { c -> c.any { it.connected } }.asState(viewModelScope, false)
}
