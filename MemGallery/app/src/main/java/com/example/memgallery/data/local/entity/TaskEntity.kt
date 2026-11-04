package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memoryId"]), Index(value = ["projectId"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val memoryId: Int? = null,
    val title: String,
    val description: String,
    val dueDate: String?, // YYYY-MM-DD
    val dueTime: String?, // HH:MM
    val isCompleted: Boolean = false,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val status: String = "PENDING", // PENDING, COMPLETED
    val isApproved: Boolean = false,
    val type: String = "TODO", // TODO, EVENT
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null, // e.g., "DAILY", "WEEKLY", "MONTHLY"
    val creationTimestamp: Long = System.currentTimeMillis(),

    // --- Agent Bridge (v18) ---
    /** Who created this: "USER", "AI", or "AGENT:<client name>". */
    val source: String = "USER",
    /** Caller-owned id, so an agent can upsert idempotently against its own tracker. */
    val externalId: String? = null,
    /** Owning project. Points at [CollectionEntity.id]; no FK so deleting a project orphans rather than cascades. */
    val projectId: Int? = null,
    /** Last mutation, in epoch millis. Drives mirror sync and last-write-wins. */
    val updatedAt: Long = System.currentTimeMillis()
)
