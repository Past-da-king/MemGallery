package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // User-provided inputs
    val userText: String?,
    val imageUri: String?, // Stores the local content URI of the image
    val audioFilePath: String?, // Stores the local file path of the recording

    // AI-generated data
    val aiTitle: String?,
    val aiSummary: String?,
    val aiTags: List<String>?, // Room will use a TypeConverter for this
    val aiImageAnalysis: String?,
    val aiAudioTranscription: String?,

    // Metadata
    val creationTimestamp: Long,
    val status: String // e.g., "PENDING", "PROCESSING", "COMPLETED", "FAILED"
)
