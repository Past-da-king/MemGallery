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
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _memories = memoryRepository.getMemories()

    val memories: StateFlow<List<MemoryEntity>> = searchText
        .combine(_memories) { text, memories ->
            if (text.isBlank()) {
                memories
            } else {
                memories.filter {
                    it.aiTitle?.contains(text, ignoreCase = true) ?: false ||
                    it.aiSummary?.contains(text, ignoreCase = true) ?: false ||
                    it.aiTags?.any { tag -> tag.contains(text, ignoreCase = true) } ?: false
                }
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
}
