package com.example.memgallery.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MemoryProcessingService"
private const val RETRY_DELAY_MS = 5000L // 5 seconds

@Singleton
class MemoryProcessingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val memoryDao: MemoryDao // Injecting DAO directly for status updates
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startProcessing() {
        serviceScope.launch {
            while (true) {
                if (isNetworkAvailable()) {
                    Log.d(TAG, "Network available. Checking for pending memories...")
                    processPendingMemories()
                } else {
                    Log.d(TAG, "Network not available. Waiting...")
                }
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun processPendingMemories() {
        val pendingMemories = memoryDao.getAllMemories().first().filter { it.status == "PENDING" || it.status == "FAILED" }
        if (pendingMemories.isEmpty()) {
            Log.d(TAG, "No pending or failed memories found.")
            return
        }

        Log.d(TAG, "Found ${pendingMemories.size} pending/failed memories to process.")
        pendingMemories.forEach { memory ->
            // Update status to PROCESSING
            val processingMemory = memory.copy(status = "PROCESSING")
            memoryDao.updateMemory(processingMemory)

            val result = memoryRepository.processMemoryWithAI(
                memoryId = memory.id,
                imageUri = memory.imageUri,
                audioUri = memory.audioFilePath,
                userText = memory.userText
            )

            result.onSuccess {
                Log.d(TAG, "Memory ${memory.id} successfully processed by AI.")
                // Status is already updated to COMPLETED inside processMemoryWithAI
            }.onFailure { e ->
                Log.e(TAG, "Failed to process memory ${memory.id} with AI: ${e.message}")
                val failedMemory = memory.copy(status = "FAILED")
                memoryDao.updateMemory(failedMemory)
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
