package com.example.memgallery.data.local.dao

import androidx.room.*
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef
import com.example.memgallery.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("SELECT * FROM collections ORDER BY creationTimestamp DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMemoryToCollection(crossRef: MemoryCollectionCrossRef)

    @Delete
    suspend fun removeMemoryFromCollection(crossRef: MemoryCollectionCrossRef)

    @Transaction
    @Query("""
        SELECT * FROM memories 
        INNER JOIN memory_collection_cross_ref ON memories.id = memory_collection_cross_ref.memoryId 
        WHERE memory_collection_cross_ref.collectionId = :collectionId
        ORDER BY creationTimestamp DESC
    """)
    fun getMemoriesForCollection(collectionId: Int): Flow<List<MemoryEntity>>

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Int)
    
    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getCollectionByName(name: String): CollectionEntity?
}
