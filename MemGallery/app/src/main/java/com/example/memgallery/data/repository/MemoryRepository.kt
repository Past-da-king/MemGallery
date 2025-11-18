package com.example.memgallery.data.repository

import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.utils.FileUtils
import kotlinx.coroutines.flow.first
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

    suspend fun savePendingMemory(
        imageUri: String?,
        audioUri: String?,
        userText: String?
    ): Result<Long> {
        Log.d(TAG, "savePendingMemory called with imageUri: $imageUri, audioUri: $audioUri, userText: $userText")

        // Create permanent copies for storage
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

        val memoryEntity = MemoryEntity(
            userText = userText,
            imageUri = permanentImageUri,
            audioFilePath = permanentAudioUri,
            aiTitle = null, // Will be filled by AI
            aiSummary = null, // Will be filled by AI
            aiTags = null, // Will be filled by AI
            aiImageAnalysis = null,
            aiAudioTranscription = null,
            creationTimestamp = System.currentTimeMillis(),
            status = "PENDING"
        )
        Log.d(TAG, "Inserting new pending memory entity: $memoryEntity")
        return Result.success(memoryDao.insertMemory(memoryEntity))
    }

    suspend fun processMemoryWithAI(
        memoryId: Int,
        imageUri: String?,
        audioUri: String?,
        userText: String?
    ): Result<Unit> {
        Log.d(TAG, "processMemoryWithAI called for memoryId: $memoryId")
        if (!geminiService.isEnabled()) {
            return Result.failure(IllegalStateException("API Key not set."))
        }

        val analysisResult = geminiService.processMemory(imageUri, audioUri, userText)

        return analysisResult.map { aiAnalysis ->
            val existingMemory = memoryDao.getMemoryById(memoryId).first() // Get the existing memory
            existingMemory?.let {
                val updatedMemory = it.copy(
                    aiTitle = aiAnalysis.title,
                    aiSummary = aiAnalysis.summary,
                    aiTags = aiAnalysis.tags,
                    aiImageAnalysis = aiAnalysis.imageAnalysis,
                    aiAudioTranscription = aiAnalysis.audioTranscription,
                    status = "COMPLETED"
                )
                memoryDao.updateMemory(updatedMemory)
                Log.d(TAG, "Memory $memoryId updated with AI analysis.")
            } ?: throw IllegalStateException("Memory with ID $memoryId not found for AI processing.")
        }
    }

    suspend fun deleteMemory(memory: MemoryEntity) {
        memory.imageUri?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") { // Only delete app-internal files
                fileUtils.deleteFile(uri)
            }
        }
        memory.audioFilePath?.let { uriString ->
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") { // Only delete app-internal files
                fileUtils.deleteFile(uri)
            }
        }
        memoryDao.deleteMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory)
    }

    suspend fun updateMemoryMedia(memoryId: Int, deleteImage: Boolean, deleteAudio: Boolean) {
        val existingMemory = memoryDao.getMemoryById(memoryId).first()
        existingMemory?.let { memory ->
            var updatedImageUri = memory.imageUri
            var updatedAudioFilePath = memory.audioFilePath

            if (deleteImage && memory.imageUri != null) {
                val uri = Uri.parse(memory.imageUri)
                if (uri.scheme == "file") { // Only delete app-internal files
                    fileUtils.deleteFile(uri)
                }
                updatedImageUri = null
            }

            if (deleteAudio && memory.audioFilePath != null) {
                val uri = Uri.parse(memory.audioFilePath)
                if (uri.scheme == "file") { // Only delete app-internal files
                    fileUtils.deleteFile(uri)
                }
                updatedAudioFilePath = null
            }

            val updatedMemory = memory.copy(
                imageUri = updatedImageUri,
                audioFilePath = updatedAudioFilePath
            )
            memoryDao.updateMemory(updatedMemory)
        }
    }

    suspend fun hideMemory(memoryId: Int, hide: Boolean) {
        val existingMemory = memoryDao.getMemoryById(memoryId).first()
        existingMemory?.let { memory ->
            val updatedMemory = memory.copy(isHidden = hide)
            memoryDao.updateMemory(updatedMemory)
        }
    }
}
