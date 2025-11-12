package com.example.memg.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "memories")
data class MemoryObject(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val imageUri: String? = null,
    val audioUri: String? = null,
    val text: String? = null,
    val tags: String = "[]", // Store as JSON string
    val summary: String = "",
    val folder: String = "Uncategorized",
    val url: String? = null,
    val timestamp: Date = Date(),
    val transcribedText: String? = null,
    val title: String = "",
    val imageAnalysis: String? = null
)