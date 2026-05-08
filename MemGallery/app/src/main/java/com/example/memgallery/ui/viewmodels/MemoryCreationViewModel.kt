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

sealed interface MemoryCreationUiState {
    object Idle : MemoryCreationUiState
    object Loading : MemoryCreationUiState
    data class Success(val newMemoryId: Long) : MemoryCreationUiState
    data class Error(val message: String) : MemoryCreationUiState
}

@HiltViewModel
class MemoryCreationViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val urlMetadataExtractor: com.example.memgallery.utils.UrlMetadataExtractor,
    private val fileUtils: com.example.memgallery.utils.FileUtils
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemoryCreationUiState>(MemoryCreationUiState.Idle)
    val uiState: StateFlow<MemoryCreationUiState> = _uiState.asStateFlow()

    private val _draftImageUris = MutableStateFlow<List<String>>(emptyList())
    val draftImageUris: StateFlow<List<String>> = _draftImageUris.asStateFlow()

    private val _draftAudioUri = MutableStateFlow<String?>(null)
    val draftAudioUri: StateFlow<String?> = _draftAudioUri.asStateFlow()

    private val _draftUserText = MutableStateFlow<String?>(null)
    val draftUserText: StateFlow<String?> = _draftUserText.asStateFlow()

    private val _draftBookmarkUrl = MutableStateFlow<String?>(null)
    val draftBookmarkUrl: StateFlow<String?> = _draftBookmarkUrl.asStateFlow()

    fun addDraftImageUri(uri: String) {
        _draftImageUris.value = _draftImageUris.value + uri
    }

    fun removeDraftImageUri(uri: String) {
        _draftImageUris.value = _draftImageUris.value - uri
    }
    
    fun setDraftImageUris(uris: List<String>) {
        _draftImageUris.value = uris
    }

    fun setDraftAudioUri(uri: String?) {
        _draftAudioUri.value = uri
    }

    fun setDraftUserText(text: String?) {
        _draftUserText.value = text
    }

    fun setDraftBookmarkUrl(url: String?) {
        _draftBookmarkUrl.value = url
        if (!url.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    val metadata = urlMetadataExtractor.extract(url)
                    if (metadata.imageUrl != null && _draftImageUris.value.isEmpty()) {
                        val localUri = fileUtils.downloadImageFromUrl(metadata.imageUrl)
                        if (localUri != null) {
                            addDraftImageUri(localUri.toString())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun createMemory() {
        val imageUris = _draftImageUris.value
        val audioUri = _draftAudioUri.value
        val userText = _draftUserText.value
        val bookmarkUrl = _draftBookmarkUrl.value

        if (imageUris.isEmpty() && audioUri == null && userText.isNullOrBlank() && bookmarkUrl.isNullOrBlank()) {
            _uiState.value = MemoryCreationUiState.Error("At least one input is required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = MemoryCreationUiState.Loading

            val result = memoryRepository.savePendingMemory(
                imageUris = imageUris,
                audioUri = audioUri,
                userText = userText,
                bookmarkUrl = bookmarkUrl
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
        _draftImageUris.value = emptyList()
        _draftAudioUri.value = null
        _draftUserText.value = null
        _draftBookmarkUrl.value = null
    }
}
