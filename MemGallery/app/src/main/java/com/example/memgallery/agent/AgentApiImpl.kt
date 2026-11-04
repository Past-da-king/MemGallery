package com.example.memgallery.agent

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.memgallery.agent.model.*
import com.example.memgallery.data.local.dao.AgentAskDao
import com.example.memgallery.data.local.dao.AgentAuditDao
import com.example.memgallery.data.local.dao.AgentClientDao
import com.example.memgallery.data.local.dao.AgentOutboxDao
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.AgentAskEntity
import com.example.memgallery.data.local.entity.AgentAuditEntity
import com.example.memgallery.data.local.entity.AgentWebhookEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentApiImpl"

/**
 * The one implementation of [AgentApi].
 *
 * Scope checks live here rather than in the HTTP layer on purpose: a second transport
 * (the hub's outbox drain, an in-app caller) must not be able to forget them. Same for
 * event emission — every mutation emits exactly once, from here, so no path out of the
 * device can silently skip the outbox.
 */
@Singleton
class AgentApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val taskDao: TaskDao,
    private val collectionDao: CollectionDao,
    private val chatDao: ChatDao,
    private val clientDao: AgentClientDao,
    private val askDao: AgentAskDao,
    private val outboxDao: AgentOutboxDao,
    private val auditDao: AgentAuditDao,
    private val memoryRepository: MemoryRepository,
    private val events: AgentEventBus,
    private val notifier: AgentNotifier,
    private val settings: AgentBridgeSettings
) : AgentApi {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dateFormat.format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun datePlusDays(days: Int): String = dateFormat.format(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time
    )

    private fun String.fold(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()

    private suspend fun audit(
        client: AgentClient,
        action: String,
        entityType: String,
        entityId: String?,
        detail: String? = null
    ) {
        if (client.id == "internal") return
        try {
            auditDao.insert(
                AgentAuditEntity(
                    clientId = client.id,
                    clientName = client.name,
                    action = action,
                    entityType = entityType,
                    entityId = entityId,
                    detail = detail,
                    at = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Audit write failed", e)
        }
    }

    /**
     * Applies the cursor and slices one page. Cursors carry a fingerprint of the query
     * they were issued for; replaying one against different filters is an error rather
     * than silently skewed results.
     */
    private fun <T> paginate(all: List<T>, limit: Int, cursor: String?, fingerprint: Int): Page<T> {
        val decoded = Cursor.decode(cursor)
        if (cursor != null && decoded == null) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "Malformed cursor.")
        }
        if (decoded != null && decoded.queryHash != fingerprint) {
            throw AgentException(
                AgentErrorCode.INVALID_REQUEST,
                "This cursor was issued for a different filter set. Restart the listing."
            )
        }
        val offset = decoded?.offset ?: 0
        if (offset >= all.size) return Page(emptyList(), null, all.size)
        val slice = all.drop(offset).take(limit)
        val nextOffset = offset + slice.size
        val next = if (nextOffset < all.size) Cursor(nextOffset, fingerprint).encode() else null
        return Page(slice, next, all.size)
    }

    // =======================================================================
    // Orientation
    // =======================================================================

    override suspend fun meta(): AgentMeta {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val aiAllowed = settings.aiProcessingAllowed.first()
        val capabilities = buildList {
            add(AgentMeta.CAP_MEDIA)
            add(AgentMeta.CAP_EVENTS)
            add(AgentMeta.CAP_WEBHOOKS)
            add(AgentMeta.CAP_ASK)
            add(AgentMeta.CAP_NOTIFY)
            add(AgentMeta.CAP_MEMORY_CREATE)
            if (aiAllowed) add(AgentMeta.CAP_AI_PROCESSING)
            if (settings.mirrorEnabled.first()) add(AgentMeta.CAP_MIRROR)
        }
        return AgentMeta(
            appVersion = appVersion,
            deviceName = settings.deviceName.first(),
            deviceId = settings.deviceId(),
            capabilities = capabilities,
            scopes = AgentScope.entries.map { it.wire },
            serverTime = System.currentTimeMillis(),
            timeZone = TimeZone.getDefault().id
        )
    }

    override suspend fun digest(client: AgentClient): Digest {
        client.require(AgentScope.READ)

        val today = today()
        val weekEnd = datePlusDays(7)
        val all = taskDao.getAllTasksRaw().filter { it.isApproved }
        val open = all.filter { !it.isCompleted }

        fun List<TaskEntity>.toDto() = map { AgentMappers.toAgentTask(it) }

        val overdue = open.filter { it.dueDate != null && it.dueDate < today }
            .sortedBy { it.dueDate }.toDto()
        val dueToday = open.filter { it.dueDate == today }
            .sortedBy { it.dueTime ?: "99:99" }.toDto()
        val dueThisWeek = open.filter { it.dueDate != null && it.dueDate > today && it.dueDate <= weekEnd }
            .sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: "99:99" })).toDto()
        val eventsToday = open.filter { it.dueDate == today && it.type.equals("EVENT", true) }
            .sortedBy { it.dueTime ?: "99:99" }.toDto()

        val recent = memoryDao.getAllMemoriesOnce()
            .filter { !it.isHidden }
            .take(10)
            .map { AgentMappers.toAgentMemory(it) }

        val projects = collectionDao.getAllCollectionsRaw()
            .filter { !it.archived && !it.status.equals("DONE", true) }
            .sortedBy { it.sortOrder }
            .map { AgentMappers.toAgentProject(it) }

        val startOfDay = startOfToday()

        return Digest(
            generatedAt = System.currentTimeMillis(),
            today = today,
            timeZone = TimeZone.getDefault().id,
            overdue = overdue,
            dueToday = dueToday,
            dueThisWeek = dueThisWeek,
            eventsToday = eventsToday,
            awaitingApproval = AwaitingApproval(
                tasks = taskDao.countUnapproved(),
                asks = askDao.countPending()
            ),
            recentMemories = recent,
            activeProjects = projects,
            processing = ProcessingCounts(
                pending = memoryDao.countByStatus("PENDING"),
                processing = memoryDao.countByStatus("PROCESSING"),
                failed = memoryDao.countByStatus("FAILED")
            ),
            activity = ActivitySummary(
                memoriesToday = memoryDao.countCreatedSince(startOfDay),
                tasksCompletedToday = taskDao.countCompletedSince(startOfDay),
                streakDays = computeStreak()
            )
        )
    }

    /** Consecutive days back from today with at least one memory. Stops at the first gap. */
    private suspend fun computeStreak(): Int {
        val stamps = memoryDao.getAllMemoriesOnce().map { it.creationTimestamp }
        if (stamps.isEmpty()) return 0
        val days = stamps.map { dateFormat.format(Date(it)) }.toSet()
        var streak = 0
        val cal = Calendar.getInstance()
        while (streak < 3650) {
            val day = dateFormat.format(cal.time)
            if (day !in days) {
                // Today not yet captured doesn't break a streak that's alive as of yesterday.
                if (streak == 0 && day == today()) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    override suspend fun search(query: SearchQuery, client: AgentClient): SearchResult {
        client.require(AgentScope.READ)
        val q = query.text.fold()
        if (q.isBlank()) throw AgentException(AgentErrorCode.INVALID_REQUEST, "'q' must not be empty.")
        val n = query.limitPerEntity.coerceIn(1, 50)

        val memories = if (SearchEntity.MEMORIES in query.scope) {
            memoryDao.getAllMemoriesOnce().filter { !it.isHidden && it.matches(q) }
                .take(n).map { AgentMappers.toAgentMemory(it) }
        } else emptyList()

        val tasks = if (SearchEntity.TASKS in query.scope) {
            taskDao.getAllTasksRaw().filter {
                it.isApproved && (it.title.fold().contains(q) || it.description.fold().contains(q))
            }.take(n).map { AgentMappers.toAgentTask(it) }
        } else emptyList()

        val projects = if (SearchEntity.PROJECTS in query.scope) {
            collectionDao.getAllCollectionsRaw().filter {
                it.name.fold().contains(q) || it.description.fold().contains(q)
            }.take(n).map { AgentMappers.toAgentProject(it) }
        } else emptyList()

        val chats = if (SearchEntity.CHATS in query.scope) {
            chatDao.getAllChatsOnce().filter {
                it.title.fold().contains(q) || it.summary?.fold()?.contains(q) == true
            }.take(n).map { AgentMappers.toAgentChat(it) }
        } else emptyList()

        return SearchResult(
            query = query.text,
            memories = memories,
            tasks = tasks,
            projects = projects,
            chats = chats,
            truncated = listOf(memories, tasks, projects, chats).any { it.size >= n }
        )
    }

    private fun MemoryEntity.matches(q: String): Boolean =
        aiTitle?.fold()?.contains(q) == true ||
            aiSummary?.fold()?.contains(q) == true ||
            userText?.fold()?.contains(q) == true ||
            aiImageAnalysis?.fold()?.contains(q) == true ||
            aiAudioTranscription?.fold()?.contains(q) == true ||
            bookmarkTitle?.fold()?.contains(q) == true ||
            aiTags?.any { it.fold().contains(q) } == true

    // =======================================================================
    // Tasks
    // =======================================================================

    override suspend fun listTasks(query: TaskQuery, client: AgentClient): Page<AgentTask> {
        client.require(AgentScope.READ)
        var tasks = taskDao.getAllTasksRaw()

        // Approved-only unless explicitly asked otherwise. An agent asking for "tasks"
        // wants his real list, not its own pending suggestions echoed back at it.
        tasks = when (query.approved) {
            null -> tasks.filter { it.isApproved }
            true -> tasks.filter { it.isApproved }
            false -> tasks.filter { !it.isApproved }
        }

        tasks = when (query.status) {
            TaskStatusFilter.OPEN -> tasks.filter { !it.isCompleted }
            TaskStatusFilter.COMPLETED -> tasks.filter { it.isCompleted }
            TaskStatusFilter.ANY -> tasks
        }

        query.type?.let { t -> tasks = tasks.filter { it.type.equals(t.name, true) } }
        query.priority?.let { p -> tasks = tasks.filter { it.priority.equals(p.name, true) } }
        query.projectId?.let { id -> tasks = tasks.filter { it.projectId == id } }
        query.memoryId?.let { id -> tasks = tasks.filter { it.memoryId == id } }
        query.source?.let { s -> tasks = tasks.filter { it.source.equals(s, true) } }
        query.externalId?.let { e -> tasks = tasks.filter { it.externalId == e } }
        query.dueBefore?.let { d -> tasks = tasks.filter { it.dueDate != null && it.dueDate <= d } }
        query.dueAfter?.let { d -> tasks = tasks.filter { it.dueDate != null && it.dueDate >= d } }
        query.search?.let { s ->
            val q = s.fold()
            tasks = tasks.filter { it.title.fold().contains(q) || it.description.fold().contains(q) }
        }

        // Undated tasks sort last on a due-date ordering rather than first, which is what
        // "￿" buys: an inbox item with no date shouldn't outrank today's meeting.
        tasks = when (query.order) {
            TaskOrder.DUE_ASC -> tasks.sortedWith(compareBy({ it.dueDate ?: "￿" }, { it.dueTime ?: "99:99" }))
            TaskOrder.DUE_DESC -> tasks.sortedWith(compareByDescending<TaskEntity> { it.dueDate ?: "" }.thenByDescending { it.dueTime ?: "" })
            TaskOrder.CREATED_ASC -> tasks.sortedBy { it.creationTimestamp }
            TaskOrder.CREATED_DESC -> tasks.sortedByDescending { it.creationTimestamp }
            TaskOrder.PRIORITY_DESC -> tasks.sortedByDescending {
                when (it.priority.uppercase()) { "HIGH" -> 3; "MEDIUM" -> 2; else -> 1 }
            }
        }

        return paginate(tasks, query.effectiveLimit, query.cursor, query.fingerprint())
            .map { AgentMappers.toAgentTask(it) }
    }

    override suspend fun getTask(id: Int, client: AgentClient): AgentTask? {
        client.require(AgentScope.READ)
        return taskDao.getTaskById(id)?.let { AgentMappers.toAgentTask(it) }
    }

    override suspend fun createTask(request: CreateTask, client: AgentClient): AgentTask {
        client.require(AgentScope.WRITE)
        if (request.title.isBlank()) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'title' must not be blank.")
        }
        request.projectId?.let { requireProject(it) }

        val now = System.currentTimeMillis()

        // Idempotency: same external_id from the same client updates rather than duplicates.
        request.externalId?.let { externalId ->
            val existing = taskDao.findByExternalId(externalId, client.sourceTag)
            if (existing != null) {
                val merged = existing.copy(
                    title = request.title,
                    description = request.description,
                    dueDate = request.dueDate,
                    dueTime = request.dueTime,
                    priority = request.priority.name,
                    type = request.type.name,
                    isRecurring = request.recurring,
                    recurrenceRule = request.recurrenceRule,
                    projectId = request.projectId,
                    updatedAt = now
                )
                taskDao.updateTask(merged)
                val dto = AgentMappers.toAgentTask(merged)
                events.emit(AgentEventType.TASK_UPDATED, merged.id.toString(), dto)
                audit(client, "update", "task", merged.id.toString(), "idempotent upsert on $externalId")
                return dto
            }
        }

        val entity = AgentMappers.toTaskEntity(request, client, now)
        val id = taskDao.insertTask(entity).toInt()
        val saved = entity.copy(id = id)
        val dto = AgentMappers.toAgentTask(saved)

        events.emit(AgentEventType.TASK_CREATED, id.toString(), dto)
        audit(client, "create", "task", id.toString(), request.title)

        // A proposed task is worthless if he never learns it's waiting.
        if (request.requiresApproval && client.has(AgentScope.NOTIFY)) {
            runCatching {
                notifier.notify(
                    NotifyRequest(
                        title = context.getString(com.example.memgallery.R.string.agent_task_proposed_title),
                        body = request.title,
                        priority = NotifyPriority.NORMAL,
                        tag = "agent-approval"
                    ),
                    client.name
                )
            }
        }
        return dto
    }

    private suspend fun requireProject(id: Int) {
        collectionDao.getCollectionById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No project with id $id.")
    }

    override suspend fun updateTask(id: Int, patch: TaskPatch, client: AgentClient): AgentTask {
        client.require(AgentScope.WRITE)
        if (patch.isEmpty) throw AgentException(AgentErrorCode.INVALID_REQUEST, "Empty patch.")
        val existing = taskDao.getTaskById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No task with id $id.")
        patch.projectId?.let { requireProject(it) }

        val updated = existing.copy(
            title = patch.title ?: existing.title,
            description = patch.description ?: existing.description,
            dueDate = if (patch.clearDueDate) null else patch.dueDate ?: existing.dueDate,
            dueTime = if (patch.clearDueTime) null else patch.dueTime ?: existing.dueTime,
            priority = patch.priority?.name ?: existing.priority,
            type = patch.type?.name ?: existing.type,
            isCompleted = patch.completed ?: existing.isCompleted,
            status = if (patch.completed == true) "COMPLETED" else if (patch.completed == false) "PENDING" else existing.status,
            isRecurring = patch.recurring ?: existing.isRecurring,
            recurrenceRule = patch.recurrenceRule ?: existing.recurrenceRule,
            projectId = if (patch.clearProject) null else patch.projectId ?: existing.projectId,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.updateTask(updated)
        val dto = AgentMappers.toAgentTask(updated)

        val completedNow = patch.completed == true && !existing.isCompleted
        events.emit(
            if (completedNow) AgentEventType.TASK_COMPLETED else AgentEventType.TASK_UPDATED,
            id.toString(),
            dto
        )
        audit(client, if (completedNow) "complete" else "update", "task", id.toString())
        return dto
    }

    override suspend fun completeTask(id: Int, client: AgentClient): AgentTask =
        updateTask(id, TaskPatch(completed = true), client)

    override suspend fun deleteTask(id: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        val existing = taskDao.getTaskById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No task with id $id.")
        taskDao.deleteTask(existing)
        events.emit(AgentEventType.TASK_DELETED, id.toString(), mapOf("id" to id))
        audit(client, "delete", "task", id.toString())
    }

    override suspend fun approveTask(id: Int, client: AgentClient): AgentTask {
        client.require(AgentScope.WRITE)
        val existing = taskDao.getTaskById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No task with id $id.")
        val updated = existing.copy(isApproved = true, updatedAt = System.currentTimeMillis())
        taskDao.updateTask(updated)
        val dto = AgentMappers.toAgentTask(updated)
        events.emit(AgentEventType.TASK_APPROVED, id.toString(), dto)
        audit(client, "approve", "task", id.toString())
        return dto
    }

    override suspend fun rejectTask(id: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        val existing = taskDao.getTaskById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No task with id $id.")
        taskDao.deleteTask(existing)
        events.emit(AgentEventType.TASK_REJECTED, id.toString(), AgentMappers.toAgentTask(existing))
        audit(client, "reject", "task", id.toString())
    }

    override suspend fun listPendingApproval(client: AgentClient): List<AgentTask> {
        client.require(AgentScope.READ)
        return taskDao.getAllTasksRaw().filter { !it.isApproved }
            .sortedByDescending { it.creationTimestamp }
            .map { AgentMappers.toAgentTask(it) }
    }

    // =======================================================================
    // Memories
    // =======================================================================

    override suspend fun listMemories(query: MemoryQuery, client: AgentClient): Page<AgentMemory> {
        client.require(AgentScope.READ)
        var memories = memoryDao.getAllMemoriesOnce()

        if (!query.includeHidden) memories = memories.filter { !it.isHidden }
        query.status?.let { s -> memories = memories.filter { it.status.equals(s.name, true) } }
        query.from?.let { f -> memories = memories.filter { it.creationTimestamp >= f } }
        query.to?.let { t -> memories = memories.filter { it.creationTimestamp <= t } }
        query.tag?.let { tag ->
            val folded = tag.fold()
            memories = memories.filter { m -> m.aiTags?.any { it.fold() == folded } == true }
        }
        query.search?.let { s ->
            val q = s.fold()
            memories = memories.filter { it.matches(q) }
        }
        query.projectId?.let { id ->
            val ids = collectionDao.memoryIdsForCollection(id).toSet()
            memories = memories.filter { it.id in ids }
        }
        query.kind?.let { kind ->
            memories = memories.filter { AgentMappers.toAgentMemory(it).kind == kind }
        }

        memories = when (query.order) {
            MemoryOrder.CREATED_DESC -> memories.sortedByDescending { it.creationTimestamp }
            MemoryOrder.CREATED_ASC -> memories.sortedBy { it.creationTimestamp }
            MemoryOrder.TITLE_ASC -> memories.sortedBy { it.aiTitle ?: "￿" }
        }

        val page = paginate(memories, query.effectiveLimit, query.cursor, query.fingerprint())
        // Collection ids are looked up only for the page actually returned — doing it for
        // the whole filtered set would be one query per memory in the library.
        return Page(
            page.items.map { AgentMappers.toAgentMemory(it, collectionDao.collectionIdsForMemory(it.id)) },
            page.nextCursor,
            page.total
        )
    }

    override suspend fun getMemory(id: Int, client: AgentClient): AgentMemory? {
        client.require(AgentScope.READ)
        val entity = memoryDao.getMemoryByIdOnce(id) ?: return null
        return AgentMappers.toAgentMemory(entity, collectionDao.collectionIdsForMemory(id))
    }

    override suspend fun getMemoryMedia(id: Int, index: Int, client: AgentClient): MediaBlob? {
        client.require(AgentScope.MEDIA_READ)
        val entity = memoryDao.getMemoryByIdOnce(id) ?: return null
        val images = entity.imageUris ?: listOfNotNull(entity.imageUri?.takeIf { it.isNotBlank() })
        val uriString = when {
            index < images.size -> images[index]
            index == images.size && !entity.audioFilePath.isNullOrBlank() -> entity.audioFilePath
            else -> null
        } ?: return null

        val bytes = try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: uriString)
                if (!file.exists()) return null
                file.readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read media $index of memory $id", e)
            return null
        }

        audit(client, "read", "media", "$id/$index")
        val isAudio = index >= images.size
        return MediaBlob(
            bytes = bytes,
            mimeType = if (isAudio) "audio/mp4" else "image/jpeg",
            filename = uriString.substringAfterLast('/')
        )
    }

    override suspend fun createMemory(request: CreateMemory, client: AgentClient): AgentMemory {
        client.require(AgentScope.WRITE)

        val wantsAi = request.process
        if (wantsAi) {
            if (!settings.aiProcessingAllowed.first()) {
                throw AgentException(
                    AgentErrorCode.FORBIDDEN_SCOPE,
                    "AI processing for agents is switched off in Settings. Retry with process=false."
                )
            }
            enforceAiQuota(client)
        }

        var imageUris = emptyList<String>()
        var audioUri: String? = null
        var userText: String? = null
        var bookmarkUrl: String? = null

        when (val content = request.content) {
            is MemoryContent.Text -> userText = content.text
            is MemoryContent.Url -> bookmarkUrl = content.url
            is MemoryContent.Image -> imageUris = listOf(writeBytes(content.bytes, "image", content.mimeType))
            is MemoryContent.Audio -> audioUri = writeBytes(content.bytes, "audio", content.mimeType)
        }

        val memoryId: Int = if (wantsAi) {
            val result = memoryRepository.savePendingMemory(
                imageUris = imageUris,
                audioUri = audioUri,
                userText = userText,
                bookmarkUrl = bookmarkUrl
            )
            result.getOrElse {
                throw AgentException(AgentErrorCode.INTERNAL, "Could not store memory: ${it.message}")
            }.toInt()
        } else {
            // Stored raw and left PENDING; the normal pipeline picks it up on its next run
            // rather than spending AI quota now.
            val entity = MemoryEntity(
                userText = userText,
                imageUri = imageUris.firstOrNull(),
                imageUris = imageUris.ifEmpty { null },
                audioFilePath = audioUri,
                bookmarkUrl = bookmarkUrl,
                aiTitle = null, aiSummary = null, aiTags = null,
                aiImageAnalysis = null, aiAudioTranscription = null, aiActions = null,
                creationTimestamp = System.currentTimeMillis(),
                status = "PENDING",
                isHidden = request.hidden
            )
            memoryDao.insertMemory(entity).toInt()
        }

        for (projectId in request.projectIds) {
            runCatching { collectionDao.addMemoryToCollection(MemoryCollectionCrossRef(memoryId, projectId)) }
        }

        val saved = memoryDao.getMemoryByIdOnce(memoryId)
            ?: throw AgentException(AgentErrorCode.INTERNAL, "Memory vanished immediately after being stored.")
        val dto = AgentMappers.toAgentMemory(saved, request.projectIds)

        events.emit(AgentEventType.MEMORY_CREATED, memoryId.toString(), dto)
        audit(client, "create", "memory", memoryId.toString(), if (wantsAi) "ai" else "raw")
        return dto
    }

    /** Rolls the counter on date change, so a quota is per calendar day and not per 24h window. */
    private suspend fun enforceAiQuota(client: AgentClient) {
        if (client.id == "internal") return
        val entity = clientDao.getById(client.id) ?: return
        val today = today()
        val used = if (entity.quotaDay == today) entity.aiUsedToday else 0
        if (used >= entity.dailyAiQuota) {
            throw AgentException(
                AgentErrorCode.QUOTA_EXCEEDED,
                "Daily AI-processing quota (${entity.dailyAiQuota}) spent for '${client.name}'. Retry with process=false.",
                mapOf("quota" to entity.dailyAiQuota.toString(), "used" to used.toString())
            )
        }
        clientDao.setQuotaUsage(client.id, used + 1, today)
    }

    private fun writeBytes(bytes: ByteArray, kind: String, mimeType: String): String {
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("mpeg") -> "mp3"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("wav") -> "wav"
            kind == "audio" -> "m4a"
            else -> "jpg"
        }
        val file = File(context.filesDir, "agent_${kind}_${System.currentTimeMillis()}.$ext")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    override suspend fun updateMemory(id: Int, patch: MemoryPatch, client: AgentClient): AgentMemory {
        client.require(AgentScope.WRITE)
        val existing = memoryDao.getMemoryByIdOnce(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No memory with id $id.")
        val updated = existing.copy(
            aiTitle = patch.title ?: existing.aiTitle,
            aiSummary = patch.summary ?: existing.aiSummary,
            userText = patch.userText ?: existing.userText,
            aiTags = patch.tags ?: existing.aiTags,
            isHidden = patch.hidden ?: existing.isHidden
        )
        memoryDao.updateMemory(updated)
        val dto = AgentMappers.toAgentMemory(updated, collectionDao.collectionIdsForMemory(id))
        events.emit(AgentEventType.MEMORY_UPDATED, id.toString(), dto)
        audit(client, "update", "memory", id.toString())
        return dto
    }

    override suspend fun deleteMemory(id: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        val existing = memoryDao.getMemoryByIdOnce(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No memory with id $id.")
        memoryRepository.deleteMemory(existing)
        events.emit(AgentEventType.MEMORY_DELETED, id.toString(), mapOf("id" to id))
        audit(client, "delete", "memory", id.toString())
    }

    // =======================================================================
    // Projects
    // =======================================================================

    override suspend fun listProjects(query: ProjectQuery, client: AgentClient): Page<AgentProject> {
        client.require(AgentScope.READ)
        var all = collectionDao.getAllCollectionsRaw()
        if (!query.includeArchived) all = all.filter { !it.archived }
        query.status?.let { s -> all = all.filter { it.status.equals(s.name, true) } }
        query.search?.let { s ->
            val q = s.fold()
            all = all.filter { it.name.fold().contains(q) || it.description.fold().contains(q) }
        }
        all = all.sortedWith(compareBy({ it.sortOrder }, { -it.creationTimestamp }))

        val page = paginate(all, query.effectiveLimit, query.cursor, query.fingerprint())
        if (!query.withCounts) {
            return Page(page.items.map { AgentMappers.toAgentProject(it) }, page.nextCursor, page.total)
        }

        val today = today()
        return Page(
            page.items.map { collection ->
                val tasks = taskDao.getTasksForProject(collection.id).filter { it.isApproved }
                AgentMappers.toAgentProject(
                    collection,
                    memoryCount = collectionDao.countMemoriesInCollection(collection.id),
                    taskCounts = TaskCounts(
                        total = tasks.size,
                        open = tasks.count { !it.isCompleted },
                        overdue = tasks.count { !it.isCompleted && it.dueDate != null && it.dueDate < today }
                    )
                )
            },
            page.nextCursor,
            page.total
        )
    }

    override suspend fun getProject(id: Int, client: AgentClient): AgentProject? {
        client.require(AgentScope.READ)
        val entity = collectionDao.getCollectionById(id) ?: return null
        val tasks = taskDao.getTasksForProject(id).filter { it.isApproved }
        val today = today()
        return AgentMappers.toAgentProject(
            entity,
            memoryCount = collectionDao.countMemoriesInCollection(id),
            taskCounts = TaskCounts(
                total = tasks.size,
                open = tasks.count { !it.isCompleted },
                overdue = tasks.count { !it.isCompleted && it.dueDate != null && it.dueDate < today }
            )
        )
    }

    override suspend fun createProject(request: CreateProject, client: AgentClient): AgentProject {
        client.require(AgentScope.WRITE)
        if (request.name.isBlank()) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'name' must not be blank.")
        }
        val entity = AgentMappers.toCollectionEntity(request, System.currentTimeMillis())
        val id = collectionDao.insertCollection(entity).toInt()
        val dto = AgentMappers.toAgentProject(entity.copy(id = id))
        events.emit(AgentEventType.PROJECT_CREATED, id.toString(), dto)
        audit(client, "create", "project", id.toString(), request.name)
        return dto
    }

    override suspend fun updateProject(id: Int, patch: ProjectPatch, client: AgentClient): AgentProject {
        client.require(AgentScope.WRITE)
        val existing = collectionDao.getCollectionById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No project with id $id.")
        val updated = existing.copy(
            name = patch.name ?: existing.name,
            description = patch.description ?: existing.description,
            status = patch.status?.name ?: existing.status,
            color = if (patch.clearColor) null else patch.color ?: existing.color,
            targetDate = if (patch.clearTargetDate) null else patch.targetDate ?: existing.targetDate,
            archived = patch.archived ?: existing.archived,
            sortOrder = patch.sortOrder ?: existing.sortOrder
        )
        collectionDao.updateCollection(updated)
        val dto = AgentMappers.toAgentProject(updated)
        events.emit(AgentEventType.PROJECT_UPDATED, id.toString(), dto)
        audit(client, "update", "project", id.toString())
        return dto
    }

    override suspend fun deleteProject(id: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        collectionDao.getCollectionById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No project with id $id.")
        // Deleting a container must never delete its contents. Tasks and memories survive,
        // merely unassigned — the opposite would make an agent's tidy-up catastrophic.
        taskDao.detachFromProject(id)
        collectionDao.clearCollectionLinks(id)
        collectionDao.deleteCollection(id)
        events.emit(AgentEventType.PROJECT_DELETED, id.toString(), mapOf("id" to id))
        audit(client, "delete", "project", id.toString())
    }

    override suspend fun addMemoryToProject(projectId: Int, memoryId: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        requireProject(projectId)
        memoryDao.getMemoryByIdOnce(memoryId)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No memory with id $memoryId.")
        collectionDao.addMemoryToCollection(MemoryCollectionCrossRef(memoryId, projectId))
        audit(client, "update", "project", projectId.toString(), "linked memory $memoryId")
    }

    override suspend fun removeMemoryFromProject(projectId: Int, memoryId: Int, client: AgentClient) {
        client.require(AgentScope.WRITE)
        collectionDao.removeMemoryFromCollection(MemoryCollectionCrossRef(memoryId, projectId))
        audit(client, "update", "project", projectId.toString(), "unlinked memory $memoryId")
    }

    // =======================================================================
    // Chats
    // =======================================================================

    override suspend fun listChats(query: ChatQuery, client: AgentClient): Page<AgentChat> {
        client.require(AgentScope.READ)
        var chats = chatDao.getAllChatsOnce()
        query.search?.let { s ->
            val q = s.fold()
            chats = chats.filter { it.title.fold().contains(q) || it.summary?.fold()?.contains(q) == true }
        }
        val page = paginate(chats, query.effectiveLimit, query.cursor, query.fingerprint())
        return Page(
            page.items.map { AgentMappers.toAgentChat(it, chatDao.countMessages(it.id)) },
            page.nextCursor,
            page.total
        )
    }

    override suspend fun getChatMessages(chatId: Int, client: AgentClient): List<AgentChatMessage> {
        client.require(AgentScope.READ)
        return chatDao.getMessagesForChatOnce(chatId).map { AgentMappers.toAgentChatMessage(it) }
    }

    // =======================================================================
    // Operator attention
    // =======================================================================

    override suspend fun notify(request: NotifyRequest, client: AgentClient): NotificationRef {
        client.require(AgentScope.NOTIFY)
        if (request.title.isBlank()) {
            throw AgentException(AgentErrorCode.INVALID_REQUEST, "'title' must not be blank.")
        }
        enforceNotifyRate(client)
        val id = notifier.notify(request, client.name)
        audit(client, "notify", "notification", id.toString(), request.title)
        return NotificationRef(id = id, tag = request.tag, postedAt = System.currentTimeMillis())
    }

    /**
     * 30 notifications an hour per client. Generous for anything legitimate, and the
     * difference between a buggy agent being annoying and it being uninstallable.
     */
    private suspend fun enforceNotifyRate(client: AgentClient) {
        if (client.id == "internal") return
        val since = System.currentTimeMillis() - 60 * 60 * 1000L
        val recent = auditDao.countSince(client.id, "notify", since)
        if (recent >= 30) {
            throw AgentException(
                AgentErrorCode.RATE_LIMITED,
                "'${client.name}' has posted $recent notifications in the last hour. Slow down."
            )
        }
    }

    override suspend fun ask(request: AskRequest, client: AgentClient): AgentAsk {
        client.require(AgentScope.NOTIFY)
        enforceNotifyRate(client)

        val now = System.currentTimeMillis()
        val id = AgentTokens.generateId()
        val notificationId = 200_000 + (now % 700_000).toInt()
        val entity = AgentAskEntity(
            id = id,
            clientId = client.id.takeIf { it != "internal" },
            askedBy = client.name,
            kind = request.kind.name,
            question = request.question,
            contextText = request.context,
            optionsJson = AgentMappers.encodeStringList(request.options),
            status = AskStatus.PENDING.name,
            priority = request.priority.name,
            deepLink = request.deepLink,
            createdAt = now,
            expiresAt = request.timeoutSec?.takeIf { it > 0 }?.let { now + it * 1000L },
            notificationId = notificationId
        )
        askDao.upsert(entity)
        val dto = AgentMappers.toAgentAsk(entity)

        notifier.postAsk(dto, notificationId)
        events.emit(AgentEventType.ASK_CREATED, id, dto)
        audit(client, "ask", "ask", id, request.question)
        return dto
    }

    override suspend fun pollAsk(id: String, waitSec: Int, client: AgentClient): AgentAsk {
        client.require(AgentScope.READ)
        val deadline = System.currentTimeMillis() + waitSec.coerceIn(0, 60) * 1000L
        while (true) {
            askDao.expireOverdue(System.currentTimeMillis())
            val entity = askDao.getById(id)
                ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No ask with id $id.")
            val dto = AgentMappers.toAgentAsk(entity)
            if (dto.settled || System.currentTimeMillis() >= deadline) {
                if (dto.status == AskStatus.EXPIRED) notifier.cancel(entity.notificationId)
                return dto
            }
            delay(500)
        }
    }

    override suspend fun answerAsk(id: String, answer: String): AgentAsk {
        val entity = askDao.getById(id)
            ?: throw AgentException(AgentErrorCode.NOT_FOUND, "No ask with id $id.")
        val changed = askDao.answer(id, answer, System.currentTimeMillis())
        if (changed == 0) {
            // Already settled — return what's there rather than overwriting a real answer
            // with a late notification tap.
            return AgentMappers.toAgentAsk(entity)
        }
        notifier.cancel(entity.notificationId)
        val updated = askDao.getById(id)!!
        val dto = AgentMappers.toAgentAsk(updated)
        events.emit(AgentEventType.ASK_ANSWERED, id, dto)
        return dto
    }

    override suspend fun listPendingAsks(client: AgentClient): List<AgentAsk> {
        client.require(AgentScope.READ)
        askDao.expireOverdue(System.currentTimeMillis())
        return askDao.observePending().first().map { AgentMappers.toAgentAsk(it) }
    }

    // =======================================================================
    // Streaming and delivery
    // =======================================================================

    override fun events(types: Set<AgentEventType>, afterId: Long): Flow<AgentEvent> =
        events.stream(types, afterId)

    override suspend fun registerWebhook(request: RegisterWebhook, client: AgentClient): AgentWebhook {
        client.require(AgentScope.ADMIN)
        val entity = AgentWebhookEntity(
            id = AgentTokens.generateId(),
            clientId = client.id.takeIf { it != "internal" },
            url = request.url,
            eventTypes = request.eventTypes.joinToString(","),
            secret = request.secret ?: AgentTokens.generateSecret(),
            active = true,
            createdAt = System.currentTimeMillis()
        )
        outboxDao.upsertWebhook(entity)
        audit(client, "create", "webhook", entity.id, request.url)
        return AgentMappers.toAgentWebhook(entity)
    }

    override suspend fun listWebhooks(client: AgentClient): List<AgentWebhook> {
        client.require(AgentScope.ADMIN)
        return outboxDao.observeWebhooks().first().map { AgentMappers.toAgentWebhook(it) }
    }

    override suspend fun deleteWebhook(id: String, client: AgentClient) {
        client.require(AgentScope.ADMIN)
        outboxDao.deleteWebhook(id)
        audit(client, "delete", "webhook", id)
    }

    // =======================================================================
    // Client management — used by the Settings UI, not exposed over HTTP writes
    // =======================================================================

    suspend fun issueToken(request: CreateClient): IssuedToken {
        val token = AgentTokens.generateToken()
        val salt = AgentTokens.generateSalt()
        val entity = com.example.memgallery.data.local.entity.AgentClientEntity(
            id = AgentTokens.generateId(),
            name = request.name.ifBlank { "agent" },
            tokenHash = AgentTokens.hash(token, salt),
            tokenSalt = salt,
            tokenPrefix = AgentTokens.prefixOf(token),
            scopes = AgentScope.format(request.scopes),
            createdAt = System.currentTimeMillis(),
            dailyAiQuota = request.dailyAiQuota
        )
        clientDao.upsert(entity)
        return IssuedToken(AgentMappers.toAgentClient(entity), token)
    }

    /**
     * Resolves a bearer token. Narrow by prefix, then verify the hash — the prefix is not
     * a secret and matching on it alone would be no authentication at all.
     */
    suspend fun authenticate(token: String?): AgentClient? {
        if (token.isNullOrBlank()) return null
        val candidates = clientDao.findByPrefix(AgentTokens.prefixOf(token))
        for (candidate in candidates) {
            if (AgentTokens.verify(token, candidate.tokenSalt, candidate.tokenHash)) {
                clientDao.touch(candidate.id, System.currentTimeMillis())
                return AgentMappers.toAgentClient(candidate)
            }
        }
        return null
    }

    suspend fun hasAnyClient(): Boolean = clientDao.countActive() > 0
}
