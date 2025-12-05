package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.local.dao.ChatDao
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.remote.ChatGeminiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val chatGeminiService: ChatGeminiService,
    private val memoryRepository: com.example.memgallery.data.repository.MemoryRepository
) : ViewModel() {

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _inputMessage = MutableStateFlow("")
    val inputMessage = _inputMessage.asStateFlow()

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
            val newChat = ChatEntity(title = "New Chat")
            val id = chatDao.insertChat(newChat).toInt()
            _currentChatId.value = id
        }
    }

    fun sendMessage() {
        val message = _inputMessage.value
        val chatId = _currentChatId.value ?: return
        if (message.isBlank()) return

        viewModelScope.launch {
            _inputMessage.value = "" // Clear input
            
            // Insert user message immediately for instant UI feedback
            chatDao.insertMessage(ChatMessageEntity(chatId = chatId, role = "user", content = message))
            
            _isLoading.value = true
            
            // Update title if it's the first message (or title is "New Chat")
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == "New Chat") {
                val newTitle = message.take(30) + if (message.length > 30) "..." else ""
                chatDao.updateChat(chat.copy(title = newTitle))
            }
            
            val result = chatGeminiService.sendMessage(chatId, message)
            _isLoading.value = false
            
            if (result.isFailure) {
                // Handle error - could show error state or retry option
                android.util.Log.e("ChatViewModel", "Failed to send message", result.exceptionOrNull())
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
            // Insert user message immediately with audio indicator
            val displayText = additionalText?.takeIf { it.isNotBlank() } ?: "🎤 Voice message"
            chatDao.insertMessage(ChatMessageEntity(
                chatId = chatId, 
                role = "user", 
                content = displayText,
                audioFilePath = audioFilePath
            ))
            
            _isLoading.value = true
            
            // Update title if first message
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == "New Chat") {
                chatDao.updateChat(chat.copy(title = "Voice Chat"))
            }
            
            val result = chatGeminiService.sendMessageWithMedia(
                chatId = chatId,
                message = additionalText,
                audioUri = "file://$audioFilePath"
            )
            _isLoading.value = false
            
            if (result.isFailure) {
                android.util.Log.e("ChatViewModel", "Failed to send audio message", result.exceptionOrNull())
                _snackbarMessage.value = "Failed to send audio message"
            }
        }
    }

    /**
     * Send a message with image or document attachment
     */
    fun sendMediaMessage(mediaUri: String, additionalText: String? = null) {
        val chatId = _currentChatId.value ?: return

        viewModelScope.launch {
            _inputMessage.value = "" // Clear input
            
            // Insert user message immediately with media indicator
            val displayText = additionalText?.takeIf { it.isNotBlank() } ?: "📎 Attachment"
            chatDao.insertMessage(ChatMessageEntity(
                chatId = chatId, 
                role = "user", 
                content = displayText,
                imageUri = mediaUri
            ))
            
            _isLoading.value = true
            
            // Update title if first message
            val chat = chatDao.getChatById(chatId)
            if (chat != null && chat.title == "New Chat") {
                chatDao.updateChat(chat.copy(title = "Media Chat"))
            }
            
            val result = chatGeminiService.sendMessageWithMedia(
                chatId = chatId,
                message = additionalText,
                imageUri = mediaUri
            )
            _isLoading.value = false
            
            if (result.isFailure) {
                android.util.Log.e("ChatViewModel", "Failed to send media message", result.exceptionOrNull())
                _snackbarMessage.value = "Failed to send attachment"
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
                    imageUri = null,
                    audioUri = null,
                    userText = content
                )
                _snackbarMessage.value = "Saved as note"
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to save message as note", e)
                _snackbarMessage.value = "Failed to save note"
            }
        }
    }
}
