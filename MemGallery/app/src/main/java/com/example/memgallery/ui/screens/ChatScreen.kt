package com.example.memgallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val inputMessage by viewModel.inputMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    // Initialize new chat if needed
    LaunchedEffect(Unit) {
        if (currentChatId == null) {
            viewModel.createNewChat()
        }
    }

    Scaffold(
        bottomBar = {
            ChatInputBar(
                inputMessage = inputMessage,
                onValueChange = viewModel::updateInputMessage,
                onSend = {
                    viewModel.sendMessage()
                    focusManager.clearFocus()
                },
                isLoading = isLoading
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Custom Header matching GalleryScreen style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentChatTitle = chats.find { it.id == currentChatId }?.title ?: "New Chat"
                Text(
                    text = currentChatTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // History Icon (Styled like Settings)
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Chat Icon (Visible only if messages exist)
                    if (currentMessages.isNotEmpty()) {
                        IconButton(
                            onClick = { currentChatId?.let { viewModel.saveChatAsMemory(it) } },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save Chat",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Chat Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (currentMessages.isEmpty()) {
                    EmptyStateWelcome()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(currentMessages) { message ->
                            MessageBubble(message = message)
                        }
                        if (isLoading) {
                            item {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState()
        ) {
            ChatHistoryList(
                chats = chats,
                currentChatId = currentChatId,
                onChatSelected = { chatId ->
                    viewModel.selectChat(chatId)
                    showHistorySheet = false
                },
                onNewChat = {
                    viewModel.createNewChat()
                    showHistorySheet = false
                }
            )
        }
    }
}

@Composable
fun EmptyStateWelcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How can I help you?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "I can analyze your memories, answer questions, or just chat.",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatInputBar(
    inputMessage: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputMessage,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                placeholder = { Text("Ask anything...", style = MaterialTheme.typography.bodyLarge) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (inputMessage.isNotBlank() && !isLoading) onSend() })
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onSend,
                enabled = inputMessage.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (inputMessage.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputMessage.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                text = "AI Assistant",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )
        }
        
        Surface(
            shape = if (isUser) {
                RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
            } else {
                RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
            },
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp
                ),
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatHistoryList(
    chats: List<ChatEntity>,
    currentChatId: Int?,
    onChatSelected: (Int) -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Chat History",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("New Chat") },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNewChat)
                )
                HorizontalDivider()
            }
            items(chats) { chat ->
                ListItem(
                    headlineContent = { 
                        Text(
                            chat.title.ifBlank { "New Chat" },
                            fontWeight = if (chat.id == currentChatId) FontWeight.Bold else FontWeight.Normal
                        ) 
                    },
                    supportingContent = if (chat.summary != null) {
                        { Text(chat.summary, maxLines = 1) }
                    } else null,
                    modifier = Modifier
                        .clickable { onChatSelected(chat.id) }
                        .background(
                            if (chat.id == currentChatId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                            else Color.Transparent
                        )
                )
            }
        }
    }
}
