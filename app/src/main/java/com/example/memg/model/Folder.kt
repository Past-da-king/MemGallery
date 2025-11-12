package com.example.memg.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val itemCount: Int = 0
)