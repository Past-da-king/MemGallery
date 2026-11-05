package com.example.memgallery.agent

import com.example.memgallery.agent.model.AgentAsk
import com.example.memgallery.agent.model.AgentChat
import com.example.memgallery.agent.model.AgentChatMessage
import com.example.memgallery.agent.model.AgentClient
import com.example.memgallery.agent.model.AgentEvent
import com.example.memgallery.agent.model.AgentEventType
import com.example.memgallery.agent.model.AgentMemory
import com.example.memgallery.agent.model.AgentProject
import com.example.memgallery.agent.model.AgentScope
import com.example.memgallery.agent.model.AgentTask
import com.example.memgallery.agent.model.AgentWebhook
import com.example.memgallery.agent.model.AskKind
import com.example.memgallery.agent.model.AskStatus
import com.example.memgallery.agent.model.BookmarkInfo
import com.example.memgallery.agent.model.CreateProject
import com.example.memgallery.agent.model.CreateTask
import com.example.memgallery.agent.model.DetectedAction
import com.example.memgallery.agent.model.MediaRef
import com.example.memgallery.agent.model.MediaType
import com.example.memgallery.agent.model.MemoryKind
import com.example.memgallery.agent.model.ProcessingStatus
import com.example.memgallery.agent.model.ProjectStatus
import com.example.memgallery.agent.model.TaskPriority
import com.example.memgallery.agent.model.TaskType
import com.example.memgallery.data.local.entity.AgentAskEntity
import com.example.memgallery.data.local.entity.AgentClientEntity
import com.example.memgallery.data.local.entity.AgentEventEntity
import com.example.memgallery.data.local.entity.AgentWebhookEntity
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room entity ↔ agent DTO translation, in one place.
 *
 * Kept out of both the entities and the API surface on purpose: storage details
 * (content URIs, file paths, `aiTags` that is null when it means empty) stop at this
 * boundary, and the wire shape can stay stable while the schema underneath moves.
 */
object AgentMappers {

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    // -----------------------------------------------------------------------
    // Tasks
    // -----------------------------------------------------------------------

    fun toAgentTask(entity: TaskEntity): AgentTask = AgentTask(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        dueDate = entity.dueDate?.takeIf { it.isNotBlank() },
        dueTime = entity.dueTime?.takeIf { it.isNotBlank() },
        priority = TaskPriority.fromStorage(entity.priority),
        type = TaskType.fromStorage(entity.type),
        completed = entity.isCompleted,
        approved = entity.isApproved,
        recurring = entity.isRecurring,
        recurrenceRule = entity.recurrenceRule,
        projectId = entity.projectId,
        memoryId = entity.memoryId,
        source = entity.source,
        externalId = entity.externalId,
        createdAt = entity.creationTimestamp,
        updatedAt = if (entity.updatedAt > 0) entity.updatedAt else entity.creationTimestamp
    )

    fun toTaskEntity(request: CreateTask, client: AgentClient, now: Long): TaskEntity = TaskEntity(
        memoryId = request.memoryId,
        title = request.title,
        description = request.description,
        dueDate = request.dueDate,
        dueTime = request.dueTime,
        isCompleted = false,
        priority = request.priority.name,
        status = "PENDING",
        isApproved = !request.requiresApproval,
        type = request.type.name,
        isRecurring = request.recurring,
        recurrenceRule = request.recurrenceRule,
        creationTimestamp = now,
        source = client.sourceTag,
        externalId = request.externalId,
        projectId = request.projectId,
        updatedAt = now
    )

    // -----------------------------------------------------------------------
    // Memories
    // -----------------------------------------------------------------------

    /**
     * [mediaPathPrefix] is prepended to each [MediaRef.url]. The device server passes
     * `/v1`, so refs stay relative and survive being reached over Tailscale, LAN or the
     * hub without anyone rewriting absolute hostnames.
     */
    fun toAgentMemory(
        entity: MemoryEntity,
        projectIds: List<Int> = emptyList(),
        mediaPathPrefix: String = "/v1"
    ): AgentMemory {
        val images = entity.imageUris
            ?: listOfNotNull(entity.imageUri?.takeIf { it.isNotBlank() })
        val hasAudio = !entity.audioFilePath.isNullOrBlank()

        val media = buildList {
            images.forEachIndexed { index, uri ->
                add(
                    MediaRef(
                        index = index,
                        type = MediaType.IMAGE,
                        url = "$mediaPathPrefix/memories/${entity.id}/media/$index",
                        mimeType = guessMimeType(uri, "image/jpeg")
                    )
                )
            }
            if (hasAudio) {
                add(
                    MediaRef(
                        index = images.size,
                        type = MediaType.AUDIO,
                        url = "$mediaPathPrefix/memories/${entity.id}/media/${images.size}",
                        mimeType = guessMimeType(entity.audioFilePath, "audio/mp4")
                    )
                )
            }
        }

        val bookmark = entity.bookmarkUrl?.takeIf { it.isNotBlank() }?.let {
            BookmarkInfo(
                url = it,
                title = entity.bookmarkTitle,
                description = entity.bookmarkDescription,
                imageUrl = entity.bookmarkImageUrl,
                faviconUrl = entity.bookmarkFaviconUrl
            )
        }

        return AgentMemory(
            id = entity.id,
            kind = inferKind(entity, images.size, hasAudio),
            title = entity.aiTitle?.takeIf { it.isNotBlank() },
            summary = entity.aiSummary?.takeIf { it.isNotBlank() },
            tags = entity.aiTags.orEmpty(),
            userText = entity.userText?.takeIf { it.isNotBlank() },
            imageAnalysis = entity.aiImageAnalysis?.takeIf { it.isNotBlank() },
            transcription = entity.aiAudioTranscription?.takeIf { it.isNotBlank() },
            bookmark = bookmark,
            media = media,
            actions = entity.aiActions.orEmpty().map {
                DetectedAction(type = it.type, description = it.description, date = it.date, time = it.time)
            },
            status = ProcessingStatus.fromStorage(entity.status),
            hidden = entity.isHidden,
            projectIds = projectIds,
            createdAt = entity.creationTimestamp
        )
    }

    private fun inferKind(entity: MemoryEntity, imageCount: Int, hasAudio: Boolean): MemoryKind {
        if (entity.type.equals("CHAT", ignoreCase = true)) return MemoryKind.CHAT
        val kinds = buildList {
            if (imageCount > 0) add(MemoryKind.IMAGE)
            if (hasAudio) add(MemoryKind.AUDIO)
            if (!entity.bookmarkUrl.isNullOrBlank()) add(MemoryKind.BOOKMARK)
            if (!entity.userText.isNullOrBlank()) add(MemoryKind.TEXT)
        }
        return when {
            kinds.isEmpty() -> MemoryKind.TEXT
            kinds.size == 1 -> kinds.first()
            // Text alongside media is a caption, not a second kind.
            kinds.filterNot { it == MemoryKind.TEXT }.size == 1 -> kinds.first { it != MemoryKind.TEXT }
            else -> MemoryKind.MIXED
        }
    }

    private fun guessMimeType(path: String?, fallback: String): String {
        val ext = path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "3gp" -> "audio/3gpp"
            else -> fallback
        }
    }

    // -----------------------------------------------------------------------
    // Projects
    // -----------------------------------------------------------------------

    fun toAgentProject(
        entity: CollectionEntity,
        memoryCount: Int? = null,
        taskCounts: com.example.memgallery.agent.model.TaskCounts? = null
    ): AgentProject = AgentProject(
        id = entity.id,
        name = entity.name,
        description = entity.description,
        status = ProjectStatus.fromStorage(entity.status),
        color = entity.color,
        targetDate = entity.targetDate?.takeIf { it.isNotBlank() },
        archived = entity.archived,
        sortOrder = entity.sortOrder,
        createdAt = entity.creationTimestamp,
        memoryCount = memoryCount,
        taskCounts = taskCounts
    )

    fun toCollectionEntity(request: CreateProject, now: Long): CollectionEntity = CollectionEntity(
        name = request.name,
        description = request.description,
        creationTimestamp = now,
        status = request.status.name,
        color = request.color,
        targetDate = request.targetDate,
        archived = false,
        sortOrder = 0
    )

    // -----------------------------------------------------------------------
    // Chats
    // -----------------------------------------------------------------------

    fun toAgentChat(entity: ChatEntity, messageCount: Int? = null): AgentChat = AgentChat(
        id = entity.id,
        title = entity.title,
        summary = entity.summary,
        messageCount = messageCount,
        savedAsMemory = entity.isSavedAsMemory,
        createdAt = entity.timestamp
    )

    fun toAgentChatMessage(entity: ChatMessageEntity): AgentChatMessage = AgentChatMessage(
        id = entity.id,
        chatId = entity.chatId,
        role = entity.role,
        content = entity.content,
        hasAudio = !entity.audioFilePath.isNullOrBlank(),
        hasImage = !entity.imageUri.isNullOrBlank(),
        createdAt = entity.timestamp
    )

    // -----------------------------------------------------------------------
    // Agent plumbing
    // -----------------------------------------------------------------------

    fun toAgentClient(entity: AgentClientEntity): AgentClient = AgentClient(
        id = entity.id,
        name = entity.name,
        scopes = AgentScope.parse(entity.scopes),
        createdAt = entity.createdAt,
        lastSeenAt = entity.lastSeenAt,
        revoked = entity.revoked,
        dailyAiQuota = entity.dailyAiQuota
    )

    fun toAgentAsk(entity: AgentAskEntity): AgentAsk = AgentAsk(
        id = entity.id,
        kind = AskKind.fromStorage(entity.kind),
        question = entity.question,
        context = entity.contextText,
        options = decodeStringList(entity.optionsJson),
        answer = entity.answer,
        status = AskStatus.fromStorage(entity.status),
        askedBy = entity.askedBy,
        createdAt = entity.createdAt,
        expiresAt = entity.expiresAt,
        answeredAt = entity.answeredAt
    )

    fun toAgentWebhook(entity: AgentWebhookEntity): AgentWebhook = AgentWebhook(
        id = entity.id,
        url = entity.url,
        eventTypes = entity.eventTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        active = entity.active,
        createdAt = entity.createdAt,
        lastStatus = entity.lastStatus,
        lastAttemptAt = entity.lastAttemptAt
    )

    /** Unknown event types are dropped rather than crashing an older client mid-stream. */
    fun toAgentEvent(entity: AgentEventEntity): AgentEvent? {
        val type = AgentEventType.fromWire(entity.type) ?: return null
        return AgentEvent(
            id = entity.id,
            type = type,
            at = entity.createdAt,
            entityId = entity.entityId,
            payloadJson = entity.payloadJson
        )
    }

    fun encodeStringList(values: List<String>): String? =
        if (values.isEmpty()) null else gson.toJson(values)

    fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<String>>(json, stringListType).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
