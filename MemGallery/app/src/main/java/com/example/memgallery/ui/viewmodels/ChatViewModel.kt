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
                // Handle error
            }
        }
    }

    fun saveChatAsMemory(chatId: Int) {
        viewModelScope.launch {
            val chat = chatDao.getChatById(chatId) ?: return@launch
            if (chat.isSavedAsMemory) return@launch // Already saved

            val messages = chatDao.getMessagesForChat(chatId).first()
            val chatExport = messages.joinToString("\n") { "${it.role}: ${it.content}" }

            memoryRepository.saveChatMemory(chatExport)
            chatDao.updateChat(chat.copy(isSavedAsMemory = true))
        }
    }
}
