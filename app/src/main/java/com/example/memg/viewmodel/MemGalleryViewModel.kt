package com.example.memg.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memg.model.MemoryObject
import com.example.memg.model.Folder
import com.example.memg.repository.MemGalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.gson.Gson

@HiltViewModel
class MemGalleryViewModel @Inject constructor(
    private val repository: MemGalleryRepository,
    private val geminiService: com.example.memg.ai.GeminiService,
    private val application: Application // Inject Application context
) : ViewModel() {

    private val _memories = MutableStateFlow<List<MemoryObject>>(emptyList())
    val memories: StateFlow<List<MemoryObject>> = _memories.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _activeScreen = MutableStateFlow("gallery")
    val activeScreen: StateFlow<String> = _activeScreen.asStateFlow()

    private val _selectedMemory = MutableStateFlow<MemoryObject?>(null)
    val selectedMemory: StateFlow<MemoryObject?> = _selectedMemory.asStateFlow()

    private val _isCaptureVisible = MutableStateFlow(false)
    val isCaptureVisible: StateFlow<Boolean> = _isCaptureVisible.asStateFlow()

    private val _isDetailVisible = MutableStateFlow(false)
    val isDetailVisible: StateFlow<Boolean> = _isDetailVisible.asStateFlow()

    private val _isSettingsVisible = MutableStateFlow(false)
    val isSettingsVisible: StateFlow<Boolean> = _isSettingsVisible.asStateFlow()

    // Settings related StateFlows
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isAiEnabled = MutableStateFlow(false)
    val isAiEnabled: StateFlow<Boolean> = _isAiEnabled.asStateFlow()

    private val _autoDeleteDays = MutableStateFlow("30")
    val autoDeleteDays: StateFlow<String> = _autoDeleteDays.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("memgallery_settings", Context.MODE_PRIVATE)

    init {
        loadMemories()
        loadFolders()
        loadSettings() // Load settings when ViewModel is initialized
    }

    private fun loadMemories() {
        viewModelScope.launch {
            repository.getAllMemories().collect { memoryList ->
                _memories.value = memoryList
            }
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folderList ->
                _folders.value = folderList
            }
        }
    }

    fun setActiveScreen(screen: String) {
        _activeScreen.value = screen
    }

    fun setSelectedMemory(memory: MemoryObject?) {
        _selectedMemory.value = memory
    }

    fun setIsCaptureVisible(visible: Boolean) {
        _isCaptureVisible.value = visible
    }

    fun setIsDetailVisible(visible: Boolean) {
        _isDetailVisible.value = visible
    }

    fun setIsSettingsVisible(visible: Boolean) {
        _isSettingsVisible.value = visible
    }

    fun addMemory(memory: MemoryObject) {
        viewModelScope.launch {
            repository.insertMemory(memory)

            // Update folder count
            updateFolderCount(memory.folder)
        }
    }

    fun updateMemory(memory: MemoryObject) {
        viewModelScope.launch {
            repository.updateMemory(memory)

            // Update folder count
            updateFolderCount(memory.folder)
        }
    }

    fun deleteMemory(memory: MemoryObject) {
        viewModelScope.launch {
            repository.deleteMemory(memory)

            // Update folder count
            updateFolderCount(memory.folder)
        }
    }

    // Add memory with AI processing
    fun addMemoryWithAIProcessing(initialMemory: MemoryObject) {
        viewModelScope.launch {
            try {
                var processedMemory = initialMemory

                // Process with AI if the service is enabled
                if (geminiService.isEnabled()) {
                    val hasImage = initialMemory.imageUri != null
                    val hasText = initialMemory.text != null
                    val hasAudio = initialMemory.audioUri != null

                    val processedContent = if (hasImage || hasText || hasAudio) {
                        geminiService.processContent(
                            imageUri = initialMemory.imageUri,
                            audioUri = initialMemory.audioUri,
                            text = initialMemory.text,
                            transcribedText = initialMemory.transcribedText
                        )
                    } else {
                        mapOf(
                            "summary" to initialMemory.summary,
                            "tags" to initialMemory.tags.toList(),
                            "folder" to initialMemory.folder
                        )
                    }

                    processedMemory = initialMemory.copy(
                        title = processedContent["title"] as? String ?: initialMemory.title,
                        summary = processedContent["summary"] as? String ?: initialMemory.summary,
                        tags = Gson().toJson((processedContent["tags"] as? List<*>)?.filterIsInstance<String>() ?: initialMemory.tags.toList()),
                        folder = processedContent["folder"] as? String ?: initialMemory.folder,
                        transcribedText = processedContent["transcription"] as? String ?: initialMemory.transcribedText,
                        text = processedContent["text"] as? String ?: initialMemory.text, // AI's text analysis or original text
                        imageAnalysis = processedContent["image_analysis"] as? String ?: initialMemory.imageAnalysis
                    )
                }

                repository.insertMemory(processedMemory)
                updateFolderCount(processedMemory.folder)
            } catch (e: Exception) {
                // If AI processing fails, save the memory with original data
                repository.insertMemory(initialMemory)
                updateFolderCount(initialMemory.folder)
            }
        }
    }

    fun getMemoryById(id: String): MemoryObject? {
        return _memories.value.find { memory -> memory.id == id }
    }

    private suspend fun updateFolderCount(folderName: String) {
        var memoriesInFolder = emptyList<MemoryObject>()
        repository.getMemoriesByFolder(folderName).collect { list ->
            memoriesInFolder = list
        }
        val folder = repository.getFolderByName(folderName)

        if (folder != null) {
            val updatedFolder = folder.copy(itemCount = memoriesInFolder.size)
            repository.updateFolder(updatedFolder)
        } else {
            repository.insertFolder(Folder(name = folderName, itemCount = memoriesInFolder.size))
        }
    }

    fun searchMemories(query: String) {
        if (query.isEmpty()) {
            loadMemories()
        } else {
            viewModelScope.launch {
                // This is a simplified example - in a real implementation you'd want to properly implement search
                // For now, we'll just filter from the existing list
            }
        }
    }

    // Settings related functions
    fun saveSettings(apiKey: String, isEnabled: Boolean, autoDeleteDays: String) {
        viewModelScope.launch {
            sharedPreferences.edit().apply {
                putString("gemini_api_key", apiKey)
                putBoolean("ai_enabled", isEnabled)
                putString("auto_delete_days", autoDeleteDays)
                apply()
            }
            _apiKey.value = apiKey
            _isAiEnabled.value = isEnabled
            _autoDeleteDays.value = autoDeleteDays

            if (isEnabled && apiKey.isNotBlank()) {
                geminiService.initialize(apiKey)
            } else {
                geminiService.disable()
            }
        }
    }

    private fun loadSettings() {
        val loadedApiKey = sharedPreferences.getString("gemini_api_key", "") ?: ""
        val loadedIsEnabled = sharedPreferences.getBoolean("ai_enabled", false)
        _apiKey.value = loadedApiKey
        _isAiEnabled.value = loadedIsEnabled
        _autoDeleteDays.value = sharedPreferences.getString("auto_delete_days", "30") ?: "30"

        if (loadedIsEnabled && loadedApiKey.isNotBlank()) {
            geminiService.initialize(loadedApiKey)
        } else {
            geminiService.disable()
        }
    }
}