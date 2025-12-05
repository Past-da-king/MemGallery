package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chatId: Int,
    val role: String, // "user" or "model"
    val content: String,
    val audioFilePath: String? = null, // Path to audio file attachment
    val imageUri: String? = null,      // URI of image/document attachment
    val timestamp: Long = System.currentTimeMillis()
)
