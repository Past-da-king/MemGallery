package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.remote.dto.AiAnalysisDto
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MemoryCreationUiState {
    object Idle : MemoryCreationUiState
    object Loading : MemoryCreationUiState
    data class Success(val newMemoryId: Long) : MemoryCreationUiState
    data class Error(val message: String) : MemoryCreationUiState
}

@HiltViewModel
class MemoryCreationViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemoryCreationUiState>(MemoryCreationUiState.Idle)
    val uiState: StateFlow<MemoryCreationUiState> = _uiState.asStateFlow()

    fun createMemory(imageUri: String?, audioUri: String?, userText: String?) {
        if (imageUri == null && audioUri == null && userText.isNullOrBlank()) {
            _uiState.value = MemoryCreationUiState.Error("At least one input is required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = MemoryCreationUiState.Loading

            val result = memoryRepository.createNewMemory(
                imageUri = imageUri,
                audioUri = audioUri,
                userText = userText
            )

            result.onSuccess { newId ->
                _uiState.value = MemoryCreationUiState.Success(newId)
            }.onFailure { exception ->
                _uiState.value = MemoryCreationUiState.Error(exception.message ?: "An unknown error occurred.")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = MemoryCreationUiState.Idle
    }
}
