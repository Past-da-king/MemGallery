package com.example.memgallery.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

import androidx.core.content.FileProvider
import java.util.Objects

private const val TAG = "FileUtils"

@Singleton
class FileUtils @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun copyFileToInternalStorage(uri: Uri, fileType: String): Uri? {
        Log.d(TAG, "Attempting to copy file from URI: $uri")
        var attempt = 0
        val maxAttempts = 5
        val delayMs = 500L

        while (attempt < maxAttempts) {
            try {
                val inputStream = when (uri.scheme) {
                    "content" -> {
                        Log.d(TAG, "URI scheme is 'content', using ContentResolver.")
                        context.contentResolver.openInputStream(uri)
                    }
                    "file" -> {
                        Log.d(TAG, "URI scheme is 'file', using direct file path.")
                        uri.path?.let { java.io.File(it).inputStream() }
                    }
                    else -> throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
                } ?: return null

                val fileExtension = when (fileType) {
                    "image" -> ".jpg"
                    "audio" -> ".m4a"
                    else -> ""
                }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "${fileType.uppercase()}_$timeStamp$fileExtension"
                val file = File(context.filesDir, fileName)
                Log.d(TAG, "Creating new file at: ${file.absolutePath}")
                val outputStream = FileOutputStream(file)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val newFileUri = Uri.fromFile(file)
                Log.d(TAG, "File copied successfully. New URI: $newFileUri")
                return newFileUri
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to copy file (Attempt ${attempt + 1}/$maxAttempts): content is pending or trashed. Retrying...", e)
                attempt++
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(delayMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy file", e)
                e.printStackTrace()
                return null
            }
        }
        Log.e(TAG, "Failed to copy file after $maxAttempts attempts.")
        return null
    }

    fun createImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = context.externalCacheDir
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        )
        return FileProvider.getUriForFile(
            Objects.requireNonNull(context),
            context.packageName + ".provider",
            image
        )
    }

    fun deleteFile(uri: Uri): Boolean {
        Log.d(TAG, "Attempting to delete file from URI: $uri")
        return try {
            when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path!!)
                    if (file.exists()) {
                        file.delete().also {
                            Log.d(TAG, "File deleted: ${uri.path} - $it")
                        }
                    } else {
                        Log.d(TAG, "File does not exist: ${uri.path}")
                        false
                    }
                }
                "content" -> {
                    // For content URIs, we might not have direct file access or ownership.
                    // If the content URI points to a file copied by the app, its scheme would be "file".
                    // If it's from a media picker, it's managed by the system.
                    Log.w(TAG, "Attempted to delete content URI. Not supported for direct file deletion: $uri")
                    false
                }
                else -> {
                    Log.w(TAG, "Unsupported URI scheme for deletion: ${uri.scheme}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $uri", e)
            false
        }
    }
}
