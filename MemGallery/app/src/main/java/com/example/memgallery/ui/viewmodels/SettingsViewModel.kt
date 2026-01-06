package com.example.memgallery.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.remote.GeminiService
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.service.EdgeGestureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geminiService: GeminiService,
    private val chatGeminiService: com.example.memgallery.data.remote.ChatGeminiService
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

    // AI Provider Selection
    val aiProvider: StateFlow<String> = settingsRepository.aiProviderFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "GEMINI"
        )

    // Groq API Key State
    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _groqApiKeyUiState = MutableStateFlow<ApiKeyUiState>(ApiKeyUiState.Idle)
    val groqApiKeyUiState: StateFlow<ApiKeyUiState> = _groqApiKeyUiState.asStateFlow()

    // Groq Model State
    val groqModelId: StateFlow<String> = settingsRepository.groqModelIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "meta-llama/llama-4-maverick-17b-128e-instruct"
        )

    // Notification Preferences
    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val notificationFilter: StateFlow<String> = settingsRepository.notificationFilterFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "ALL"
        )

    // Share Sheet Preference
    val showInShareSheet: StateFlow<Boolean> = settingsRepository.showInShareSheetFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Onboarding State
    val isOnboardingCompleted: StateFlow<Boolean> = settingsRepository.isOnboardingCompletedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Task Screen State
    val taskScreenEnabled: StateFlow<Boolean> = settingsRepository.taskScreenEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Highlights State
    val showHighlights: StateFlow<Boolean> = settingsRepository.showHighlightsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // User System Prompt State
    private val _userSystemPrompt = MutableStateFlow("")
    val userSystemPrompt: StateFlow<String> = _userSystemPrompt.asStateFlow()

    // User Context State
    private val _userContextSummary = MutableStateFlow("")
    val userContextSummary: StateFlow<String> = _userContextSummary.asStateFlow()

    val userContextGenerationFrequency: StateFlow<String> = settingsRepository.userContextGenerationFrequencyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "DAILY"
        )

    private val _isGeneratingContext = MutableStateFlow(false)
    val isGeneratingContext: StateFlow<Boolean> = _isGeneratingContext.asStateFlow()

    // Theme Settings
    val dynamicThemingEnabled: StateFlow<Boolean> = settingsRepository.dynamicThemingEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val appThemeMode: StateFlow<String> = settingsRepository.appThemeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "SYSTEM"
        )

    val amoledModeEnabled: StateFlow<Boolean> = settingsRepository.amoledModeEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val selectedColor: StateFlow<Int> = settingsRepository.selectedColorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = -1
        )

    // Edge Gesture Settings
    val edgeGestureEnabled: StateFlow<Boolean> = settingsRepository.edgeGestureEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val edgeGestureSide: StateFlow<String> = settingsRepository.edgeGestureSideFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "RIGHT"
        )

    val edgeGestureActionSwipeUp: StateFlow<String> = settingsRepository.edgeGestureActionSwipeUpFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "NONE"
        )

    val edgeGestureActionSwipeDown: StateFlow<String> = settingsRepository.edgeGestureActionSwipeDownFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "NONE"
        )

    val edgeGestureActionDoubleTap: StateFlow<String> = settingsRepository.edgeGestureActionDoubleTapFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "NONE"
        )

    // New Edge Gesture Appearance Settings
    val edgeGesturePositionY: StateFlow<Int> = settingsRepository.edgeGesturePositionYFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 50)

    val edgeGestureHeightPercent: StateFlow<Int> = settingsRepository.edgeGestureHeightPercentFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 30)

    val edgeGestureWidth: StateFlow<Int> = settingsRepository.edgeGestureWidthFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 20)

    val edgeGestureDualHandles: StateFlow<Boolean> = settingsRepository.edgeGestureDualHandlesFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false)

    val edgeGestureVisible: StateFlow<Boolean> = settingsRepository.edgeGestureVisibleFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    // New Behavior Settings
    val audioAutoStart: StateFlow<Boolean> = settingsRepository.audioAutoStartFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    val postCaptureBehavior: StateFlow<String> = settingsRepository.postCaptureBehaviorFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = "FOREGROUND")

    val autoRemindersEnabled: StateFlow<Boolean> = settingsRepository.autoRemindersEnabledFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    val overlayStyle: StateFlow<String> = settingsRepository.overlayStyleFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = "EDGE")

    fun setAutoIndexScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoIndexScreenshots(enabled)
        }
    }

    fun setAutoRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRemindersEnabled(enabled)
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
                chatGeminiService.initialize(key)
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

    // AI Provider Selection Actions
    fun setAiProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.setAiProvider(provider)
        }
    }

    // Groq API Key Actions
    fun onGroqApiKeyChange(newKey: String) {
        _groqApiKey.value = newKey
    }

    fun validateAndSaveGroqKey() {
        viewModelScope.launch {
            _groqApiKeyUiState.value = ApiKeyUiState.Loading
            val key = _groqApiKey.value
            if (key.isBlank()) {
                _groqApiKeyUiState.value = ApiKeyUiState.Error("API Key cannot be empty.")
                return@launch
            }

            try {
                // Simple validation - try to create a client and make a test request
                settingsRepository.saveGroqApiKey(key)
                _groqApiKeyUiState.value = ApiKeyUiState.Success("Success! Groq key saved.")
            } catch (e: Exception) {
                _groqApiKeyUiState.value = ApiKeyUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun clearGroqKey() {
        viewModelScope.launch {
            settingsRepository.clearGroqApiKey()
            _groqApiKey.value = ""
            _groqApiKeyUiState.value = ApiKeyUiState.Idle
        }
    }

    fun setGroqModelId(modelId: String) {
        viewModelScope.launch {
            settingsRepository.setGroqModelId(modelId)
        }
    }

    // Notification Actions
    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setNotificationFilter(filter: String) {
        viewModelScope.launch {
            settingsRepository.setNotificationFilter(filter)
        }
    }

    // Share Sheet Actions
    fun setShowInShareSheet(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowInShareSheet(enabled)
        }
    }

    // Onboarding Actions
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    // Task Screen Actions
    fun setTaskScreenEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTaskScreenEnabled(enabled)
        }
    }

    // Highlights Actions
    fun setShowHighlights(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHighlights(enabled)
        }
    }

    // User System Prompt Actions
    fun onUserSystemPromptChange(prompt: String) {
        _userSystemPrompt.value = prompt
    }

    fun saveUserSystemPrompt() {
        viewModelScope.launch {
            settingsRepository.saveUserSystemPrompt(_userSystemPrompt.value)
        }
    }

    // User Context Actions
    fun onUserContextSummaryChange(summary: String) {
        _userContextSummary.value = summary
    }

    fun saveUserContextSummary() {
        viewModelScope.launch {
            settingsRepository.saveUserContextSummary(_userContextSummary.value)
        }
    }

    fun setUserContextGenerationFrequency(frequency: String) {
        viewModelScope.launch {
            settingsRepository.setUserContextGenerationFrequency(frequency)
        }
    }

    fun generateUserContext() {
        viewModelScope.launch {
            _isGeneratingContext.value = true
            try {
                val result = chatGeminiService.generateUserContext()
                result.onSuccess { summary ->
                    onUserContextSummaryChange(summary)
                    // The repo call inside service already saves it, but we can save explicitly to be sure or just rely on flow
                    // Actually, generateUserContext in service calls settingsRepository.saveUserContextSummary(summary)
                    // So we just update our local state to reflect it immediately if needed (or rely on the flow collection)
                }
            } catch (e: Exception) {
               // Handle error
            } finally {
                _isGeneratingContext.value = false
            }
        }
    }

    fun setDynamicThemingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicThemingEnabled(enabled)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setAppThemeMode(mode)
        }
    }

    fun setAmoledModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAmoledModeEnabled(enabled)
        }
    }

    fun setSelectedColor(color: Int) {
        viewModelScope.launch {
            settingsRepository.setSelectedColor(color)
        }
    }

    fun setEdgeGestureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEdgeGestureEnabled(enabled)
            if (enabled) {
                startEdgeGestureService()
            } else {
                stopEdgeGestureService()
            }
        }
    }

    private fun startEdgeGestureService() {
        val intent = Intent(context, EdgeGestureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopEdgeGestureService() {
        val intent = Intent(context, EdgeGestureService::class.java)
        context.stopService(intent)
    }

    fun setEdgeGestureSide(side: String) {
        viewModelScope.launch {
            settingsRepository.setEdgeGestureSide(side)
        }
    }

    fun setEdgeGestureActionSwipeUp(action: String) {
        viewModelScope.launch {
            settingsRepository.setEdgeGestureActionSwipeUp(action)
        }
    }

    fun setEdgeGestureActionSwipeDown(action: String) {
        viewModelScope.launch {
            settingsRepository.setEdgeGestureActionSwipeDown(action)
        }
    }

    fun setEdgeGestureActionDoubleTap(action: String) {
        viewModelScope.launch {
            settingsRepository.setEdgeGestureActionDoubleTap(action)
        }
    }

    // New Setters
    fun setEdgeGesturePositionY(percent: Int) {
        viewModelScope.launch { settingsRepository.setEdgeGesturePositionY(percent) }
    }

    fun setEdgeGestureHeightPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setEdgeGestureHeightPercent(percent) }
    }

    fun setEdgeGestureWidth(width: Int) {
        viewModelScope.launch { settingsRepository.setEdgeGestureWidth(width) }
    }

    fun setEdgeGestureDualHandles(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEdgeGestureDualHandles(enabled) }
    }

    fun setEdgeGestureVisible(visible: Boolean) {
        viewModelScope.launch { settingsRepository.setEdgeGestureVisible(visible) }
    }

    fun setAudioAutoStart(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAudioAutoStart(enabled) }
    }

    fun setPostCaptureBehavior(behavior: String) {
        viewModelScope.launch { settingsRepository.setPostCaptureBehavior(behavior) }
    }

    fun setOverlayStyle(style: String) {
        viewModelScope.launch { settingsRepository.setOverlayStyle(style) }
    }

    init {
        viewModelScope.launch {
            _apiKey.value = settingsRepository.apiKeyFlow.first() ?: ""
            _groqApiKey.value = settingsRepository.groqApiKeyFlow.first() ?: ""
            _userSystemPrompt.value = settingsRepository.userSystemPromptFlow.first()
            _userContextSummary.value = settingsRepository.userContextSummaryFlow.first()
        }
    }
}