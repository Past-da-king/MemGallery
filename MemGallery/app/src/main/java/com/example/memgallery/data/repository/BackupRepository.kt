package com.example.memgallery.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.entity.MemoryEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupRepository"
private const val JSON_FILENAME = "memories.json"
private const val ASSETS_DIR = "assets/"
private const val IMAGES_DIR = "assets/images/"
private const val AUDIO_DIR = "assets/audio/"

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val gson: Gson
) {

    fun exportBackup(uri: Uri): Flow<Result<Unit>> = flow<Result<Unit>> {
        try {
            emit(Result.success(Unit)) // Signal start
            
            val memories = memoryDao.getAllMemoriesIncludingHidden().first()
            val memoriesToExport = mutableListOf<MemoryEntity>()
            val addedEntries = mutableSetOf<String>()
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                    
                    // 1. Process Assets & Prepare Entities
                    for (memory in memories) {
                        var exportMemory = memory.copy()
                        
                        // Handle Primary Image
                        memory.imageUri?.let { path ->
                            val fileName = File(path).name
                            val relativePath = "$IMAGES_DIR$fileName"
                            if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                exportMemory = exportMemory.copy(imageUri = relativePath)
                            }
                        }

                        // Handle Image List
                        memory.imageUris?.let { uris ->
                            val newUris = mutableListOf<String>()
                            for (path in uris) {
                                val fileName = File(path).name
                                val relativePath = "$IMAGES_DIR$fileName"
                                if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                    newUris.add(relativePath)
                                } else {
                                    // Keep original if fail? Or skip? Let's keep original to avoid data loss in JSON but it won't be in zip
                                    newUris.add(path) 
                                }
                            }
                            exportMemory = exportMemory.copy(imageUris = newUris)
                        }

                        // Handle Audio
                        memory.audioFilePath?.let { path ->
                            val fileName = File(path).name
                            val relativePath = "$AUDIO_DIR$fileName"
                            if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                exportMemory = exportMemory.copy(audioFilePath = relativePath)
                            }
                        }

                        memoriesToExport.add(exportMemory)
                    }

                    // 2. Write JSON
                    val jsonString = gson.toJson(memoriesToExport)
                    val entry = ZipEntry(JSON_FILENAME)
                    zipOut.putNextEntry(entry)
                    zipOut.write(jsonString.toByteArray())
                    zipOut.closeEntry()
                }
            }
            emit(Result.success(Unit))
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    fun importBackup(uri: Uri): Flow<Result<Int>> = flow<Result<Int>> {
        try {
            var importCount = 0
            val importedMemories = mutableListOf<MemoryEntity>()
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    var memoriesJson: String? = null
                    
                    while (entry != null) {
                        val name = entry!!.name
                        if (name == JSON_FILENAME) {
                            // Fix: Do not close the stream! readBytes reads the entry fully.
                            memoriesJson = String(zipIn.readBytes(), Charsets.UTF_8)
                        } else if (name.startsWith(IMAGES_DIR) || name.startsWith(AUDIO_DIR)) {
                            // Extract asset
                            val fileName = File(name).name
                            val targetFile = File(context.filesDir, fileName) // Flatten to filesDir
                            
                            // Check for collision rename?
                            // For simplicity, we overwrite or keep. Assuming UUIDs in filenames from app logic.
                            FileOutputStream(targetFile).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                    
                    if (memoriesJson != null) {
                        val type = object : TypeToken<List<MemoryEntity>>() {}.type
                        val parsedMemories: List<MemoryEntity> = gson.fromJson(memoriesJson, type)
                        
                        // Remap paths and reset IDs
                        for (memory in parsedMemories) {
                            var newMemory = memory.copy(id = 0) // New ID
                            
                            // Remap imageUri
                            newMemory.imageUri?.let { path ->
                                if (path.startsWith(ASSETS_DIR)) {
                                    val fileName = File(path).name
                                    val newPath = File(context.filesDir, fileName).absolutePath
                                    newMemory = newMemory.copy(imageUri = newPath)
                                }
                            }

                            // Remap imageUris
                            newMemory.imageUris?.let { uris ->
                                val newUris = uris.map { path ->
                                    if (path.startsWith(ASSETS_DIR)) {
                                        val fileName = File(path).name
                                        File(context.filesDir, fileName).absolutePath
                                    } else {
                                        path
                                    }
                                }
                                newMemory = newMemory.copy(imageUris = newUris)
                            }

                            // Remap audio
                            newMemory.audioFilePath?.let { path ->
                                if (path.startsWith(ASSETS_DIR)) {
                                    val fileName = File(path).name
                                    val newPath = File(context.filesDir, fileName).absolutePath
                                    newMemory = newMemory.copy(audioFilePath = newPath)
                                }
                            }
                            importedMemories.add(newMemory)
                        }
                        
                        memoryDao.insertMemories(importedMemories)
                        importCount = importedMemories.size
                    }
                }
            }
            
            emit(Result.success(importCount))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    
    private fun addFileToZip(
        zipOut: ZipOutputStream, 
        filePath: String, 
        entryName: String, 
        addedEntries: MutableSet<String>
    ): Boolean {
        if (addedEntries.contains(entryName)) return true

        try {
            val entry = ZipEntry(entryName)
            zipOut.putNextEntry(entry)

            val inputCallback: ((java.io.InputStream) -> Unit) = { input ->
                input.copyTo(zipOut)
            }

            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use(inputCallback) ?: return false
            } else if (filePath.startsWith("file://")) {
                val uri = Uri.parse(filePath)
                val path = uri.path ?: return false
                val file = File(path)
                if (!file.exists()) return false
                FileInputStream(file).use(inputCallback)
            } else {
                val file = File(filePath)
                if (file.exists()) {
                     FileInputStream(file).use(inputCallback)
                } else {
                     return false
                }
            }

            zipOut.closeEntry()
            addedEntries.add(entryName)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to zip file: $filePath", e)
            return false
        }
    }
}
