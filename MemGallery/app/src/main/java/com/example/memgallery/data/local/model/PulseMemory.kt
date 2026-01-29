package com.example.memgallery.data.local.model

import androidx.room.Embedded
import com.example.memgallery.data.local.entity.MemoryEntity

data class PulseMemory(
    @Embedded
    val memory: MemoryEntity,
    val taskDueDate: String?,
    val taskDueTime: String?
)
