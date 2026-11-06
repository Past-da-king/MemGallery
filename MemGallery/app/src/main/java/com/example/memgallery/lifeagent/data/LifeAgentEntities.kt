package com.example.memgallery.lifeagent.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room tables for the Life Agent. Created by AppDatabase.MIGRATION_18_19. Keep the two in step.

@Entity(tableName = "life_connections")
data class LifeConnectionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val connected: Boolean,
    val accountLabel: String?,
    val lastPulledAt: Long,
    val lastError: String?,
    /** Composio connected_account_id found when the user connected this app (an id, not a secret). */
    val accountId: String?,
    /** Composio user_id that owns the connected account. */
    val userId: String?,
)

@Entity(tableName = "life_items", indices = [Index(value = ["fingerprint"], unique = true)])
data class LifeItemEntity(
    @PrimaryKey val id: String,
    val app: String,
    val sourceId: String,
    /** email, event, task or message. */
    val kind: String,
    val title: String,
    val snippet: String,
    /** Raw sender, "Name <email>" when known. */
    val author: String?,
    val url: String?,
    val occurredAt: Long,
    val ingestedAt: Long,
    val fingerprint: String,
)

@Entity(tableName = "life_proposals", indices = [Index(value = ["fingerprint"], unique = true)])
data class LifeProposalEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val app: String,
    val sourceId: String,
    val sourceTitle: String,
    val sourceUrl: String?,
    val personName: String?,
    val dueAt: Long?,
    val amountCents: Long?,
    val currency: String?,
    val confidence: Float,
    val status: String,
    val reason: String,
    val fingerprint: String,
    val matchedTaskId: String?,
    val collectedAt: Long,
    val decidedAt: Long?,
)

@Entity(tableName = "life_people")
data class LifePersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String?,
    val email: String?,
    val phone: String?,
    val lastSeenAt: Long,
)

@Entity(tableName = "life_tasks")
data class LifeTaskEntity(
    @PrimaryKey val id: String,
    val source: String,
    val listName: String?,
    val title: String,
    val dueAt: Long?,
    val status: String,
    val doneLocal: Boolean,
    val snoozedUntil: Long?,
    val url: String?,
)

@Entity(tableName = "life_meetings")
data class LifeMeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val location: String?,
    val joinUrl: String?,
)

@Entity(tableName = "life_runs")
data class LifeRunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val appStatsJson: String,
    val noiseDropped: Int,
    val proposalsCreated: Int,
)

@Entity(tableName = "life_learning")
data class LifeLearningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val app: String,
    val senderKey: String,
    val kind: String,
    /** keep or drop. */
    val decision: String,
    val decidedAt: Long,
)

/**
 * One toolkit of the Composio catalog, cached so Connections opens instantly and works offline. Refreshed from
 * Composio when the cache is older than a day or the user asks. Never holds a credential.
 */
@Entity(tableName = "life_toolkits")
data class LifeToolkitEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val categories: String?,   // comma separated, as Composio names them
    val authSchemes: String?,  // comma separated
    val noAuth: Boolean,
    val toolkitType: String?,
    /** Read tool slugs we found for this toolkit, comma separated. Filled when it is connected. */
    val readTools: String?,
    val fetchedAt: Long,
)
