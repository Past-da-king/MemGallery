@file:OptIn(kotlinx.coroutines.FlowPreview::class)
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private fun String.foldForSearch(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()

data class CollectionInfo(val count: Int = 0, val covers: List<String> = emptyList())

/** Numbers shown in the Home header. All are derived from real memories and tasks. */
data class GalleryStats(
    val kept: Int = 0,
    val thisWeek: Int = 0,
    val reviewCount: Int = 0,
    val dayStreak: Int = 0,
) {
    companion object {
        fun from(
            perDay: Map<java.time.LocalDate, Int>,
            reviewCount: Int,
            today: java.time.LocalDate = java.time.LocalDate.now()
        ): GalleryStats {
            val kept = perDay.values.sum()
            val thisWeek = (0..6).sumOf { perDay[today.minusDays(it.toLong())] ?: 0 }
            // The streak counts back from today; if nothing was kept yet today, from yesterday.
            var day = if ((perDay[today] ?: 0) > 0) today else today.minusDays(1)
            var streak = 0
            while ((perDay[day] ?: 0) > 0) {
                streak++
                day = day.minusDays(1)
            }
            return GalleryStats(kept, thisWeek, reviewCount, streak)
        }
    }
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: com.example.memgallery.data.repository.SettingsRepository,
    taskDao: com.example.memgallery.data.local.dao.TaskDao
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

    private val _pendingDeletionIntent = MutableStateFlow<android.app.PendingIntent?>(null)
    val pendingDeletionIntent = _pendingDeletionIntent.asStateFlow()
    
    // Heatmap Data derived from timestamps
    // We fetch Flow<List<Long>> and convert to Map<LocalDate, Int>
    val heatMapData: StateFlow<Map<java.time.LocalDate, Int>> = memoryRepository.getAllCreationTimestamps()
        .map { timestamps ->
            timestamps.map { timestamp ->
                java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }
            .groupingBy { it }
            .eachCount()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** Real numbers for the Home header: kept, this week, day streak and tasks waiting for review. */
    val stats: StateFlow<GalleryStats> = combine(
        heatMapData,
        taskDao.getUnapprovedTasks().map { it.size }
    ) { heat, review ->
        GalleryStats.from(heat, review)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryStats()
    )

    val collections = memoryRepository.getAllCollections()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Memory count and up to three cover images per collection. */
    val collectionInfo: StateFlow<Map<Int, CollectionInfo>> = combine(
        memoryRepository.observeCollectionCounts(),
        memoryRepository.observeCollectionCovers()
    ) { counts, covers ->
        val coverMap = covers.groupBy { it.collectionId }.mapValues { (_, v) -> v.take(3).map { it.imageUri } }
        counts.associate { it.collectionId to CollectionInfo(it.total, coverMap[it.collectionId].orEmpty()) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _memories = memoryRepository.getMemories()

    val memories: StateFlow<List<MemoryEntity>> = combine(
        _searchText.debounce(150L),
        _memories,
        _selectedFilter
    ) { text, allMemories, filter ->
        val filteredBySearch = if (text.isBlank()) {
            allMemories
        } else {
            val q = text.foldForSearch()
            allMemories.filter { m ->
                m.aiTitle?.foldForSearch()?.contains(q) == true ||
                m.aiSummary?.foldForSearch()?.contains(q) == true ||
                m.aiTags?.any { tag -> tag.foldForSearch().contains(q) } == true ||
                m.userText?.foldForSearch()?.contains(q) == true ||
                m.aiImageAnalysis?.foldForSearch()?.contains(q) == true ||
                m.aiAudioTranscription?.foldForSearch()?.contains(q) == true
            }
        }

        when (filter) {
            "Images" -> filteredBySearch.filter { it.imageUri != null }
            "Notes" -> filteredBySearch.filter { !it.userText.isNullOrBlank() }
            "Audio" -> filteredBySearch.filter { it.audioFilePath != null }
            "Bookmarks" -> filteredBySearch.filter { it.bookmarkUrl != null }
            "Collections" -> emptyList() // Handled by UI to show collection list
            "All" -> filteredBySearch
            else -> filteredBySearch // Should not happen with defined filters
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createCollection(name: String, description: String, memoriesToAdd: List<Int> = emptyList()) {
        viewModelScope.launch {
            val collectionId = memoryRepository.createCollection(name, description)
            if (memoriesToAdd.isNotEmpty()) {
                memoriesToAdd.forEach { memoryId ->
                    memoryRepository.addMemoryToCollection(memoryId, collectionId.toInt())
                }
                clearSelection()
            }
        }
    }

    fun addSelectedMemoriesToCollection(collectionId: Int) {
        viewModelScope.launch {
            _selectedMemoryIds.value.forEach { memoryId ->
                memoryRepository.addMemoryToCollection(memoryId, collectionId)
            }
            clearSelection()
        }
    }

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
            val iterator = _selectedMemoryIds.value.iterator()
            while (iterator.hasNext()) {
                val memoryId = iterator.next()
                try {
                    val memoryToDelete = memoryRepository.getMemory(memoryId).first()
                    memoryToDelete?.let { memoryRepository.deleteMemory(it) }
                } catch (e: Exception) {
                     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                        e is android.app.RecoverableSecurityException) {
                        _pendingDeletionIntent.value = e.userAction.actionIntent
                        // Stop batch deletion to handle permission one by one (limitation of this simple approach)
                        break 
                    }
                }
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
            try {
                val memoryToDelete = memoryRepository.getMemory(memoryId).first()
                memoryToDelete?.let { memoryRepository.deleteMemory(it) }
            } catch (e: Exception) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                    e is android.app.RecoverableSecurityException) {
                    _pendingDeletionIntent.value = e.userAction.actionIntent
                } else {
                    // Log error or show snackbar
                }
            }
        }
    }

    fun clearPendingDeletionIntent() {
        _pendingDeletionIntent.value = null
    }

    fun hideMemory(memoryId: Int) {
        viewModelScope.launch {
            memoryRepository.hideMemory(memoryId, true)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                _memories,
                settingsRepository.showHighlightsFlow
            ) { allMemories, showHighlights ->
                if (showHighlights && allMemories.isNotEmpty()) {
                    updateHighlight(allMemories)
                } else {
                    _highlightTag.value = null
                    _highlightMemories.value = emptyList()
                }
            }.collect {}
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
