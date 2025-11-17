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

    fun copyFileToInternalStorage(uri: Uri, fileType: String): Uri? {
        Log.d(TAG, "Attempting to copy file from URI: $uri")
        return try {
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
            newFileUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file", e)
            e.printStackTrace()
            null
        }
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
}
