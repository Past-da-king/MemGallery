package com.example.memg.data

import androidx.room.*
import com.example.memg.model.MemoryObject
import com.example.memg.model.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryObject>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoryObject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryObject)

    @Update
    suspend fun updateMemory(memory: MemoryObject)

    @Delete
    suspend fun deleteMemory(memory: MemoryObject)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("SELECT * FROM memories WHERE folder = :folderName ORDER BY timestamp DESC")
    fun getMemoriesByFolder(folderName: String): Flow<List<MemoryObject>>

    @Query("SELECT * FROM memories WHERE summary LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMemories(query: String): Flow<List<MemoryObject>>
}