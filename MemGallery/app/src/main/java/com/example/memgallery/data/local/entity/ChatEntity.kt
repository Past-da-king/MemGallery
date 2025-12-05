package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val summary: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSavedAsMemory: Boolean = false
)
