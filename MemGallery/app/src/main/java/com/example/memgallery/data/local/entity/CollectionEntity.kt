package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
/**
 * A collection of memories — and, since v18, also a **project**: tasks point at one via
 * [TaskEntity.projectId]. The Agent Bridge exposes this as `/v1/projects` with
 * `/v1/collections` kept as an alias. One table, one mental model.
 */
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val creationTimestamp: Long = System.currentTimeMillis(),

    // --- Project semantics (v18) ---
    val status: String = "ACTIVE", // ACTIVE, PAUSED, DONE
    val color: Int? = null,        // ARGB, null = use the app accent
    val targetDate: String? = null, // YYYY-MM-DD
    val archived: Boolean = false,
    val sortOrder: Int = 0
)
