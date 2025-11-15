package com.example.memgallery.data.repository

import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.remote.GeminiService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val geminiService: GeminiService
) {

    fun getMemories(): Flow<List<MemoryEntity>> {
        return memoryDao.getAllMemories()
    }

    fun getMemory(id: Int): Flow<MemoryEntity?> {
        return memoryDao.getMemoryById(id)
    }

    suspend fun createNewMemory(
        imageUri: String?,
        audioUri: String?,
        userText: String?
    ): Result<Long> {
        if (!geminiService.isEnabled()) {
            return Result.failure(IllegalStateException("API Key not set."))
        }

        val analysisResult = geminiService.processMemory(imageUri, audioUri, userText)

        return analysisResult.map { aiAnalysis ->
            val memoryEntity = MemoryEntity(
                userText = userText,
                imageUri = imageUri,
                audioFilePath = audioUri,
                aiTitle = aiAnalysis.title,
                aiSummary = aiAnalysis.summary,
                aiTags = aiAnalysis.tags,
                aiImageAnalysis = aiAnalysis.imageAnalysis,
                aiAudioTranscription = aiAnalysis.audioTranscription,
                creationTimestamp = System.currentTimeMillis()
            )
            memoryDao.insertMemory(memoryEntity)
        }
    }

    suspend fun deleteMemory(memory: MemoryEntity) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory)
    }
}
