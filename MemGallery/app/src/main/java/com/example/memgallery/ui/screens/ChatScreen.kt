package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.MemSheet
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.ui.viewmodels.ChatViewModel
import com.example.memgallery.navigation.Screen
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.memgallery.ui.components.ThinkingProcessAccordion
import com.example.memgallery.ui.components.ToolExecutionIndicator
import com.example.memgallery.ui.components.MarkdownText
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel(),
    audioViewModel: com.example.memgallery.ui.viewmodels.AudioCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val chats by viewModel.chats.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val displayedMemories by viewModel.displayedMemories.collectAsState()
    val inputMessage by viewModel.inputMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Audio Playback State
    val currentPlayingAudioPath by viewModel.currentPlayingAudioPath.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val audioCurrentTime by viewModel.audioCurrentTime.collectAsState()
    val audioTotalTime by viewModel.audioTotalTime.collectAsState()
    
    // Streaming State
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val currentThought by viewModel.currentThought.collectAsState()
    val currentTool by viewModel.currentTool.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val copiedToClipboardMessage = stringResource(R.string.chat_snackbar_copied)

    // Audio recording state
    // Audio recording state from ViewModel
    val isRecording by audioViewModel.isRecording.collectAsState()
    val recordingTime by audioViewModel.recordingTime.collectAsState()
    val recordingAmplitudes by audioViewModel.amplitudes.collectAsState()
    val recordedFilePath by audioViewModel.recordedFilePath.collectAsState()
    val recordingError by audioViewModel.error.collectAsState()
    var showPermissionSheet by remember { mutableStateOf(false) }

    // Image/Document picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.sendMediaMessage(it.toString(), inputMessage.takeIf { it.isNotBlank() })
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { 
            viewModel.sendMediaMessage(it.toString(), inputMessage.takeIf { it.isNotBlank() })
        }
    }



    // ... (existing code) ...


    
    // ... (existing code) ...



    // Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioViewModel.startRecording()
            showPermissionSheet = false
        }
    }

    // Handle finished recording
    LaunchedEffect(recordedFilePath) {
        recordedFilePath?.let { path ->
            viewModel.sendAudioMessage(path)
            // Reset (optional, depending on VM behavior, but VM clears it usually on new start or we can ignore duplicates)
        }
    }
    
    // Handle recording errors
    LaunchedEffect(recordingError) {
        recordingError?.let {
            snackbarHostState.showSnackbar(it)
            audioViewModel.clearError()
        }
    }

    // Show snackbar when message changes
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Initialize new chat if needed
    LaunchedEffect(Unit) {
        if (currentChatId == null) {
            viewModel.createNewChat()
        }
    }

    val settingsViewModel: com.example.memgallery.ui.viewmodels.SettingsViewModel = hiltViewModel()
    val aiProvider by settingsViewModel.aiProvider.collectAsState()
    val subtitle = when (aiProvider) {
        "LOCAL" -> stringResource(R.string.processing_provider_local)
        "GROQ" -> "Groq"
        "GEMINI" -> "Gemini"
        else -> stringResource(R.string.settings_ai_provider_custom)
    }

    ChatLayout(
        title = chats.find { it.id == currentChatId }?.title ?: stringResource(R.string.chat_default_title),
        subtitle = subtitle,
        messages = currentMessages,
        displayedMemories = displayedMemories,
        inputMessage = inputMessage,
        isLoading = isLoading,
        isStreaming = isStreaming,
        streamingContent = streamingContent,
        currentThought = currentThought,
        currentTool = currentTool,
        isRecording = isRecording,
        recordingAmplitudes = recordingAmplitudes,
        recordingTime = recordingTime,
        playingPath = currentPlayingAudioPath,
        isPlaying = isPlaying,
        audioProgress = audioProgress,
        audioCurrentTime = audioCurrentTime,
        audioTotalTime = audioTotalTime,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onValueChange = viewModel::updateInputMessage,
        onSend = { viewModel.sendMessage() },
        onAttach = { showAttachMenu = true },
        onMic = {
            if (isRecording) {
                audioViewModel.stopRecording()
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioViewModel.startRecording()
            } else {
                showPermissionSheet = true
            }
        },
        onHistory = { showHistorySheet = true },
        onNewChat = { viewModel.createNewChat() },
        onSaveChat = { currentChatId?.let { viewModel.saveChatAsMemory(it) } },
        onSaveAsNote = { viewModel.saveMessageAsNote(it.content) },
        onCopy = {
            clipboardManager.setText(AnnotatedString(it.content))
            scope.launch { snackbarHostState.showSnackbar(copiedToClipboardMessage) }
        },
        onPlayAudio = viewModel::playAudio,
        onPauseAudio = viewModel::pauseAudio,
        onSeekAudio = viewModel::seekAudio,
        onMemoryClick = { memory -> navController.navigate(Screen.Detail.createRoute(memory.id)) },
        onSuggestion = { viewModel.updateInputMessage(it); viewModel.sendMessage() },
    )

    // Attachment Menu
    if (showAttachMenu) {
        MemSheet(
            onDismissRequest = { showAttachMenu = false },
        ) {
            PremiumAttachmentSheet(
                onDismiss = { showAttachMenu = false },
                onImageClick = {
                    showAttachMenu = false
                    imagePickerLauncher.launch("image/*")
                },
                onDocumentClick = {
                    showAttachMenu = false
                    documentPickerLauncher.launch(arrayOf("application/pdf", "text/*"))
                }
            )
        }
    }
    // Selection state for deletion
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val selectedChatIds by viewModel.selectedChatIds.collectAsState()
    
    // History Sheet
    if (showHistorySheet) {
        MemSheet(
            onDismissRequest = { 
                showHistorySheet = false
                viewModel.clearSelection()
            },
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
                },
                selectionModeActive = selectionModeActive,
                selectedChatIds = selectedChatIds,
                onToggleSelection = viewModel::toggleChatSelection,
                onDeleteChat = viewModel::deleteChat,
                onDeleteSelected = viewModel::deleteSelectedChats,
                onClearSelection = viewModel::clearSelection
            )
        }
    }

    if (showPermissionSheet) {
        MemSheet(
            onDismissRequest = { showPermissionSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.chat_sheet_mic_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.chat_sheet_mic_body),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text(stringResource(R.string.common_grant_permission))
                }
                TextButton(
                    onClick = { showPermissionSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_not_now))
                }
            }
        }
    }
}


@Composable
fun MiniWaveform(
    amplitudes: List<Float>, 
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val barWidth = 4.dp.toPx()
        val gap = 2.dp.toPx()
        val totalWidth = size.width
        val maxBars = (totalWidth / (barWidth + gap)).toInt()
        
        // Take the last N amplitudes that fit
        val visibleAmplitudes = amplitudes.takeLast(maxBars)
        
        visibleAmplitudes.forEachIndexed { index, amplitude ->
            // Animate height based on amplitude
            val barHeight = (size.height * amplitude).coerceAtLeast(4.dp.toPx())
            val x = index * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            
            drawRoundRect(
                color = color.copy(alpha = 0.5f + (amplitude * 0.5f)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

internal fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}






@Composable
fun PremiumAttachmentSheet(
    onDismiss: () -> Unit,
    onImageClick: () -> Unit,
    onDocumentClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            stringResource(R.string.chat_sheet_share_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOption(
                icon = Icons.Default.Image,
                label = stringResource(R.string.chat_attachment_gallery),
                color = MaterialTheme.colorScheme.primary,
                onClick = onImageClick
            )
            AttachmentOption(
                icon = Icons.Default.Description,
                label = stringResource(R.string.chat_attachment_document),
                color = MaterialTheme.colorScheme.primary,
                onClick = onDocumentClick
            )
        }
    }
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
fun CompactAudioPlayer(audioPath: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = stringResource(R.string.chat_cd_voice),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.chat_voice_message_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.chat_thinking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryList(
    chats: List<ChatEntity>,
    currentChatId: Int?,
    onChatSelected: (Int) -> Unit,
    onNewChat: () -> Unit,
    selectionModeActive: Boolean = false,
    selectedChatIds: Set<Int> = emptySet(),
    onToggleSelection: (Int) -> Unit = {},
    onDeleteChat: (Int) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        if (selectionModeActive) {
            // Selection mode — soft secondary tint, not an alarm-red bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.chat_cd_cancel_selection),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gallery_selection_count, selectedChatIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.chat_selection_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onDeleteSelected,
                    enabled = selectedChatIds.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_delete))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_section_conversations),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (chats.isEmpty()) stringResource(R.string.chat_no_chats)
                           else if (chats.size == 1) stringResource(R.string.vm_chats_count_one, chats.size)
                           else stringResource(R.string.vm_chats_count_many, chats.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Promoted "Start new chat" tile
        if (!selectionModeActive) {
            Surface(
                onClick = onNewChat,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_new_chat_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.chat_new_chat_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (chats.isEmpty() && !selectionModeActive) {
            ChatHistoryEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chats, key = { it.id }) { chat ->
                    val isSelected = chat.id in selectedChatIds
                    val isActive = chat.id == currentChatId
                    ChatHistoryRow(
                        chat = chat,
                        isActive = isActive,
                        isSelectionMode = selectionModeActive,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionModeActive) onToggleSelection(chat.id)
                            else onChatSelected(chat.id)
                        },
                        onLongClick = {
                            if (!selectionModeActive) onToggleSelection(chat.id)
                        },
                        onDeleteClick = { onDeleteChat(chat.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistoryRow(
    chat: ChatEntity,
    isActive: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val borderWidth = if (isSelected || isActive) 1.5.dp else 0.dp

    val displayTitle = chat.title.ifBlank { stringResource(R.string.chat_history_fallback_title) }
    val initial = displayTitle.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar — letter chip; flips to a checkbox in selection mode
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.chat_active_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = chat.summary?.takeIf { it.isNotBlank() }
                    ?: relativeTime(chat.timestamp)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Trailing: time on the right when there's a summary, or a delete affordance
            if (!isSelectionMode) {
                if (chat.summary?.isNotBlank() == true) {
                    Text(
                        text = relativeTime(chat.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.chat_cd_delete_chat),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chat_no_conversations_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.chat_no_conversations_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun relativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = (now - timestampMs).coerceAtLeast(0L)
    val sec = diffMs / 1000
    val min = sec / 60
    val hours = min / 60
    val days = hours / 24
    return when {
        sec < 45 -> stringResource(R.string.chat_time_now)
        min < 60 -> stringResource(R.string.chat_time_minutes, min.toInt())
        hours < 24 -> stringResource(R.string.chat_time_hours, hours.toInt())
        days < 2 -> stringResource(R.string.chat_time_yesterday)
        days < 7 -> stringResource(R.string.chat_time_days, days.toInt())
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}

// Audio recording helpers
private fun startAudioRecording(
    context: android.content.Context
): Pair<MediaRecorder, File> {
    val fileName = "CHAT_AUD_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
    val outputFile = File(context.externalCacheDir, fileName)

    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(outputFile.absolutePath)
        prepare()
        start()
    }

    return Pair(recorder, outputFile)
}
