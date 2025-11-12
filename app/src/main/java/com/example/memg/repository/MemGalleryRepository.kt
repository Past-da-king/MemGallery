package com.example.memg.repository

import com.example.memg.data.MemoryDao
import com.example.memg.data.FolderDao
import com.example.memg.model.MemoryObject
import com.example.memg.model.Folder
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Singleton
class MemGalleryRepository(
    private val memoryDao: MemoryDao,
    private val folderDao: FolderDao
) {
    fun getAllMemories(): Flow<List<MemoryObject>> = memoryDao.getAllMemories()
    
    fun getMemoriesByFolder(folderName: String): Flow<List<MemoryObject>> = 
        memoryDao.getMemoriesByFolder(folderName)
    
    fun searchMemories(query: String): Flow<List<MemoryObject>> = 
        memoryDao.searchMemories(query)
    
    suspend fun getMemoryById(id: String): MemoryObject? = memoryDao.getMemoryById(id)
    
    suspend fun insertMemory(memory: MemoryObject) = memoryDao.insertMemory(memory)
    
    suspend fun updateMemory(memory: MemoryObject) = memoryDao.updateMemory(memory)
    
    suspend fun deleteMemory(memory: MemoryObject) = memoryDao.deleteMemory(memory)
    
    suspend fun deleteMemoryById(id: String) = memoryDao.deleteMemoryById(id)
    
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()
    
    suspend fun getFolderById(id: String): Folder? = folderDao.getFolderById(id)
    
    suspend fun insertFolder(folder: Folder) = folderDao.insertFolder(folder)
    
    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)
    
    suspend fun deleteFolder(folder: Folder) = folderDao.deleteFolder(folder)
    
    suspend fun getFolderByName(name: String): Folder? = folderDao.getFolderByName(name)
}