package com.example.memgallery.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.R
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.remote.ChatGeminiService
import com.example.memgallery.data.remote.ChatStreamEvent
import com.example.memgallery.data.remote.ChatTools
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val chatGeminiService: ChatGeminiService,
    private val memoryRepository: com.example.memgallery.data.repository.MemoryRepository,
    private val settingsRepository: com.example.memgallery.data.repository.SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        // DB-persisted markers and equality literals — DO NOT localize.
        const val DEFAULT_CHAT_TITLE = "New Chat"
        const val VOICE_MESSAGE_MARKER = "🎤 Voice message"
        const val ATTACHMENT_MARKER = "📎 Attachment"
    }

    // ... (existing code) ...

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Chats List (Filtered by search)
    val chats: StateFlow<List<ChatEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                chatDao.getAllChats()
            } else {
                chatDao.searchChats(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Chat State
    private val _currentChatId = MutableStateFlow<Int?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _currentChatId
        .flatMapLatest { chatId ->
            if (chatId != null) {
                chatDao.getMessagesForChat(chatId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Memories referenced in chat messages (e.g. by displayMemoriesById tool)
    val displayedMemories: StateFlow<Map<Int, com.example.memgallery.data.local.entity.MemoryEntity>> = currentMessages
        .flatMapLatest { messages ->
            val allIds = messages.mapNotNull { it.displayedMemoryIds }.flatten().distinct()
            if (allIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                memoryRepository.getMemoriesByIds(allIds).map { memories ->
                    memories.associateBy { it.id }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage = _inputMessage.asStateFlow()

    // ==================== STREAMING & THINKING STATE ====================
    
    // The content currently being streamed (chunks appended)
    private val _streamingContent = MutableStateFlow("")
    val streamingContent = _streamingContent.asStateFlow()
    
    // The "thought" process currently being streamed (if using ThinkingConfig)
    private val _currentThought = MutableStateFlow("")
    val currentThought = _currentThought.asStateFlow()
    
    // The name of the tool currently executing (e.g. "Querying Database")
    private val _currentTool = MutableStateFlow<String?>(null)
    val currentTool = _currentTool.asStateFlow()
    
    // Whether we are currently streaming a response
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    init {
        // Collect Tool Events for UI feedback
        viewModelScope.launch {
            ChatTools.toolEvents.collect { event ->
                when (event) {
                    is ChatTools.ToolEvent.Started -> {
                        _currentTool.value = context.getString(R.string.chat_tool_indicator, event.toolName)
                    }
                    is ChatTools.ToolEvent.Finished -> {
                        _currentTool.value = null
                    }
                }
            }
        }

        // Smart User Context Generation
        viewModelScope.launch {
            val context = settingsRepository.userContextSummaryFlow.first()
            val frequency = settingsRepository.userContextGenerationFrequencyFlow.first()
            val lastTimestamp = settingsRepository.lastContextGenerationTimestampFlow.first()
            val now = System.currentTimeMillis()
            
            val shouldGenerate = when (frequency) {
                "ALWAYS" -> true // Generate on every app launch/ViewModel init
                "DAILY" -> (now - lastTimestamp) > 24 * 60 * 60 * 1000 // 24 hours
                "WEEKLY" -> (now - lastTimestamp) > 7 * 24 * 60 * 60 * 1000 // 7 days
                "MANUAL" -> context.isBlank() // Only generate if empty (first run)
                else -> context.isBlank()
            }

            if (shouldGenerate) {
                android.util.Log.i("ChatViewModel", "Auto-generating User Context (Freq: $frequency)...")
                chatGeminiService.generateUserContext()
            } else {
                android.util.Log.d("ChatViewModel", "Skipping User Context generation. Freq: $frequency")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateInputMessage(message: String) {
        _inputMessage.value = message
    }

    fun selectChat(chatId: Int) {
        _currentChatId.value = chatId
    }

    fun createNewChat() {
        viewModelScope.launch {
            val newChat = ChatEntity(title = DEFAULT_CHAT_TITLE)
            val id = chatDao.insertChat(newChat).toInt()
            _currentChatId.value = id
        }
    }

    // ==================== CHAT SELECTION & DELETION ====================
    
    private val _selectionModeActive = MutableStateFlow(false)
    val selectionModeActive = _selectionModeActive.asStateFlow()
    
    private val _selectedChatIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedChatIds = _selectedChatIds.asStateFlow()
    
    fun toggleSelectionMode() {
        _selectionModeActive.value = !_selectionModeActive.value
        if (!_selectionModeActive.value) {
            _selectedChatIds.value = emptySet()
        }
    }
    
    fun toggleChatSelection(chatId: Int) {
        val current = _selectedChatIds.value.toMutableSet()
        if (chatId in current) {
            current.remove(chatId)
        } else {
            current.add(chatId)
        }
        _selectedChatIds.value = current
        
        // Exit selection mode if nothing selected
        if (current.isEmpty()) {
            _selectionModeActive.value = false
        }
    }
    
    fun clearSelection() {
        _selectedChatIds.value = emptySet()
        _selectionModeActive.value = false
    }
    
    fun deleteChat(chatId: Int) {
        viewModelScope.launch {
            chatDao.deleteChatsByIds(listOf(chatId))
            // If deleted chat was current, clear selection
            if (_currentChatId.value == chatId) {
                _currentChatId.value = null
            }
            _snackbarMessage.value = context.getString(R.string.chat_snackbar_deleted_one)
        }
    }
    
    fun deleteSelectedChats() {
        viewModelScope.launch {
            val ids = _selectedChatIds.value.toList()
            if (ids.isNotEmpty()) {
                chatDao.deleteChatsByIds(ids)
                // If current chat was in deleted list, clear it
                if (_currentChatId.value in ids) {
                    _currentChatId.value = null
                }
                _snackbarMessage.value = context.getString(R.string.vm_chats_deleted_count, ids.size)
            }
            clearSelection()
        }
    }

    fun sendMessage() {
        val message = _inputMessage.value
        val chatId = _currentChatId.value ?: return
        if (message.isBlank()) return

        viewModelScope.launch {
            _inputMessage.value = "" // Clear input
            
            // Handle Slash Commands
            if (message.trim() == "/refresh_context") {
                _snackbarMessage.value = context.getString(R.string.chat_snackbar_regenerating_context)
                chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "user", content = context.getString(R.string.chat_system_regenerating_context)))
                _isLoading.value = true

                try {
                    val result = chatGeminiService.generateUserContext()
                    result.onSuccess { summary ->
                        chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "system", content = context.getString(R.string.chat_system_context_updated, summary)))
                        _snackbarMessage.value = context.getString(R.string.chat_snackbar_context_updated)
                    }
                    result.onFailure { e ->
                        chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "system", content = context.getString(R.string.chat_system_context_failed, e.message ?: "")))
                        _snackbarMessage.value = context.getString(R.string.chat_snackbar_context_failed)
                    }
                } catch (e: Exception) {
                     android.util.Log.e("ChatViewModel", "Context generation error", e)
                } finally {
                    _isLoading.value = false
                }
                return@launch
            }

            // Insert user message immediately for instant UI feedback
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "user", content = message))
            
            _isLoading.value = true
            _isStreaming.value = true
            _streamingContent.value = ""
            _currentThought.value = ""
            _currentTool.value = null
            
            // Update title if it's the first message (or title is the default DB marker)
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == DEFAULT_CHAT_TITLE) {
                val newTitle = message.take(30) + if (message.length > 30) "..." else ""
                chatDao.updateChat(chat.copy(title = newTitle))
            }
            
            try {
                chatGeminiService.sendMessageStream(chatId, message).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Thinking -> {
                            _currentThought.value += event.text
                        }
                        is ChatStreamEvent.Content -> {
                            _streamingContent.value += event.text
                        }
                        is ChatStreamEvent.Done -> {
                            _isLoading.value = false
                            _isStreaming.value = false
                            _streamingContent.value = ""
                            _currentThought.value = ""
                        }
                        is ChatStreamEvent.Error -> {
                            _isLoading.value = false
                            _isStreaming.value = false
                            android.util.Log.e("ChatViewModel", "Stream error", event.error)
                            _snackbarMessage.value = context.getString(
                                R.string.chat_snackbar_error_with_message,
                                event.error.localizedMessage ?: event.error::class.java.simpleName
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                 _isLoading.value = false
                 _isStreaming.value = false
                 android.util.Log.e("ChatViewModel", "sendMessage failed", e)
                 _snackbarMessage.value = context.getString(R.string.chat_snackbar_send_failed, e.message ?: "")
            }
        }
    }

    fun saveChatAsMemory(chatId: Int) {
        viewModelScope.launch {
            android.util.Log.d("ChatViewModel", "saveChatAsMemory called for chatId: $chatId")
            val chat = chatDao.getChatById(chatId) ?: return@launch
            if (chat.isSavedAsMemory) {
                android.util.Log.d("ChatViewModel", "Chat already saved as memory, skipping")
                return@launch
            }

            // Use the cached messages from the UI state for reliability
            val messages = currentMessages.value
            android.util.Log.d("ChatViewModel", "Messages count: ${messages.size}")
            
            if (messages.isEmpty()) {
                android.util.Log.w("ChatViewModel", "No messages to save!")
                return@launch
            }
            
            val chatExport = messages.joinToString("\n") { "${it.role}: ${it.content}" }
            android.util.Log.d("ChatViewModel", "Chat export length: ${chatExport.length}")

            memoryRepository.saveChatMemory(chatExport)
            chatDao.updateChat(chat.copy(isSavedAsMemory = true))
            android.util.Log.d("ChatViewModel", "Chat saved as memory successfully")
        }
    }

    // ==================== MEDIA SUPPORT ====================

    // Snackbar state for feedback
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()
    
    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Send a message with audio attachment
     */
    fun sendAudioMessage(audioFilePath: String, additionalText: String? = null) {
        val chatId = _currentChatId.value ?: return

        viewModelScope.launch {
            // Insert user message immediately with audio indicator.
            // VOICE_MESSAGE_MARKER is a DB-persisted marker (English) — must remain stable.
            val displayText = additionalText?.takeIf { it.isNotBlank() } ?: VOICE_MESSAGE_MARKER
            chatDao.insertMessage(ChatMessageEntity(
                chatId = chatId,
                role = "user",
                content = displayText,
                audioFilePath = audioFilePath
            ))

            _isLoading.value = true
            _isStreaming.value = true
            _streamingContent.value = ""
            _currentThought.value = ""

            // Update title if first message
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == DEFAULT_CHAT_TITLE) {
                chatDao.updateChat(chat.copy(title = context.getString(R.string.chat_voice_chat_title)))
            }

            try {
                chatGeminiService.sendMessageWithMediaStream(
                    chatId = chatId,
                    message = additionalText,
                    audioUri = "file://$audioFilePath",
                    imageUri = null
                ).collect { event ->
                    handleStreamEvent(event)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _isStreaming.value = false
                android.util.Log.e("ChatViewModel", "Failed to send audio message", e)
                _snackbarMessage.value = context.getString(R.string.chat_snackbar_audio_failed)
            }
        }
    }
    
    private fun handleStreamEvent(event: ChatStreamEvent) {
        when (event) {
            is ChatStreamEvent.Thinking -> _currentThought.value += event.text
            is ChatStreamEvent.Content -> _streamingContent.value += event.text
            is ChatStreamEvent.Done -> {
                _isLoading.value = false
                _isStreaming.value = false
                _streamingContent.value = ""
                _currentThought.value = ""
            }
            is ChatStreamEvent.Error -> {
                _isLoading.value = false
                _isStreaming.value = false
                android.util.Log.e("ChatViewModel", "Stream error", event.error)
                _snackbarMessage.value = context.getString(
                    R.string.chat_snackbar_error_with_message,
                    event.error.localizedMessage ?: event.error::class.java.simpleName
                )
            }
        }
    }

    /**
     * Send a message with image or document attachment
     */
    /**
     * Send a message with image or document attachment
     */
    fun sendMediaMessage(mediaUri: String, additionalText: String? = null) {
        val chatId = _currentChatId.value ?: return

        viewModelScope.launch {
            _inputMessage.value = "" // Clear input

            // Insert user message immediately with media indicator.
            // ATTACHMENT_MARKER is a DB-persisted marker (English) — must remain stable.
            val displayText = additionalText?.takeIf { it.isNotBlank() } ?: ATTACHMENT_MARKER
            chatDao.insertMessage(ChatMessageEntity(
                chatId = chatId,
                role = "user",
                content = displayText,
                imageUri = mediaUri
            ))

            _isLoading.value = true
            _isStreaming.value = true
            _streamingContent.value = ""
            _currentThought.value = ""

            // Update title if first message
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == DEFAULT_CHAT_TITLE) {
                chatDao.updateChat(chat.copy(title = context.getString(R.string.chat_media_chat_title)))
            }

            try {
                chatGeminiService.sendMessageWithMediaStream(
                    chatId = chatId,
                    message = additionalText,
                    imageUri = mediaUri,
                    audioUri = null
                ).collect { event ->
                    handleStreamEvent(event)
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _isStreaming.value = false
                android.util.Log.e("ChatViewModel", "Failed to send media message", e)
                _snackbarMessage.value = context.getString(R.string.chat_snackbar_attachment_failed)
            }
        }
    }

    /**
     * Save an AI message as a note (memory)
     * This creates a text-based memory that will be processed by the AI worker
     */
    fun saveMessageAsNote(content: String) {
        viewModelScope.launch {
            try {
                memoryRepository.savePendingMemory(
                    imageUris = emptyList(),
                    audioUri = null,
                    userText = content
                )
                _snackbarMessage.value = context.getString(R.string.chat_snackbar_saved_note)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to save message as note", e)
                _snackbarMessage.value = context.getString(R.string.chat_snackbar_save_note_failed)
            }
        }
    }
    // ==================== AUDIO PLAYBACK ====================
    
    private var mediaPlayer: android.media.MediaPlayer? = null
    
    private val _currentPlayingAudioPath = MutableStateFlow<String?>(null)
    val currentPlayingAudioPath = _currentPlayingAudioPath.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _audioProgress = MutableStateFlow(0f)
    val audioProgress = _audioProgress.asStateFlow()
    
    private val _audioCurrentTime = MutableStateFlow("00:00")
    val audioCurrentTime = _audioCurrentTime.asStateFlow()
    
    private val _audioTotalTime = MutableStateFlow("00:00")
    val audioTotalTime = _audioTotalTime.asStateFlow()
    
    private var playbackJob: kotlinx.coroutines.Job? = null
    
    fun playAudio(path: String) {
        if (_currentPlayingAudioPath.value == path && _isPlaying.value) {
            pauseAudio()
            return
        }
        
        stopAudio() // Stop any previous playback
        
        try {
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    stopAudio()
                }
            }
            _currentPlayingAudioPath.value = path
            _isPlaying.value = true
            
            // Start progress polling
            startProgressPolling()
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Failed to play audio: $path", e)
            _snackbarMessage.value = context.getString(R.string.chat_snackbar_audio_play_failed)
            stopAudio()
        }
    }
    
    fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }
    
    fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _currentPlayingAudioPath.value = null
        _isPlaying.value = false
        _audioProgress.value = 0f
        _audioCurrentTime.value = "00:00"
        playbackJob?.cancel()
    }
    
    private fun startProgressPolling() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value && mediaPlayer != null) {
                try {
                    val current = mediaPlayer!!.currentPosition
                    val total = mediaPlayer!!.duration
                    if (total > 0) {
                        _audioProgress.value = current.toFloat() / total.toFloat()
                        _audioCurrentTime.value = formatTime(current)
                        _audioTotalTime.value = formatTime(total)
                    }
                } catch (e: Exception) {
                    // Handle illegal state if player released
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }
    
    fun seekAudio(progress: Float) {
        mediaPlayer?.let { player ->
            if (player.isPlaying || _isPlaying.value) {
                val newPos = (player.duration * progress).toInt()
                player.seekTo(newPos)
                _audioProgress.value = progress
            }
        }
    }
    
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
