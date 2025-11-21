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
    indices = [Index(value = ["memoryId"])]
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
    val type: String = "TODO", // TODO, EVENT
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null, // e.g., "DAILY", "WEEKLY", "MONTHLY"
    val creationTimestamp: Long = System.currentTimeMillis()
)
