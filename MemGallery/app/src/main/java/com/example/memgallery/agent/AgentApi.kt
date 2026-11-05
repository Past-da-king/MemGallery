package com.example.memgallery.agent

import com.example.memgallery.agent.model.AgentAsk
import com.example.memgallery.agent.model.AgentChat
import com.example.memgallery.agent.model.AgentChatMessage
import com.example.memgallery.agent.model.AgentClient
import com.example.memgallery.agent.model.AgentEvent
import com.example.memgallery.agent.model.AgentEventType
import com.example.memgallery.agent.model.AgentMemory
import com.example.memgallery.agent.model.AgentMeta
import com.example.memgallery.agent.model.AgentProject
import com.example.memgallery.agent.model.AgentTask
import com.example.memgallery.agent.model.AgentWebhook
import com.example.memgallery.agent.model.AskRequest
import com.example.memgallery.agent.model.ChatQuery
import com.example.memgallery.agent.model.CreateMemory
import com.example.memgallery.agent.model.CreateProject
import com.example.memgallery.agent.model.CreateTask
import com.example.memgallery.agent.model.Digest
import com.example.memgallery.agent.model.MediaBlob
import com.example.memgallery.agent.model.MemoryPatch
import com.example.memgallery.agent.model.MemoryQuery
import com.example.memgallery.agent.model.NotificationRef
import com.example.memgallery.agent.model.NotifyRequest
import com.example.memgallery.agent.model.Page
import com.example.memgallery.agent.model.ProjectPatch
import com.example.memgallery.agent.model.ProjectQuery
import com.example.memgallery.agent.model.RegisterWebhook
import com.example.memgallery.agent.model.SearchQuery
import com.example.memgallery.agent.model.SearchResult
import com.example.memgallery.agent.model.TaskPatch
import com.example.memgallery.agent.model.TaskQuery
import kotlinx.coroutines.flow.Flow

/**
 * Everything MemGallery can do, as far as an outside agent is concerned.
 *
 * This is the whole contract. The HTTP server, the hub's outbox drain and the in-app
 * chat assistant are all just callers; none of them holds business logic of its own, and
 * **if a capability isn't declared here it isn't reachable from anywhere.** That rule is
 * the only thing keeping "expose MemGallery to agents" from turning into three divergent
 * half-APIs.
 *
 * Deliberately transport-agnostic: no Ktor types, no JSON, no Android. It knows nothing
 * about MCP, and nothing at all about A.O.S — A.O.S is simply one client that happens to
 * hold a token.
 *
 * Every mutating call takes the authenticated [AgentClient] so that writes are attributable
 * ([AgentClient.sourceTag]), quotas are enforceable, and the in-app audit log can name who
 * did what. Scope checks happen here, not in the transport — a second transport must not be
 * able to forget them.
 *
 * Implementations throw [com.example.memgallery.agent.model.AgentException] for anything a
 * caller could reasonably have caused; the transport maps its code to a status.
 */
interface AgentApi {

    // -----------------------------------------------------------------------
    // Orientation
    // -----------------------------------------------------------------------

    /** Version, device identity and the capability list, so clients degrade instead of guessing. */
    suspend fun meta(): AgentMeta

    /**
     * One round trip for "what is he up to right now" — overdue, today, this week, what's
     * awaiting his approval, what was captured recently. Cheap enough to mirror to the hub,
     * and the thing an agent should read before deciding whether to interrupt.
     */
    suspend fun digest(client: AgentClient): Digest

    suspend fun search(query: SearchQuery, client: AgentClient): SearchResult

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    suspend fun listTasks(query: TaskQuery, client: AgentClient): Page<AgentTask>

    suspend fun getTask(id: Int, client: AgentClient): AgentTask?

    /** Honours [CreateTask.externalId] as an upsert key, so a retry can't double-create. */
    suspend fun createTask(request: CreateTask, client: AgentClient): AgentTask

    suspend fun updateTask(id: Int, patch: TaskPatch, client: AgentClient): AgentTask

    suspend fun completeTask(id: Int, client: AgentClient): AgentTask

    suspend fun deleteTask(id: Int, client: AgentClient)

    /** Moves a proposed task into the live list. Emits `task.approved`. */
    suspend fun approveTask(id: Int, client: AgentClient): AgentTask

    /** Discards a proposed task. Emits `task.rejected`. */
    suspend fun rejectTask(id: Int, client: AgentClient)

    /** The operator's approval inbox — tasks proposed by AI or by agents, not yet accepted. */
    suspend fun listPendingApproval(client: AgentClient): List<AgentTask>

    // -----------------------------------------------------------------------
    // Memories
    // -----------------------------------------------------------------------

    suspend fun listMemories(query: MemoryQuery, client: AgentClient): Page<AgentMemory>

    suspend fun getMemory(id: Int, client: AgentClient): AgentMemory?

    /**
     * Requires `media:read`, which is not in the default scope set. Served only by the
     * device — the hub mirror never carries bytes.
     */
    suspend fun getMemoryMedia(id: Int, index: Int, client: AgentClient): MediaBlob?

    /**
     * Stores the memory and, if [CreateMemory.process] is set and the client is within its
     * daily AI quota, runs the normal processing pipeline. Returns as soon as it is stored;
     * processing completion arrives as a `memory.processed` event.
     */
    suspend fun createMemory(request: CreateMemory, client: AgentClient): AgentMemory

    suspend fun updateMemory(id: Int, patch: MemoryPatch, client: AgentClient): AgentMemory

    suspend fun deleteMemory(id: Int, client: AgentClient)

    // -----------------------------------------------------------------------
    // Projects (collections)
    // -----------------------------------------------------------------------

    suspend fun listProjects(query: ProjectQuery, client: AgentClient): Page<AgentProject>

    suspend fun getProject(id: Int, client: AgentClient): AgentProject?

    suspend fun createProject(request: CreateProject, client: AgentClient): AgentProject

    suspend fun updateProject(id: Int, patch: ProjectPatch, client: AgentClient): AgentProject

    suspend fun deleteProject(id: Int, client: AgentClient)

    suspend fun addMemoryToProject(projectId: Int, memoryId: Int, client: AgentClient)

    suspend fun removeMemoryFromProject(projectId: Int, memoryId: Int, client: AgentClient)

    // -----------------------------------------------------------------------
    // Chats (read-only; the in-app assistant owns writes)
    // -----------------------------------------------------------------------

    suspend fun listChats(query: ChatQuery, client: AgentClient): Page<AgentChat>

    suspend fun getChatMessages(chatId: Int, client: AgentClient): List<AgentChatMessage>

    // -----------------------------------------------------------------------
    // Operator attention
    // -----------------------------------------------------------------------

    /** Fire-and-forget notification on the dedicated agent channel. */
    suspend fun notify(request: NotifyRequest, client: AgentClient): NotificationRef

    /**
     * Puts a question to the operator and returns immediately with a pending [AgentAsk].
     * Answers come back via [pollAsk], the `ask.answered` event, or a webhook.
     */
    suspend fun ask(request: AskRequest, client: AgentClient): AgentAsk

    /**
     * Long-poll for an answer. Returns as soon as the ask settles, or after [waitSec]
     * with the ask still pending. [waitSec] of 0 is a plain read.
     */
    suspend fun pollAsk(id: String, waitSec: Int, client: AgentClient): AgentAsk

    /** Recorded from a notification action, a RemoteInput reply, or the in-app Agent Inbox. */
    suspend fun answerAsk(id: String, answer: String): AgentAsk

    suspend fun listPendingAsks(client: AgentClient): List<AgentAsk>

    // -----------------------------------------------------------------------
    // Streaming and delivery
    // -----------------------------------------------------------------------

    /**
     * Live event stream. [afterId] replays anything the caller missed while disconnected,
     * which is what makes a dropped SSE connection recoverable rather than a silent gap.
     */
    fun events(types: Set<AgentEventType>, afterId: Long = 0L): Flow<AgentEvent>

    suspend fun registerWebhook(request: RegisterWebhook, client: AgentClient): AgentWebhook

    suspend fun listWebhooks(client: AgentClient): List<AgentWebhook>

    suspend fun deleteWebhook(id: String, client: AgentClient)
}
