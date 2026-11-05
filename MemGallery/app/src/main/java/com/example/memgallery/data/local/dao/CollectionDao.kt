package com.example.memgallery.data.local.dao

import androidx.room.*
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef
import com.example.memgallery.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

data class CollectionCount(val collectionId: Int, val total: Int)
data class CollectionCover(val collectionId: Int, val imageUri: String)

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("SELECT collectionId, COUNT(*) AS total FROM memory_collection_cross_ref GROUP BY collectionId")
    fun observeCounts(): Flow<List<CollectionCount>>

    @Query("SELECT x.collectionId AS collectionId, m.imageUri AS imageUri FROM memory_collection_cross_ref x JOIN memories m ON m.id = x.memoryId WHERE m.imageUri IS NOT NULL ORDER BY m.creationTimestamp DESC")
    fun observeCovers(): Flow<List<CollectionCover>>

    @Query("SELECT * FROM collections ORDER BY creationTimestamp DESC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMemoryToCollection(crossRef: MemoryCollectionCrossRef)

    @Delete
    suspend fun removeMemoryFromCollection(crossRef: MemoryCollectionCrossRef)

    @Transaction
    @Query("""
        SELECT memories.* FROM memories 
        INNER JOIN memory_collection_cross_ref ON memories.id = memory_collection_cross_ref.memoryId 
        WHERE memory_collection_cross_ref.collectionId = :collectionId
        ORDER BY creationTimestamp DESC
    """)
    fun getMemoriesForCollection(collectionId: Int): Flow<List<MemoryEntity>>

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Int)
    
    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getCollectionByName(name: String): CollectionEntity?

    /** Backup: list every collection eagerly (one-shot, not Flow). */
    @Query("SELECT * FROM collections ORDER BY id ASC")
    suspend fun getAllCollectionsRaw(): List<CollectionEntity>

    /** Backup: list every memory↔collection link. */
    @Query("SELECT * FROM memory_collection_cross_ref")
    suspend fun getAllCrossRefs(): List<MemoryCollectionCrossRef>

    // --- Agent Bridge (collections double as projects from v18) ---

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Int): CollectionEntity?

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Query("SELECT COUNT(*) FROM memory_collection_cross_ref WHERE collectionId = :collectionId")
    suspend fun countMemoriesInCollection(collectionId: Int): Int

    @Query("SELECT collectionId FROM memory_collection_cross_ref WHERE memoryId = :memoryId")
    suspend fun collectionIdsForMemory(memoryId: Int): List<Int>

    @Query("SELECT memoryId FROM memory_collection_cross_ref WHERE collectionId = :collectionId")
    suspend fun memoryIdsForCollection(collectionId: Int): List<Int>

    @Query("DELETE FROM memory_collection_cross_ref WHERE collectionId = :collectionId")
    suspend fun clearCollectionLinks(collectionId: Int)
}
