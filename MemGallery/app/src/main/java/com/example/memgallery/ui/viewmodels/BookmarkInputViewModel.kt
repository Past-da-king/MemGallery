package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkInputViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<Result<Long>?>(null)
    val saveResult: StateFlow<Result<Long>?> = _saveResult.asStateFlow()

    fun saveBookmark(url: String, userNote: String) {
        if (url.isBlank()) return

        viewModelScope.launch {
            _isSaving.value = true
            val result = memoryRepository.savePendingMemory(
                imageUri = null,
                audioUri = null,
                userText = userNote.takeIf { it.isNotBlank() },
                bookmarkUrl = url
            )
            _saveResult.value = result
            _isSaving.value = false
        }
    }

    fun resetSaveResult() {
        _saveResult.value = null
    }
}
