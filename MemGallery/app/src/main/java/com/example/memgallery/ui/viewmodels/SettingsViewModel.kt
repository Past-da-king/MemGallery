package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ApiKeyUiState {
    object Idle : ApiKeyUiState
    object Loading : ApiKeyUiState
    data class Success(val message: String) : ApiKeyUiState
    data class Error(val message: String) : ApiKeyUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geminiService: GeminiService
) : ViewModel() {

    val autoIndexScreenshots: StateFlow<Boolean> = settingsRepository.autoIndexScreenshotsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // API Key State
    private val _apiKeyUiState = MutableStateFlow<ApiKeyUiState>(ApiKeyUiState.Idle)
    val apiKeyUiState: StateFlow<ApiKeyUiState> = _apiKeyUiState.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        viewModelScope.launch {
            val key = settingsRepository.apiKeyFlow.first()
            if (!key.isNullOrBlank()) {
                _apiKey.value = key
                geminiService.initialize(key)
            }
        }
    }

    fun setAutoIndexScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoIndexScreenshots(enabled)
        }
    }

    // API Key Actions
    fun onApiKeyChange(newKey: String) {
        _apiKey.value = newKey
    }

    fun validateAndSaveKey() {
        viewModelScope.launch {
            _apiKeyUiState.value = ApiKeyUiState.Loading
            val key = _apiKey.value
            if (key.isBlank()) {
                _apiKeyUiState.value = ApiKeyUiState.Error("API Key cannot be empty.")
                return@launch
            }

            val result = geminiService.validateApiKey(key)

            result.onSuccess {
                settingsRepository.saveApiKey(key)
                geminiService.initialize(key)
                _apiKeyUiState.value = ApiKeyUiState.Success("Success! Key is valid.")
            }.onFailure {
                _apiKeyUiState.value = ApiKeyUiState.Error("Error: Invalid Key.")
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            geminiService.disable()
            _apiKey.value = ""
            _apiKeyUiState.value = ApiKeyUiState.Idle
        }
    }
}
