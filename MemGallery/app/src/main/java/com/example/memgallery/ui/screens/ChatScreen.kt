package com.example.memgallery.ui.screens

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.ui.viewmodels.ChatViewModel
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val chats by viewModel.chats.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val inputMessage by viewModel.inputMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Audio recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingAmplitudes by remember { mutableStateOf(List(30) { 0f }) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

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

    // Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val (recorder, file) = startAudioRecording(context)
                mediaRecorder = recorder
                recordingFile = file
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Audio Amplitude Polling
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(50) // Update every 50ms
                mediaRecorder?.maxAmplitude?.let { maxAmp ->
                    // Normalize amplitude (0-32767) to 0-1 range with some boosting
                    val norm = (maxAmp / 32767f).coerceIn(0f, 1f)
                    // Add to list and keep size constant
                    recordingAmplitudes = (recordingAmplitudes + norm).takeLast(40)
                }
            }
        } else {
            recordingAmplitudes = List(40) { 0f }
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

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            EnhancedChatInputBar(
                inputMessage = inputMessage,
                onValueChange = viewModel::updateInputMessage,
                onSend = { viewModel.sendMessage() },
                onAttachClick = { showAttachMenu = true },
                onMicClick = {
                    if (isRecording) {
                        // Stop recording
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        mediaRecorder = null
                        isRecording = false
                        
                        // Send the file
                        recordingFile?.absolutePath?.let { path ->
                            viewModel.sendAudioMessage(path)
                        }
                        recordingFile = null
                    } else {
                        // Check permission and start recording
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                            == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val (recorder, file) = startAudioRecording(context)
                                mediaRecorder = recorder
                                recordingFile = file
                                isRecording = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                isLoading = isLoading,
                isRecording = isRecording,
                recordingAmplitudes = recordingAmplitudes
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Custom Header
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
                        contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (isLoading) {
                            item { LoadingIndicator() }
                        }
                        items(currentMessages.reversed()) { message ->
                            MessageBubble(
                                message = message,
                                onSaveAsNote = { viewModel.saveMessageAsNote(message.content) },
                                onCopy = { 
                                    clipboardManager.setText(AnnotatedString(message.content))
                                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Attachment Menu
    if (showAttachMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Attach",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Image") },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showAttachMenu = false
                        imagePickerLauncher.launch("image/*")
                    }
                )
                ListItem(
                    headlineContent = { Text("Document (PDF)") },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showAttachMenu = false
                        documentPickerLauncher.launch(arrayOf("application/pdf", "text/*"))
                    }
                )
            }
        }
    }
    // Selection state for deletion
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val selectedChatIds by viewModel.selectedChatIds.collectAsState()

    // History Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showHistorySheet = false
                viewModel.clearSelection()
            },
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
}

@Composable
fun EnhancedChatInputBar(
    inputMessage: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onMicClick: () -> Unit,
    isLoading: Boolean,
    isRecording: Boolean,
    recordingAmplitudes: List<Float>
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp, // Flat design, container handles shape
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding() // Ensure it respects gesture bar
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(containerColor, RoundedCornerShape(28.dp)) // Pill shape
                    .padding(horizontal = 8.dp, vertical = 4.dp), // Padding inside the pill
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action (Attach)
                AnimatedVisibility(visible = !isRecording) {
                    IconButton(
                        onClick = onAttachClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Center Content (TextField or Recording UI)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isRecording) {
                        // Recording UI
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Pulsing Dot
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha), CircleShape)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Waveform
                            MiniWaveform(
                                amplitudes = recordingAmplitudes, 
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = formatDuration(recordingAmplitudes.size * 50L), // Approx duration
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // Text Input
                        TextField(
                            value = inputMessage,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { 
                                Text(
                                    "Ask Gemini...", 
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                ) 
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = primaryColor
                            ),
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (inputMessage.isNotBlank() && !isLoading) onSend() })
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Action (Mic or Send)
                val isSendVisible = inputMessage.isNotBlank() && !isRecording
                
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = when {
                            isRecording -> "Stop"
                            isSendVisible -> "Send"
                            else -> "Mic"
                        },
                        label = "Action Button"
                    ) { state ->
                        when (state) {
                            "Stop" -> {
                                IconButton(
                                    onClick = onMicClick,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = "Stop Recording",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            "Send" -> {
                                IconButton(
                                    onClick = onSend,
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(primaryColor, CircleShape)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            "Mic" -> {
                                IconButton(
                                    onClick = onMicClick,
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Record Audio",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
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

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
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
            text = "I can analyze your memories, answer questions, or just chat.\nTry sending an image, voice note, or document!",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    onSaveAsNote: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    
    if (isUser) {
        // User messages
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            // Show audio indicator if present
            if (message.audioFilePath != null) {
                CompactAudioPlayer(audioPath = message.audioFilePath)
                Spacer(modifier = Modifier.height(4.dp))
            }
            // Show image indicator if present
            if (message.imageUri != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, "Attachment", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attachment", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    } else {
        // AI messages
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            ChatMarkdownText(
                markdown = message.content,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            // Action buttons
            Row(
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                }
                FilledTonalIconButton(
                    onClick = onSaveAsNote,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.NoteAdd, "Save as Note", modifier = Modifier.size(16.dp))
                }
            }
        }
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
                contentDescription = "Voice",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "🎤 Voice message",
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
            "Thinking...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatMarkdownText(markdown: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle) {
    val context = LocalContext.current
    val onSurfaceColor = MaterialTheme.colorScheme.onBackground
    val markwon = remember {
        Markwon.builder(context).build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(onSurfaceColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
                setLineSpacing(0f, 1.3f)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
            .padding(bottom = 32.dp)
    ) {
        // Header - changes based on selection mode
        if (selectionModeActive) {
            // Selection Mode Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
                Text(
                    "${selectedChatIds.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete All")
                }
            }
        } else {
            Text(
                "Chat History",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(16.dp)
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // New Chat button (hidden in selection mode)
            if (!selectionModeActive) {
                item {
                    ListItem(
                        headlineContent = { Text("New Chat") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onNewChat)
                    )
                    HorizontalDivider()
                }
            }
            
            items(chats, key = { it.id }) { chat ->
                val isSelected = chat.id in selectedChatIds
                
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
                    leadingContent = if (selectionModeActive) {
                        {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection(chat.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    } else null,
                    trailingContent = if (!selectionModeActive) {
                        {
                            IconButton(
                                onClick = { onDeleteChat(chat.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                if (selectionModeActive) {
                                    onToggleSelection(chat.id)
                                } else {
                                    onChatSelected(chat.id)
                                }
                            },
                            onLongClick = {
                                if (!selectionModeActive) {
                                    onToggleSelection(chat.id) // This will trigger selection mode
                                }
                            }
                        )
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                chat.id == currentChatId -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }
    }
}

// Audio recording helpers
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
