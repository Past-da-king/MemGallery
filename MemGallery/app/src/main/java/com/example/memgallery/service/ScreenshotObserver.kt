package com.example.memgallery.service

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.service.MemoryProcessingWorker
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
    private var isStarted = false

    init {
        Log.d(TAG, "ScreenshotObserver instance created: ${System.identityHashCode(this)}")
    }

    fun start() {
        if (isStarted) {
            Log.w(TAG, "ScreenshotObserver.start() called when already started")
            return
        }

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val contentResolver: ContentResolver = context.contentResolver
        if (hasPermission) {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                this
            )
            Log.d(TAG, "ScreenshotObserver started with full permissions")
        } else {
            // Fallback: Limited observer (may miss some screenshots)
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                false, // Not recursive
                this
            )
            Log.d(TAG, "ScreenshotObserver started in limited mode (no permission)")
        }
        isStarted = true
    }

    fun stop() {
        if (!isStarted) return
        context.contentResolver.unregisterContentObserver(this)
        // Don't cancel scope as this singleton might be restarted? 
        // Actually usage in Application.onCreate implies it lives forever.
        // job.cancel() 
        isStarted = false
        Log.d(TAG, "ScreenshotObserver stopped")
    }

    private val recentlyProcessedUris = mutableSetOf<Uri>()
    private val recentlyProcessedPaths = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "onChange triggered - isStarted: $isStarted, uri: $uri")
        uri?.let {
            if (recentlyProcessedUris.contains(it)) {
                Log.d(TAG, "Ignoring already processed URI: $it")
                return
            }
            
            scope.launch {
                if (settingsRepository.autoIndexScreenshotsFlow.first()) {
                    Log.d(TAG, "New media detected: $it")

                    // Check file path for deduplication
                    val filePath = getFilePathFromUri(it)
                    if (filePath != null && recentlyProcessedPaths.contains(filePath)) {
                        Log.d(TAG, "Ignoring already processed file path: $filePath")
                        return@launch
                    }

                    if (isScreenshot(it)) {
                        recentlyProcessedUris.add(it)
                        
                        if (filePath != null) {
                            recentlyProcessedPaths.add(filePath)
                            handler.postDelayed({ recentlyProcessedPaths.remove(filePath) }, 10000) // 10-second cooldown for path
                        }
                        
                        handler.postDelayed({ recentlyProcessedUris.remove(it) }, 5000) // 5-second cooldown for URI

                        Log.d(TAG, "New screenshot detected: $it, enqueuing for processing.")
                        val inputData = Data.Builder()
                            .putString(MemoryProcessingWorker.KEY_IMAGE_URI, it.toString())
                            .build()


                        val workRequest = OneTimeWorkRequestBuilder<MemoryProcessingWorker>()
                            .setInputData(inputData)
                            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()
                        workManager.enqueue(workRequest)
                        
                        // Show Toast feedback
                        handler.post {
                            android.widget.Toast.makeText(
                                context,
                                "Screenshot saved to MemGallery!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from URI", e)
            null
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