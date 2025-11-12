package com.example.memg.data

import androidx.room.*
import com.example.memg.model.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder)

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("SELECT * FROM folders WHERE name = :name")
    suspend fun getFolderByName(name: String): Folder?
}