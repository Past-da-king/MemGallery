package com.example.memgallery.lifeagent.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
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
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val busy = MutableStateFlow<Set<LifeAppId>>(emptySet())
    private val keyError = MutableStateFlow<String?>(null)
    private val keySaving = MutableStateFlow(false)

    private val prefs = combine(settings.composioApiKeyFlow, settings.lifeAgentEnabledFlow, settings.lifeAgentHourFlow, settings.lifeAgentMinuteFlow) { k, e, h, m -> Prefs(k, e, h, m) }
    private val transient = combine(busy, keyError, keySaving) { b, e, s -> Triple(b, e, s) }

    val state: StateFlow<ConnectionsUiState> = combine(repo.connections, prefs, transient) { conns, p, t ->
        mapConnections(conns, p.key, p.enabled, p.hour, p.minute, t.first, t.second, t.third, ctxFor(context))
    }.asState(viewModelScope, ConnectionsUiState())

    private data class Prefs(val key: String?, val enabled: Boolean, val hour: Int, val minute: Int)

    fun saveKey(key: String) {
        val k = key.trim()
        if (k.length < 8) { keyError.value = context.getString(R.string.la_key_too_short); return }
        keyError.value = null
        viewModelScope.launch {
            keySaving.value = true
            try { settings.saveComposioApiKey(k) } finally { keySaving.value = false }
        }
    }

    fun connect(app: LifeAppId) = launchBusy(app) { repo.connect(app.id) }
    fun disconnect(app: LifeAppId) = launchBusy(app) { repo.disconnect(app.id) }

    private fun launchBusy(app: LifeAppId, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.update { it + app }
            try { block() } finally { busy.update { it - app } }
        }
    }

    fun setEnabled(on: Boolean) { viewModelScope.launch { settings.setLifeAgentEnabled(on) } }
    fun setTime(hour: Int, minute: Int) { viewModelScope.launch { settings.setLifeAgentTime(hour, minute) } }
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

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: LifeAgentRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private data class Decision(val id: String, val keep: Boolean)

    /** The last decision, still in its undo window. Everything before it is already written. */
    private val pending = MutableStateFlow<Decision?>(null)
    private val committedKept = MutableStateFlow(0)
    private val committedTotal = MutableStateFlow(0)
    private var commitJob: Job? = null

    private val deck = combine(repo.proposals, repo.tasks, repo.people, pending) { proposals, tasks, people, pend ->
        Quad(proposals, tasks, people, pend)
    }
    private data class Quad(val proposals: List<LifeProposal>, val tasks: List<com.example.memgallery.lifeagent.data.LifeTask>, val people: List<com.example.memgallery.lifeagent.data.LifePerson>, val pending: Decision?)

    private val prefs = combine(settings.lifeAgentEnabledFlow, settings.lifeAgentHourFlow, settings.lifeAgentMinuteFlow) { e, h, m -> Triple(e, h, m) }
    private val counts = combine(committedKept, committedTotal) { k, t -> k to t }

    val state: StateFlow<ReviewUiState> = combine(deck, counts, repo.learned, repo.latestRun, prefs) { d, c, learned, run, p ->
        val ctx = ctxFor(context)
        val remainingModels = d.proposals.filter { it.id != d.pending?.id }
        val taskMap = d.tasks.associateBy { it.id }
        val remaining = remainingModels.map { mapCard(it, taskMap, d.people, ctx) }
        val pendingCount = if (d.pending != null) 1 else 0
        val decided = c.second + pendingCount
        val kept = c.first + if (d.pending?.keep == true) 1 else 0
        val (rows, droppedTotal) = mapLearned(learned, ctx)
        ReviewUiState(
            remaining = remaining, decided = decided, total = decided + remaining.size, keptSoFar = kept,
            collectedLabel = run?.let { ctx.clock(it.finishedAt ?: it.startedAt) }.orEmpty(),
            canUndo = d.pending != null, learned = rows, droppedTotal = droppedTotal,
            nextCollectionLabel = nextCollectionLabel(p.first, p.second, p.third, ctx),
        )
    }.asState(viewModelScope, ReviewUiState())

    fun keep(id: String) = decide(id, true)
    fun drop(id: String) = decide(id, false)

    private fun decide(id: String, keep: Boolean) {
        commitPending()
        pending.value = Decision(id, keep)
        commitJob = LifeAgentUiScope.scope.launch {
            delay(UNDO_WINDOW_MS)
            commitPending()
        }
    }

    /** Brings the last decision back to the top of the deck, if it is still in its undo window. */
    fun undo() {
        commitJob?.cancel()
        pending.value = null
    }

    /** Writes the decision that is still waiting. Called when the next card is decided, and when the screen is left. */
    fun commitPending() {
        commitJob?.cancel()
        val d = pending.getAndSet(null) ?: return
        committedTotal.update { it + 1 }
        if (d.keep) committedKept.update { it + 1 }
        LifeAgentUiScope.scope.launch { if (d.keep) repo.keep(d.id) else repo.drop(d.id) }
    }

    /** Applies MemGallery's own pick to every card that is left. */
    fun approveAll() {
        commitPending()
        val cards = state.value.remaining
        committedTotal.update { it + cards.size }
        committedKept.update { it + cards.count { c -> c.suggestKeep } }
        LifeAgentUiScope.scope.launch {
            cards.forEach { if (it.suggestKeep) repo.keep(it.id) else repo.drop(it.id) }
        }
    }

    override fun onCleared() {
        // The undo window ends with the screen: write what is waiting.
        val d = pending.value
        if (d != null) LifeAgentUiScope.scope.launch { if (d.keep) repo.keep(d.id) else repo.drop(d.id) }
        super.onCleared()
    }

    companion object { const val UNDO_WINDOW_MS = 6_000L }
}

private fun <T> MutableStateFlow<T>.getAndSet(v: T): T { val old = value; value = v; return old }

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
