package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.repository.MemoryRepository
import com.example.memgallery.data.local.entity.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _highlightTag = MutableStateFlow<String?>(null)
    val highlightTag = _highlightTag.asStateFlow()

    private val _highlightMemories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val highlightMemories = _highlightMemories.asStateFlow()

    private val _selectionModeActive = MutableStateFlow(false)
    val selectionModeActive = _selectionModeActive.asStateFlow()

    private val _selectedMemoryIds = MutableStateFlow(emptySet<Int>())
    val selectedMemoryIds = _selectedMemoryIds.asStateFlow()

    private val _memories = memoryRepository.getMemories()

    val memories: StateFlow<List<MemoryEntity>> = combine(
        _searchText,
        _memories,
        _selectedFilter
    ) { text, allMemories, filter ->
        val filteredBySearch = if (text.isBlank()) {
            allMemories
        } else {
            allMemories.filter {
                it.aiTitle?.contains(text, ignoreCase = true) ?: false ||
                it.aiSummary?.contains(text, ignoreCase = true) ?: false ||
                it.aiTags?.any { tag -> tag.contains(text, ignoreCase = true) } ?: false
            }
        }

        when (filter) {
            "Images" -> filteredBySearch.filter { it.imageUri != null }
            "Notes" -> filteredBySearch.filter { !it.userText.isNullOrBlank() }
            "Audio" -> filteredBySearch.filter { it.audioFilePath != null }
            "Bookmarks" -> filteredBySearch.filter { it.bookmarkUrl != null }
            "All" -> filteredBySearch
            else -> filteredBySearch // Should not happen with defined filters
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchTextChange(text: String) {
        _searchText.value = text
    }

    fun onFilterSelected(filter: String) {
        _selectedFilter.value = filter
    }

    fun toggleSelectionMode() {
        _selectionModeActive.value = !_selectionModeActive.value
        if (!_selectionModeActive.value) {
            _selectedMemoryIds.value = emptySet() // Clear selection when exiting mode
        }
    }

    fun toggleMemorySelection(memoryId: Int) {
        _selectedMemoryIds.value = if (_selectedMemoryIds.value.contains(memoryId)) {
            _selectedMemoryIds.value - memoryId
        } else {
            _selectedMemoryIds.value + memoryId
        }
    }

    fun clearSelection() {
        _selectedMemoryIds.value = emptySet()
        _selectionModeActive.value = false
    }

    fun deleteSelectedMemories() {
        viewModelScope.launch {
            _selectedMemoryIds.value.forEach { memoryId ->
                val memoryToDelete = memoryRepository.getMemory(memoryId).first()
                memoryToDelete?.let { memoryRepository.deleteMemory(it) }
            }
            clearSelection()
        }
    }

    fun deleteMedia(memoryId: Int, deleteImage: Boolean, deleteAudio: Boolean) {
        viewModelScope.launch {
            memoryRepository.updateMemoryMedia(memoryId, deleteImage, deleteAudio)
        }
    }

    fun deleteFullMemory(memoryId: Int) {
        viewModelScope.launch {
            val memoryToDelete = memoryRepository.getMemory(memoryId).first()
            memoryToDelete?.let { memoryRepository.deleteMemory(it) }
        }
    }

    fun hideMemory(memoryId: Int) {
        viewModelScope.launch {
            memoryRepository.hideMemory(memoryId, true)
        }
    }

    init {
        viewModelScope.launch {
            _memories.collect { allMemories ->
                if (allMemories.isNotEmpty()) {
                    updateHighlight(allMemories)
                }
            }
        }
    }

    private fun updateHighlight(allMemories: List<MemoryEntity>) {
        val allTags = allMemories.flatMap { it.aiTags.orEmpty() }.distinct()
        if (allTags.isNotEmpty()) {
            val randomTag = allTags.random()
            _highlightTag.value = randomTag
            _highlightMemories.value = allMemories.filter { it.aiTags?.contains(randomTag) == true }
        } else {
            _highlightTag.value = null
            _highlightMemories.value = emptyList()
        }
    }
}
