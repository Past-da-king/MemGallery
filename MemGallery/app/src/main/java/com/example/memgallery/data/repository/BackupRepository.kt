package com.example.memgallery.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.memgallery.data.local.dao.CollectionDao
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryCollectionCrossRef
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupRepository"

// File names inside the zip
private const val MANIFEST_FILENAME = "manifest.json"
private const val MEMORIES_JSON = "memories.json"
private const val TASKS_JSON = "tasks.json"
private const val COLLECTIONS_JSON = "collections.json"
private const val CROSS_REFS_JSON = "cross_refs.json"
private const val ASSETS_DIR = "assets/"
private const val IMAGES_DIR = "assets/images/"
private const val AUDIO_DIR = "assets/audio/"

// Bumped any time the export schema changes in a way the importer must branch on.
// 1 = MemGallery v0.2.0 / v0.3.0: memories.json + assets only
// 2 = MemGallery v0.3.1+: + tasks.json, collections.json, cross_refs.json, manifest.json
private const val FORMAT_VERSION = 2

/**
 * Manifest written at the root of the export so future importers can branch on the format.
 * Older backups (v1) won't have this file; the importer treats its absence as format = 1.
 */
data class BackupManifest(
    val format: Int,
    val exportedAt: String,
    val appVersion: String
)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDao: MemoryDao,
    private val taskDao: TaskDao,
    private val collectionDao: CollectionDao,
    private val gson: Gson
) {

    fun exportBackup(uri: Uri): Flow<Result<Unit>> = flow<Result<Unit>> {
        try {
            emit(Result.success(Unit)) // Signal start

            val memories = memoryDao.getAllMemoriesIncludingHidden().first()
            val tasks = taskDao.getAllTasksRaw()
            val collections = collectionDao.getAllCollectionsRaw()
            val crossRefs = collectionDao.getAllCrossRefs()

            val memoriesToExport = mutableListOf<MemoryEntity>()
            val addedEntries = mutableSetOf<String>()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->

                    // 1. Process memory media + rewrite paths to relative
                    for (memory in memories) {
                        var exportMemory = memory.copy()

                        memory.imageUri?.let { path ->
                            val fileName = File(path).name
                            val relativePath = "$IMAGES_DIR$fileName"
                            if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                exportMemory = exportMemory.copy(imageUri = relativePath)
                            }
                        }

                        memory.imageUris?.let { uris ->
                            val newUris = mutableListOf<String>()
                            for (path in uris) {
                                val fileName = File(path).name
                                val relativePath = "$IMAGES_DIR$fileName"
                                if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                    newUris.add(relativePath)
                                } else {
                                    newUris.add(path)
                                }
                            }
                            exportMemory = exportMemory.copy(imageUris = newUris)
                        }

                        memory.audioFilePath?.let { path ->
                            val fileName = File(path).name
                            val relativePath = "$AUDIO_DIR$fileName"
                            if (addFileToZip(zipOut, path, relativePath, addedEntries)) {
                                exportMemory = exportMemory.copy(audioFilePath = relativePath)
                            }
                        }

                        memoriesToExport.add(exportMemory)
                    }

                    // 2. Manifest first so importers can early-detect the format
                    writeJsonEntry(zipOut, MANIFEST_FILENAME, BackupManifest(
                        format = FORMAT_VERSION,
                        exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                            .format(Date()),
                        appVersion = appVersionName()
                    ))

                    // 3. Entity tables
                    writeJsonEntry(zipOut, MEMORIES_JSON, memoriesToExport)
                    writeJsonEntry(zipOut, TASKS_JSON, tasks)
                    writeJsonEntry(zipOut, COLLECTIONS_JSON, collections)
                    writeJsonEntry(zipOut, CROSS_REFS_JSON, crossRefs)

                    Log.i(TAG, "Export complete: ${memoriesToExport.size} memories, " +
                        "${tasks.size} tasks, ${collections.size} collections, " +
                        "${crossRefs.size} cross-refs")
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
            // First pass: drain the zip into memory (JSON strings) and the filesystem (assets).
            // We can't seek a ZipInputStream, so we capture each entry as it appears, then
            // resolve dependencies in order afterwards.
            var manifestJson: String? = null
            var memoriesJson: String? = null
            var tasksJson: String? = null
            var collectionsJson: String? = null
            var crossRefsJson: String? = null

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        when (val name = entry!!.name) {
                            MANIFEST_FILENAME -> manifestJson = String(zipIn.readBytes(), Charsets.UTF_8)
                            MEMORIES_JSON -> memoriesJson = String(zipIn.readBytes(), Charsets.UTF_8)
                            TASKS_JSON -> tasksJson = String(zipIn.readBytes(), Charsets.UTF_8)
                            COLLECTIONS_JSON -> collectionsJson = String(zipIn.readBytes(), Charsets.UTF_8)
                            CROSS_REFS_JSON -> crossRefsJson = String(zipIn.readBytes(), Charsets.UTF_8)
                            else -> if (name.startsWith(IMAGES_DIR) || name.startsWith(AUDIO_DIR)) {
                                val fileName = File(name).name
                                val targetFile = File(context.filesDir, fileName)
                                FileOutputStream(targetFile).use { out -> zipIn.copyTo(out) }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            if (memoriesJson == null) {
                emit(Result.failure(IllegalStateException("Backup is missing $MEMORIES_JSON")))
                return@flow
            }

            val format = manifestJson
                ?.let { runCatching { gson.fromJson(it, BackupManifest::class.java).format }.getOrNull() }
                ?: 1
            Log.i(TAG, "Importing backup format=$format")

            // ---------- Memories ----------
            val memoryListType = object : TypeToken<List<MemoryEntity>>() {}.type
            val parsedMemories: List<MemoryEntity> = gson.fromJson(memoriesJson, memoryListType)

            // oldId -> newId so we can rewrite foreign keys later
            val memoryIdMap = HashMap<Int, Int>(parsedMemories.size)
            var memoriesImported = 0

            for (memory in parsedMemories) {
                val rewritten = rewriteMemoryAssetPaths(memory).copy(id = 0)
                val newId = memoryDao.insertMemory(rewritten).toInt()
                memoryIdMap[memory.id] = newId
                memoriesImported++
            }

            // ---------- Collections + tasks + cross-refs ----------
            // Counters used in the log line; they don't surface to the snackbar (which still shows
            // memory count for backwards compatibility), but they do show up in logcat for support.
            var tasksImported = 0
            var tasksOrphaned = 0
            var tasksReconstituted = 0
            var collectionsImported = 0
            var crossRefsImported = 0
            var crossRefsOrphaned = 0

            if (format >= 2) {
                // Collections
                val collectionIdMap = HashMap<Int, Int>()
                if (collectionsJson != null) {
                    val collType = object : TypeToken<List<CollectionEntity>>() {}.type
                    val parsedCollections: List<CollectionEntity> = gson.fromJson(collectionsJson, collType)
                    for (coll in parsedCollections) {
                        val newId = collectionDao.insertCollection(coll.copy(id = 0)).toInt()
                        collectionIdMap[coll.id] = newId
                        collectionsImported++
                    }
                }

                // Tasks (rewrite memoryId via map; drop if memoryId was set but unresolvable)
                if (tasksJson != null) {
                    val taskType = object : TypeToken<List<TaskEntity>>() {}.type
                    val parsedTasks: List<TaskEntity> = gson.fromJson(tasksJson, taskType)
                    for (task in parsedTasks) {
                        val mappedMemoryId = task.memoryId?.let { memoryIdMap[it] }
                        if (task.memoryId != null && mappedMemoryId == null) {
                            // Memory disappeared between export and import; skip orphaned task.
                            tasksOrphaned++
                            continue
                        }
                        taskDao.insertTask(task.copy(id = 0, memoryId = mappedMemoryId))
                        tasksImported++
                    }
                }

                // Cross-refs (drop any pair that can't resolve both sides)
                if (crossRefsJson != null) {
                    val refType = object : TypeToken<List<MemoryCollectionCrossRef>>() {}.type
                    val parsedRefs: List<MemoryCollectionCrossRef> = gson.fromJson(crossRefsJson, refType)
                    for (ref in parsedRefs) {
                        val newMem = memoryIdMap[ref.memoryId]
                        val newColl = collectionIdMap[ref.collectionId]
                        if (newMem != null && newColl != null) {
                            collectionDao.addMemoryToCollection(MemoryCollectionCrossRef(newMem, newColl))
                            crossRefsImported++
                        } else {
                            crossRefsOrphaned++
                        }
                    }
                }
            } else {
                // Format 1 (legacy v0.2.0 / v0.3.0): no separate task table in the backup.
                // Reconstitute AI-detected tasks from each memory's `aiActions` so users at least
                // get their events/to-dos/reminders back.
                for (memory in parsedMemories) {
                    val newMemoryId = memoryIdMap[memory.id] ?: continue
                    val actions = memory.aiActions.orEmpty()
                    if (actions.isEmpty()) continue
                    val tasks = actions.map { action ->
                        TaskEntity(
                            memoryId = newMemoryId,
                            title = action.description.take(50),
                            description = action.description,
                            dueDate = action.date,
                            dueTime = action.time,
                            priority = "MEDIUM",
                            status = "PENDING",
                            type = action.type ?: "TODO",
                            isApproved = true
                        )
                    }
                    taskDao.insertTasks(tasks)
                    tasksReconstituted += tasks.size
                }
            }

            Log.i(TAG, "Import complete (format=$format): " +
                "$memoriesImported memories, $collectionsImported collections, " +
                "$tasksImported tasks (orphaned: $tasksOrphaned, reconstituted from aiActions: $tasksReconstituted), " +
                "$crossRefsImported cross-refs (orphaned: $crossRefsOrphaned)")

            emit(Result.success(memoriesImported))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun rewriteMemoryAssetPaths(memory: MemoryEntity): MemoryEntity {
        var rewritten = memory
        rewritten.imageUri?.let { path ->
            if (path.startsWith(ASSETS_DIR)) {
                val fileName = File(path).name
                rewritten = rewritten.copy(
                    imageUri = File(context.filesDir, fileName).absolutePath
                )
            }
        }
        rewritten.imageUris?.let { uris ->
            val newUris = uris.map { path ->
                if (path.startsWith(ASSETS_DIR)) {
                    val fileName = File(path).name
                    File(context.filesDir, fileName).absolutePath
                } else path
            }
            rewritten = rewritten.copy(imageUris = newUris)
        }
        rewritten.audioFilePath?.let { path ->
            if (path.startsWith(ASSETS_DIR)) {
                val fileName = File(path).name
                rewritten = rewritten.copy(
                    audioFilePath = File(context.filesDir, fileName).absolutePath
                )
            }
        }
        return rewritten
    }

    private fun writeJsonEntry(zipOut: ZipOutputStream, name: String, payload: Any) {
        val entry = ZipEntry(name)
        zipOut.putNextEntry(entry)
        zipOut.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    private fun appVersionName(): String = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

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
