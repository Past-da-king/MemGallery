package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun submitFeedback(message: String, includeLogs: Boolean) {
        if (message.isBlank()) {
            _uiState.value = FeedbackUiState.Error("Please enter a message")
            return
        }

        viewModelScope.launch {
            _uiState.value = FeedbackUiState.Loading
            repository.submitFeedback(message, includeLogs)
                .onSuccess { url ->
                    _uiState.value = FeedbackUiState.Success(url)
                }
                .onFailure { e ->
                    _uiState.value = FeedbackUiState.Error(e.message ?: "Unknown error")
                }
        }
    }

    fun resetState() {
        _uiState.value = FeedbackUiState.Idle
    }
}

sealed interface FeedbackUiState {
    object Idle : FeedbackUiState
    object Loading : FeedbackUiState
    data class Success(val issueUrl: String) : FeedbackUiState
    data class Error(val message: String) : FeedbackUiState
}
