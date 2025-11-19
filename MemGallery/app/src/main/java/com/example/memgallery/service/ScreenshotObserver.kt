package com.example.memgallery.service

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import android.os.Looper
import androidx.work.WorkManager
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ScreenshotObserver"

@Singleton
class ScreenshotObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val workManager by lazy { WorkManager.getInstance(context) }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    fun start() {
        val contentResolver: ContentResolver = context.contentResolver
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        Log.d(TAG, "ScreenshotObserver started")
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(this)
        job.cancel()
        Log.d(TAG, "ScreenshotObserver stopped")
    }

    private val recentlyProcessedUris = mutableSetOf<Uri>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        uri?.let {
            if (recentlyProcessedUris.contains(it)) {
                Log.d(TAG, "Ignoring already processed URI: $it")
                return
            }
            scope.launch {
                if (settingsRepository.autoIndexScreenshotsFlow.first()) {
                    Log.d(TAG, "New media detected: $it")
                    if (isScreenshot(it)) {
                        recentlyProcessedUris.add(it)
                        handler.postDelayed({ recentlyProcessedUris.remove(it) }, 5000) // 5-second cooldown

                        Log.d(TAG, "New screenshot detected: $it, enqueuing for processing.")
                        val inputData = Data.Builder()
                            .putString(MemoryProcessingWorker.KEY_IMAGE_URI, it.toString())
                            .build()


                        val workRequest = OneTimeWorkRequestBuilder<MemoryProcessingWorker>()
                            .setInputData(inputData)
                            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()
                        workManager.enqueue(workRequest)
                    }
                }
            }
        }
    }

    private fun isScreenshot(uri: Uri): Boolean {
        Log.d(TAG, "isScreenshot checking URI: $uri")
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    Log.d(TAG, "Checking file: $displayName")
                    val isScreen = displayName.contains("screenshot", ignoreCase = true) && !displayName.contains("thumbnail", ignoreCase = true)
                    Log.d(TAG, "Is screenshot? $isScreen")
                    return isScreen
                } else {
                    Log.d(TAG, "Cursor is empty for URI: $uri")
                }
            } ?: run {
                Log.d(TAG, "Query returned null cursor for URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if screenshot", e)
        }
        return false
    }
}