package com.example.memgallery.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memory_collection_cross_ref",
    primaryKeys = ["memoryId", "collectionId"],
    indices = [Index(value = ["memoryId"]), Index(value = ["collectionId"])]
)
data class MemoryCollectionCrossRef(
    val memoryId: Int,
    val collectionId: Int
)
