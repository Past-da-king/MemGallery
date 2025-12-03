package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.repository.MemoryRepository
import com.example.memgallery.data.local.entity.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _collectionMemories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val collectionMemories: StateFlow<List<MemoryEntity>> = _collectionMemories.asStateFlow()
    
    private val _collectionName = MutableStateFlow("")
    val collectionName: StateFlow<String> = _collectionName.asStateFlow()

    private val _selectionModeActive = MutableStateFlow(false)
    val selectionModeActive = _selectionModeActive.asStateFlow()

    private val _selectedMemoryIds = MutableStateFlow(emptySet<Int>())
    val selectedMemoryIds = _selectedMemoryIds.asStateFlow()

    fun loadCollection(collectionId: Int) {
        viewModelScope.launch {
            memoryRepository.getAllCollections().collect { collections ->
                val collection = collections.find { it.id == collectionId }
                _collectionName.value = collection?.name ?: "Collection"
            }
        }
        viewModelScope.launch {
            memoryRepository.getMemoriesForCollection(collectionId).collect {
                _collectionMemories.value = it
            }
        }
    }

    fun toggleSelectionMode() {
        _selectionModeActive.value = !_selectionModeActive.value
        if (!_selectionModeActive.value) {
            _selectedMemoryIds.value = emptySet()
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

    fun removeSelectedMemoriesFromCollection(collectionId: Int) {
        viewModelScope.launch {
            _selectedMemoryIds.value.forEach { memoryId ->
                memoryRepository.removeMemoryFromCollection(memoryId, collectionId)
            }
            clearSelection()
        }
    }

    // Logic to add MORE memories to this collection (from outside)
    // This would typically be done by selecting memories from the main gallery and adding them.
    // But the spec says: "User taps the + icon... App displays only memories NOT already in the collection"
    // So we need a list of "Available Memories"
    
    private val _availableMemories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val availableMemories: StateFlow<List<MemoryEntity>> = _availableMemories.asStateFlow()
    
    fun loadAvailableMemories(collectionId: Int) {
        viewModelScope.launch {
            val all = memoryRepository.getMemories().first()
            val currentIds = _collectionMemories.value.map { it.id }.toSet()
            _availableMemories.value = all.filter { !currentIds.contains(it.id) }
        }
    }
    
    fun addMemoriesToCollection(collectionId: Int, memoryIds: List<Int>) {
        viewModelScope.launch {
            memoryIds.forEach { memoryId ->
                memoryRepository.addMemoryToCollection(memoryId, collectionId)
            }
            // Refresh available memories?
            loadAvailableMemories(collectionId)
        }
    }
}
