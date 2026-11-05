package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Agent Bridge storage (schema v18).
 *
 * Five small tables that let outside agents talk to MemGallery without any of them
 * knowing which agent framework is on the other end. Nothing here references a
 * specific client (A.O.S, Claude Code, Codex…) — a client is just a row in
 * [AgentClientEntity] holding a hashed token and a scope set.
 */

/**
 * One authenticated caller. The plaintext token is shown exactly once, at creation,
 * and never stored — only [tokenHash] (PBKDF2 over [tokenSalt]) and [tokenPrefix],
 * which exists purely so the UI can say "mg_7f3a…" next to a revoke button.
 */
@Entity(
    tableName = "agent_clients",
    indices = [Index(value = ["tokenPrefix"])]
)
data class AgentClientEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val tokenHash: String,
    val tokenSalt: String,
    val tokenPrefix: String,
    /** Comma-separated wire scopes, e.g. "read,write,notify". */
    val scopes: String,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val revoked: Boolean = false,
    /** Max AI-processed memory creations per calendar day. 0 disables AI processing for this client. */
    val dailyAiQuota: Int = 50,
    /** Rolling counter for [dailyAiQuota], reset when [quotaDay] rolls over. */
    val aiUsedToday: Int = 0,
    /** YYYY-MM-DD the [aiUsedToday] counter belongs to. */
    val quotaDay: String? = null
)

/**
 * A question an agent put to the operator — the "modal in your pocket".
 * Created by `POST /v1/ask`, answered from a notification action, a RemoteInput
 * reply, or the in-app Agent Inbox.
 */
@Entity(
    tableName = "agent_asks",
    indices = [Index(value = ["status"]), Index(value = ["clientId"])]
)
data class AgentAskEntity(
    @PrimaryKey
    val id: String,
    val clientId: String? = null,
    /** Display name of the asker, kept even if the client is later revoked and deleted. */
    val askedBy: String,
    val kind: String,            // CONFIRM, CHOICE, TEXT
    val question: String,
    val contextText: String? = null,
    /** JSON array of choice labels, for kind = CHOICE. */
    val optionsJson: String? = null,
    val answer: String? = null,
    val status: String = "PENDING", // PENDING, ANSWERED, EXPIRED, DISMISSED
    val priority: String = "NORMAL",
    val deepLink: String? = null,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val answeredAt: Long? = null,
    /** Android notification id, so the post can be cancelled once answered elsewhere. */
    val notificationId: Int
)

/**
 * Outbox row. Every mutation appends one; SSE streams them live, webhooks and the
 * hub drain them with retries. Rows are pruned once delivered and older than a day.
 */
@Entity(
    tableName = "agent_events",
    indices = [Index(value = ["createdAt"]), Index(value = ["deliveredAt"])]
)
data class AgentEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,            // task.created, memory.processed, ask.answered, …
    val entityId: String? = null,
    val payloadJson: String,
    val createdAt: Long,
    val deliveredAt: Long? = null,
    val attempts: Int = 0
)

/** A registered outbound webhook. Deliveries are signed HMAC-SHA256 over the body with [secret]. */
@Entity(
    tableName = "agent_webhooks",
    indices = [Index(value = ["clientId"])]
)
data class AgentWebhookEntity(
    @PrimaryKey
    val id: String,
    val clientId: String? = null,
    val url: String,
    /** Comma-separated event types, or "*" for everything. */
    val eventTypes: String,
    val secret: String,
    val active: Boolean = true,
    val createdAt: Long,
    val lastStatus: String? = null,
    val lastAttemptAt: Long? = null
)

/**
 * Append-only record of what agents did, so the in-app Agent Activity screen can
 * answer "who created this task" without guessing from [TaskEntity.source].
 */
@Entity(
    tableName = "agent_audit",
    indices = [Index(value = ["at"]), Index(value = ["clientId"])]
)
data class AgentAuditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientId: String? = null,
    val clientName: String,
    val action: String,          // create, update, complete, delete, notify, ask, read
    val entityType: String,      // task, memory, project, notification, ask
    val entityId: String? = null,
    val detail: String? = null,
    val at: Long
)
