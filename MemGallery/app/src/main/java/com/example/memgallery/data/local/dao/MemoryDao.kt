package com.example.memgallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.model.PulseMemory
import kotlinx.coroutines.flow.Flow

data class TaskSourceMemory(val id: Int, val aiTitle: String?, val imageUri: String?)

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>): List<Long>


    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE isHidden = 0 ORDER BY creationTimestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY creationTimestamp DESC")
    fun getAllMemoriesIncludingHidden(): Flow<List<MemoryEntity>>

    @Query("SELECT id, aiTitle, imageUri FROM memories WHERE id IN (SELECT DISTINCT memoryId FROM tasks WHERE memoryId IS NOT NULL)")
    fun observeTaskSources(): Flow<List<TaskSourceMemory>>

    @Query("SELECT * FROM memories WHERE id = :id")
    fun getMemoryById(id: Int): Flow<MemoryEntity?>

    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    fun getMemoriesByIds(ids: List<Int>): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE originalMediaUri = :uri LIMIT 1")
    suspend fun getMemoryByOriginalUri(uri: String): MemoryEntity?

    @Query("UPDATE memories SET status = 'PROCESSING' WHERE id = :id AND (status = 'PENDING' OR status = 'FAILED')")
    suspend fun markAsProcessing(id: Int): Int

    @Query("""
        SELECT m.*, t.dueDate as taskDueDate, t.dueTime as taskDueTime 
        FROM memories as m
        INNER JOIN tasks as t ON m.id = t.memoryId
        WHERE t.dueDate >= :currentDate
        AND t.isCompleted = 0
        ORDER BY t.dueDate ASC, t.dueTime ASC
        LIMIT 10
    """)
    suspend fun getPulseMemories(currentDate: String): List<com.example.memgallery.data.local.model.PulseMemory>

    @Query("SELECT creationTimestamp FROM memories WHERE isHidden = 0")
    fun getAllCreationTimestamps(): Flow<List<Long>>

    // --- Agent Bridge ---

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryByIdOnce(id: Int): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY creationTimestamp DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM memories WHERE creationTimestamp >= :since AND isHidden = 0")
    suspend fun countCreatedSince(since: Long): Int
}
