package com.example.memgallery.service

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.repository.MemoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.example.memgallery.R
import android.content.pm.ServiceInfo

private const val TAG = "MemoryProcessingWorker"

@HiltWorker
class MemoryProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryRepository: MemoryRepository,
    private val memoryDao: MemoryDao,
    private val actionNotificationHandler: ActionNotificationHandler
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork started")
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run as foreground service: ${e.message}. Continuing as background worker.")
        }

        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        if (imageUriString != null) {
            Log.d(TAG, "Processing screenshot from URI: $imageUriString")
            val imageUri = Uri.parse(imageUriString)
            memoryRepository.createMemoryFromUri(imageUri)
            return Result.success()
        }

        val pendingMemories = memoryDao.getAllMemories().first().filter { it.status == "PENDING" || it.status == "FAILED" }
        if (pendingMemories.isEmpty()) {
            Log.d(TAG, "No pending memories to process")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingMemories.size} pending memories to process")
        Log.d(TAG, "Found ${pendingMemories.size} pending memories to process")
        


        for (memory in pendingMemories) {
            Log.d(TAG, "Processing memory ${memory.id}")
            val processingMemory = memory.copy(status = "PROCESSING")
            memoryDao.updateMemory(processingMemory)

            val result = memoryRepository.processMemoryWithAI(
                memoryId = memory.id,
                imageUri = memory.imageUri,
                audioUri = memory.audioFilePath,
                userText = memory.userText
            )

            if (result.isSuccess) {
                Log.d(TAG, "Memory ${memory.id} successfully processed")
                val analysis = result.getOrNull()
                if (analysis?.actions?.isNotEmpty() == true) {
                    // Trigger notifications for each action
                    analysis.actions.forEach { action ->
                        actionNotificationHandler.notifyAction(action, memory.id)
                    }
                }
            } else {
                val e = result.exceptionOrNull()
                Log.e(TAG, "Failed to process memory ${memory.id}", e)
                
                if (isNetworkError(e)) {
                    Log.w(TAG, "Network error encountered. Retrying work.")
                    // Revert status to PENDING so it's picked up on retry
                    memoryDao.updateMemory(memory.copy(status = "PENDING"))
                    return Result.retry()
                } else {
                    val failedMemory = memory.copy(status = "FAILED")
                    memoryDao.updateMemory(failedMemory)
                }
            }
        }

        return Result.success()
    }

    private fun isNetworkError(t: Throwable?): Boolean {
        var cause = t
        while (cause != null) {
            if (cause is java.io.IOException || cause is java.net.UnknownHostException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val id = "memory_processing_channel"
        val title = "Processing Memory"
        val cancel = "Cancel"
        
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Memory Processing"
            val description = "Notifications for AI memory processing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(id, name, importance)
            channel.description = description
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("Analyzing your memory with AI...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1001, notification)
        }
    }

    private fun sendActionNotification(memoryId: Int, actions: List<com.example.memgallery.data.remote.dto.ActionDto>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ai_actions_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AI Actions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for detected actions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        actions.forEachIndexed { index, action ->
            val intent = com.example.memgallery.utils.ActionHandler.getActionIntent(action)
            val pendingIntent = if (intent != null) {
                android.app.PendingIntent.getActivity(
                    applicationContext,
                    memoryId * 100 + index, // Unique request code
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            val builder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Action Detected: ${action.type}")
                .setContentText(action.description)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            if (pendingIntent != null) {
                val actionLabel = if (action.type == "EVENT") "Add to Calendar" else "Save Action"
                builder.addAction(R.drawable.ic_launcher_foreground, actionLabel, pendingIntent)
                builder.setContentIntent(pendingIntent) // Also make the notification body clickable
            }

            notificationManager.notify(memoryId * 100 + index, builder.build())
        }
    }
}
