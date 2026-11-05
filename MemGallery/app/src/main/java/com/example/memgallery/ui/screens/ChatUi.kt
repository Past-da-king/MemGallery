package com.example.memgallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.ChatMessageEntity
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.components.MarkdownText
import com.example.memgallery.ui.components.ThinkingProcessAccordion
import com.example.memgallery.ui.components.ToolExecutionIndicator
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.colors
import com.example.memgallery.ui.design.displayTitle
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.viewmodels.ChatViewModel
import androidx.compose.material3.Surface

/** The Ask page: header, conversation and input. Stateless so it can be drawn with sample data. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatLayout(
    title: String,
    subtitle: String,
    messages: List<ChatMessageEntity>,
    displayedMemories: Map<Int, MemoryEntity>,
    inputMessage: String,
    isLoading: Boolean,
    isStreaming: Boolean,
    streamingContent: String,
    currentThought: String,
    currentTool: String?,
    isRecording: Boolean,
    recordingAmplitudes: List<Float>,
    recordingTime: Long,
    playingPath: String?,
    isPlaying: Boolean,
    audioProgress: Float,
    audioCurrentTime: String,
    audioTotalTime: String,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onSaveChat: () -> Unit,
    onSaveAsNote: (ChatMessageEntity) -> Unit,
    onCopy: (ChatMessageEntity) -> Unit,
    onPlayAudio: (String) -> Unit,
    onPauseAudio: () -> Unit,
    onSeekAudio: (Float) -> Unit,
    onMemoryClick: (MemoryEntity) -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.surface).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(title, subtitle, hasMessages = messages.isNotEmpty(), onHistory, onNewChat, onSaveChat)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !isLoading) {
                    EmptyStateWelcome()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (isLoading || isStreaming || currentTool != null) {
                            item {
                                Column(Modifier.fillMaxWidth()) {
                                    currentTool?.let { ToolExecutionIndicator(toolName = it) }
                                    if (currentThought.isNotEmpty() && streamingContent.isEmpty()) ThinkingProcessAccordion(thought = currentThought)
                                    if (streamingContent.isNotEmpty()) {
                                        MarkdownText(markdown = streamingContent, style = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface))
                                    } else if (currentThought.isEmpty() && currentTool == null && isLoading) {
                                        LoadingIndicator(Modifier.size(40.dp))
                                    }
                                }
                            }
                        }
                        items(messages.reversed()) { message ->
                            val mine = playingPath == message.audioFilePath
                            MessageBubble(
                                message = message,
                                displayedMemories = displayedMemories,
                                onSaveAsNote = { onSaveAsNote(message) },
                                onCopy = { onCopy(message) },
                                isPlaying = isPlaying && mine,
                                audioProgress = if (mine) audioProgress else 0f,
                                audioCurrentTime = if (mine) audioCurrentTime else "00:00",
                                audioTotalTime = if (mine) audioTotalTime else "00:00",
                                onPlayAudio = onPlayAudio,
                                onPauseAudio = onPauseAudio,
                                onSeekAudio = onSeekAudio,
                                onMemoryClick = onMemoryClick
                            )
                        }
                    }
                }
            }
            if (messages.isEmpty() && !isLoading) {
                val suggestions = listOf(
                    stringResource(R.string.chat_suggest_today) to Icons.Rounded.Search,
                    stringResource(R.string.chat_suggest_week) to Icons.Rounded.EditNote,
                    stringResource(R.string.chat_suggest_tasks) to Icons.Rounded.AutoAwesome,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(suggestions) { (text, icon) ->
                        SuggestionChip(text, icon) { onSuggestion(text) }
                    }
                }
            }
            ChatInputBar(
                inputMessage = inputMessage,
                onValueChange = onValueChange,
                onSend = onSend,
                onAttachClick = onAttach,
                onMicClick = onMic,
                isLoading = isLoading,
                isRecording = isRecording,
                recordingAmplitudes = recordingAmplitudes,
                recordingDurationSeconds = recordingTime
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun SuggestionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
    }
}

@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    hasMessages: Boolean,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onSaveChat: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().appStatusBarsPadding().height(72.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIcon(Icons.Rounded.History, stringResource(R.string.chat_cd_history), onHistory)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = scheme.onSurfaceVariant)
        }
        if (hasMessages) HeaderIcon(Icons.Rounded.Save, stringResource(R.string.chat_cd_save), onSaveChat)
        HeaderIcon(Icons.Rounded.EditNote, stringResource(R.string.chat_cd_new_chat), onNewChat)
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun EmptyStateWelcome() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(88.dp).clip(MemShape.softBurst).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.chat_empty_body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    displayedMemories: Map<Int, MemoryEntity> = emptyMap(),
    onSaveAsNote: () -> Unit,
    onCopy: () -> Unit,
    isPlaying: Boolean = false,
    audioProgress: Float = 0f,
    audioCurrentTime: String = "00:00",
    audioTotalTime: String = "00:00",
    onPlayAudio: (String) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    onSeekAudio: (Float) -> Unit = {},
    onMemoryClick: (MemoryEntity) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val isUser = message.role == "user"
    // Placeholders for media are DB markers kept in English in ChatViewModel; compare, do not localize.
    val isPlaceholder = message.content == ChatViewModel.VOICE_MESSAGE_MARKER || message.content == ChatViewModel.ATTACHMENT_MARKER
    val hasMedia = message.audioFilePath != null || message.imageUri != null
    val showTextBubble = !isPlaceholder || !hasMedia

    if (isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (message.audioFilePath != null) {
                com.example.memgallery.ui.components.AudioPlayer(
                    audioUri = message.audioFilePath,
                    isPlaying = isPlaying,
                    progress = audioProgress,
                    durationString = audioTotalTime,
                    currentPositionString = audioCurrentTime,
                    onPlayClick = { if (isPlaying) onPauseAudio() else onPlayAudio(message.audioFilePath) },
                    onSeek = onSeekAudio
                )
                Spacer(Modifier.height(4.dp))
            }
            if (message.imageUri != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp).widthIn(max = 280.dp)
                ) {
                    if (message.imageUri.endsWith(".pdf", ignoreCase = true)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AttachFile, stringResource(R.string.chat_cd_attachment), tint = scheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.chat_label_attachment), style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer)
                        }
                    } else {
                        Image(
                            memoryPainter(message.imageUri), stringResource(R.string.chat_cd_attachment),
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        )
                    }
                }
            }
            if (showTextBubble) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp, bottomStart = 28.dp),
                    color = scheme.primary,
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    Text(
                        message.content,
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onPrimary
                    )
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            val sources = message.displayedMemoryIds.orEmpty().mapNotNull { displayedMemories[it] }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(MemShape.cookie).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
                }
                if (sources.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHigh).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (sources.size == 1) stringResource(R.string.chat_searched_one) else stringResource(R.string.chat_searched_memories, sources.size),
                            style = MaterialTheme.typography.titleSmall, color = scheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MarkdownText(markdown = message.content, style = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, lineHeight = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)))

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sources.size) { i -> SourceCard(i + 1, sources[i]) { onMemoryClick(sources[i]) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onCopy), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.chat_cd_copy), tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                FilledTonalButton(onClick = onSaveAsNote, modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Rounded.NoteAdd, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_cd_save_as_note), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** A memory the answer came from: shaped thumbnail with a number badge and its title. */
@Composable
private fun SourceCard(number: Int, memory: MemoryEntity, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (number % 2 == 1) RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 16.dp, bottomEnd = 16.dp) else MemShape.softBurst
    Column(Modifier.width(176.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(176.dp).clip(shape)) {
            if (memory.imageUri != null) {
                Image(memoryPainter(memory.imageUri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(scheme.surfaceContainerHigh))
            } else {
                val (bg, fg) = Tone.entries[memory.id.mod(Tone.entries.size)].colors()
                Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                    Text(
                        memory.displayTitle(""), Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(memory.displayTitle(""), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The input pill: attach, text, mic, send. Recording swaps the text for a live waveform. */
@Composable
fun ChatInputBar(
    inputMessage: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onMicClick: () -> Unit,
    isLoading: Boolean,
    isRecording: Boolean,
    recordingAmplitudes: List<Float>,
    recordingDurationSeconds: Long = 0L
) {
    val scheme = MaterialTheme.colorScheme
    val canSend = inputMessage.isNotBlank() && !isLoading && !isRecording
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).appNavigationBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .shadow(4.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !isRecording) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !isLoading, onClick = onAttachClick), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.chat_cd_attach), tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                }
            }
            Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                if (isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
                        val transition = rememberInfiniteTransition(label = "pulse")
                        val alpha by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
                        Box(Modifier.size(10.dp).background(scheme.error.copy(alpha = alpha), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        MiniWaveform(amplitudes = recordingAmplitudes, modifier = Modifier.weight(1f).height(32.dp), color = scheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text(formatDuration(recordingDurationSeconds * 1000), style = MaterialTheme.typography.labelMedium, color = scheme.error)
                    }
                } else {
                    TextField(
                        value = inputMessage,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.chat_input_placeholder), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = scheme.primary
                        ),
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
                    )
                }
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !isLoading || isRecording, onClick = onMicClick), contentAlignment = Alignment.Center) {
                if (isRecording) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(scheme.errorContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Stop, stringResource(R.string.chat_cd_stop_recording), tint = scheme.onErrorContainer)
                    }
                } else {
                    Icon(Icons.Rounded.Mic, stringResource(R.string.chat_cd_record_audio), tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.chat_cd_send), tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}
