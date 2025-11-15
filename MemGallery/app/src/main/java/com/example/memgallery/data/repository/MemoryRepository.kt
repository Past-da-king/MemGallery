package com.example.memgallery.data.repository

import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.utils.FileUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MemoryRepository"

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val geminiService: GeminiService,
    private val fileUtils: FileUtils
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
        Log.d(TAG, "createNewMemory called with imageUri: $imageUri, audioUri: $audioUri, userText: $userText")
        if (!geminiService.isEnabled()) {
            return Result.failure(IllegalStateException("API Key not set."))
        }

        // First, process the original URIs with the AI service
        val analysisResult = geminiService.processMemory(imageUri, audioUri, userText)

        // Then, after successful analysis, create permanent copies for storage
        val permanentImageUri = imageUri?.let {
            Log.d(TAG, "Copying image to internal storage from: $it")
            val newUri = fileUtils.copyFileToInternalStorage(Uri.parse(it), "image")
            Log.d(TAG, "Image copied to: $newUri")
            newUri?.toString()
        }

        val permanentAudioUri = audioUri?.let {
            Log.d(TAG, "Copying audio to internal storage from: $it")
            val newUri = fileUtils.copyFileToInternalStorage(Uri.parse(it), "audio")
            Log.d(TAG, "Audio copied to: $newUri")
            newUri?.toString()
        }

        return analysisResult.map { aiAnalysis ->
            val memoryEntity = MemoryEntity(
                userText = userText,
                imageUri = permanentImageUri,
                audioFilePath = permanentAudioUri,
                aiTitle = aiAnalysis.title,
                aiSummary = aiAnalysis.summary,
                aiTags = aiAnalysis.tags,
                aiImageAnalysis = aiAnalysis.imageAnalysis,
                aiAudioTranscription = aiAnalysis.audioTranscription,
                creationTimestamp = System.currentTimeMillis()
            )
            Log.d(TAG, "Inserting new memory entity: $memoryEntity")
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
