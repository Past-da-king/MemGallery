package com.example.memgallery.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.service.MemoryProcessingWorker
import com.example.memgallery.utils.FileUtils
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MemoryRepository"

@Singleton
class MemoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val geminiService: GeminiService,
    private val fileUtils: FileUtils,
    private val settingsRepository: SettingsRepository
) {
    private val workManager = WorkManager.getInstance(context)

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
        val memoryId = memoryDao.insertMemory(memoryEntity)
        enqueueMemoryProcessing()
        return Result.success(memoryId)
    }

    suspend fun processMemoryWithAI(
        memoryId: Int,
        imageUri: String?,
        audioUri: String?,
        userText: String?
    ): Result<Unit> {
        Log.d(TAG, "processMemoryWithAI started for memoryId: $memoryId")
        if (!geminiService.isEnabled()) {
            Log.d(TAG, "GeminiService not enabled. Attempting to initialize with stored API key.")
            val apiKey = settingsRepository.apiKeyFlow.first()
            if (!apiKey.isNullOrBlank()) {
                geminiService.initialize(apiKey)
                Log.d(TAG, "GeminiService initialized with stored API key.")
            } else {
                Log.e(TAG, "processMemoryWithAI failed: API Key not set and not found in settings.")
                return Result.failure(IllegalStateException("API Key not set."))
            }
        }

        val analysisResult = geminiService.processMemory(imageUri, audioUri, userText)

        analysisResult.onFailure {
            Log.e(TAG, "AI processing failed for memoryId: $memoryId", it)
        }

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
            } ?: run {
                Log.e(TAG, "Memory with ID $memoryId not found for AI processing.")
                throw IllegalStateException("Memory with ID $memoryId not found for AI processing.")
            }
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

    // TODO: This is a simplified version. The full AI processing should be triggered.
    suspend fun createMemoryFromUri(uri: Uri) {
        Log.d(TAG, "Creating memory from URI: $uri")
        val permanentImageUri = fileUtils.copyFileToInternalStorage(uri, "image")?.toString()

        if (permanentImageUri == null) {
            Log.e(TAG, "Failed to copy image from URI: $uri. Aborting memory creation.")
            return
        }

        val memoryEntity = MemoryEntity(
            userText = null,
            imageUri = permanentImageUri,
            audioFilePath = null,
            aiTitle = "New Screenshot", // Placeholder title
            aiSummary = null,
            aiTags = null,
            aiImageAnalysis = null,
            aiAudioTranscription = null,
            creationTimestamp = System.currentTimeMillis(),
            status = "PENDING"
        )
        memoryDao.insertMemory(memoryEntity)
        Log.d(TAG, "Inserted new pending memory from URI: $uri")
        enqueueMemoryProcessing()
    }

    private fun enqueueMemoryProcessing() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MemoryProcessingWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                60,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(workRequest)
        Log.d(TAG, "Enqueued MemoryProcessingWorker")
    }
}
