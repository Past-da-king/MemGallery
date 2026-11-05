package com.example.memgallery.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.data.remote.dto.ActionDto
import com.example.memgallery.ui.components.AudioPlayer
import com.example.memgallery.ui.components.FullscreenImageViewer
import com.example.memgallery.ui.components.MarkdownText
import com.example.memgallery.ui.components.sheets.AddTaskSheet
import com.example.memgallery.ui.components.sheets.CreateCollectionSheet
import com.example.memgallery.ui.components.sheets.SelectCollectionSheet
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.ConfirmSheet
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.appNavigationBarsPadding
import com.example.memgallery.ui.design.colors
import com.example.memgallery.ui.design.displayTitle
import com.example.memgallery.ui.design.kind
import com.example.memgallery.ui.design.MemoryKind
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.viewmodels.MemoryDetailViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MemoryDetailScreen(
    navController: NavController,
    memoryId: Int,
    viewModel: MemoryDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(memoryId) { viewModel.loadMemory(memoryId) }
    val memory by viewModel.memory.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val audioCurrentTime by viewModel.audioCurrentTime.collectAsState()
    val audioTotalTime by viewModel.audioTotalTime.collectAsState()
    val isLocalAi by viewModel.isLocalAi.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val context = LocalContext.current

    val pendingDeletionIntent by viewModel.pendingDeletionIntent.collectAsState()
    val deletionComplete by viewModel.deletionComplete.collectAsState()

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeletionConfirmedExternally()
        else viewModel.clearPendingDeletionIntent()
    }
    LaunchedEffect(pendingDeletionIntent) {
        pendingDeletionIntent?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(deletionComplete) { if (deletionComplete) navController.navigateUp() }

    val notePrefix = stringResource(R.string.memory_detail_share_note_prefix)
    val shareChooserTitle = stringResource(R.string.memory_detail_share_chooser_title)
    val shareMemory = { mem: MemoryEntity ->
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            val sb = StringBuilder()
            if (!mem.aiTitle.isNullOrBlank()) sb.append(mem.aiTitle).append("\n\n")
            if (!mem.aiSummary.isNullOrBlank()) sb.append(mem.aiSummary).append("\n\n")
            if (!mem.userText.isNullOrBlank()) sb.append(notePrefix).append(mem.userText).append("\n\n")
            if (!mem.bookmarkUrl.isNullOrBlank()) sb.append(mem.bookmarkUrl)
            putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())

            val imageUriString = mem.imageUri ?: mem.imageUris?.firstOrNull()
            if (imageUriString != null) {
                try {
                    val uri = android.net.Uri.parse(imageUriString)
                    val shareUri = if (uri.scheme == "file") {
                        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", java.io.File(uri.path!!))
                    } else uri
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    type = "image/*"
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, shareChooserTitle))
    }

    val current = memory
    if (current != null) {
        MemoryDetailContent(
            memory = current,
            onNavigateUp = { navController.navigateUp() },
            onEdit = { navController.navigate("post_capture_edit/${current.id}") },
            onShare = { shareMemory(current) },
            onCreateTask = { title, desc, date, time, type -> viewModel.createTask(current.id, title, desc, date, time, type) },
            bottomPadding = 0.dp,
            isPlaying = isPlaying,
            audioProgress = audioProgress,
            audioCurrentTime = audioCurrentTime,
            audioTotalTime = audioTotalTime,
            onPlayAudio = viewModel::playAudio,
            onPauseAudio = viewModel::pauseAudio,
            onSeekAudio = viewModel::seekAudio,
            onDelete = viewModel::deleteMemory,
            isLocalAi = isLocalAi,
            collections = collections,
            onAddToCollection = { viewModel.addToCollection(current.id, it) },
            onCreateCollection = { name, desc -> viewModel.createCollectionAndAdd(current.id, name, desc) },
        )
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
    }
}

@Composable
private fun whenLabel(ts: Long, now: LocalDateTime): String {
    val t = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val time = t.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (t.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.detail_when_today, time)
        now.toLocalDate().minusDays(1) -> stringResource(R.string.detail_when_yesterday, time)
        else -> t.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemoryDetailContent(
    memory: MemoryEntity,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onCreateTask: (String, String, String?, String?, String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    audioProgress: Float = 0f,
    audioCurrentTime: String = "00:00",
    audioTotalTime: String = "00:00",
    onPlayAudio: (String) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    onSeekAudio: (Float) -> Unit = {},
    onDelete: (MemoryEntity) -> Unit = {},
    isLocalAi: Boolean = false,
    collections: List<CollectionEntity> = emptyList(),
    onAddToCollection: (Int) -> Unit = {},
    onCreateCollection: (String, String) -> Unit = { _, _ -> },
    now: LocalDateTime = LocalDateTime.now(),
) {
    val scheme = MaterialTheme.colorScheme
    var showFullscreenImage by remember { mutableStateOf(false) }
    var fullscreenInitialIndex by remember { mutableStateOf(0) }
    var showAddTaskSheet by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<TaskEntity?>(null) }
    var showCollect by remember { mutableStateOf(false) }
    var showCreateCollection by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showFullText by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val imageUris = memory.imageUris ?: listOfNotNull(memory.imageUri)
    val kind = memory.kind()
    val kindLabel = stringResource(
        when (kind) {
            MemoryKind.Image -> R.string.detail_source_image
            MemoryKind.Note -> R.string.detail_source_note
            MemoryKind.Audio -> R.string.detail_source_voice
            MemoryKind.Link -> R.string.detail_source_bookmark
        }
    )
    val kindIcon: ImageVector = when (kind) {
        MemoryKind.Image -> Icons.Rounded.Image
        MemoryKind.Note -> Icons.Rounded.StickyNote2
        MemoryKind.Audio -> Icons.Rounded.Mic
        MemoryKind.Link -> Icons.Rounded.Link
    }
    val whenText = whenLabel(memory.creationTimestamp, now)
    val allText = listOfNotNull(memory.aiSummary, memory.userText, memory.aiImageAnalysis, memory.aiAudioTranscription, memory.bookmarkDescription)
        .filter { it.isNotBlank() }.joinToString("\n\n")

    Box(modifier.fillMaxSize().background(scheme.surface)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 128.dp + bottomPadding)) {
            item {
                BlobHeader {
                    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        // Hero
                        if (imageUris.isNotEmpty()) {
                            val pagerState = rememberPagerState(pageCount = { imageUris.size })
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 240.dp, height = 252.dp)) {
                                HorizontalPager(pagerState, Modifier.fillMaxSize()) { page ->
                                    Image(
                                        painter = memoryPainter(imageUris[page]),
                                        contentDescription = memory.aiTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                                            .background(scheme.surfaceContainerHigh)
                                            .clickable {
                                                fullscreenInitialIndex = page
                                                showFullscreenImage = true
                                            }
                                    )
                                }
                                if (imageUris.size > 1) {
                                    Row(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0x8C141028))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.detail_image_counter, pagerState.currentPage + 1, imageUris.size),
                                            style = MaterialTheme.typography.labelLarge, color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            val (bg, fg) = (when (kind) {
                                MemoryKind.Audio -> Tone.Sky
                                MemoryKind.Link -> Tone.Success
                                else -> Tone.Tertiary
                            }).colors()
                            Box(
                                Modifier.align(Alignment.TopCenter).padding(top = 20.dp).size(150.dp).clip(MemShape.softBurst).background(bg),
                                contentAlignment = Alignment.Center
                            ) { Icon(kindIcon, null, tint = fg, modifier = Modifier.size(56.dp)) }
                        }
                        // Top buttons
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            DetailRoundButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), onNavigateUp)
                            Row {
                                DetailRoundButton(Icons.Rounded.Share, stringResource(R.string.common_share), onShare)
                                DetailRoundButton(Icons.Rounded.MoreVert, stringResource(R.string.detail_cd_more)) { showMore = true }
                            }
                        }
                    }
                }
            }

            // Title and meta
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
                    Text(
                        memory.displayTitle(stringResource(R.string.gallery_untitled_memory)),
                        style = MaterialTheme.typography.displaySmall, color = scheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(kindIcon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("$kindLabel  •  $whenText", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    // Copy text, Collect, Share: a connected row of tonal buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val outer = 28.dp
                        val inner = 6.dp
                        ActionButton(Icons.Rounded.ContentCopy, stringResource(R.string.detail_action_copy), RoundedCornerShape(outer, inner, inner, outer), Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(allText.ifBlank { memory.aiTitle.orEmpty() }))
                        }
                        ActionButton(Icons.Rounded.FolderOpen, stringResource(R.string.detail_action_collect), RoundedCornerShape(inner), Modifier.weight(1f)) { showCollect = true }
                        ActionButton(Icons.Rounded.Share, stringResource(R.string.common_share), RoundedCornerShape(inner, outer, outer, inner), Modifier.weight(1f), onClick = onShare)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Bookmark
            if (memory.bookmarkUrl != null) {
                item {
                    val ctx = LocalContext.current
                    val (bg, fg) = Tone.Success.colors()
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(bg)
                            .clickable {
                                androidx.core.content.ContextCompat.startActivity(
                                    ctx, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(memory.bookmarkUrl)), null
                                )
                            }
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, tint = fg, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.memory_detail_section_bookmark), style = MaterialTheme.typography.titleSmall, color = fg)
                        }
                        if (!memory.bookmarkTitle.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(memory.bookmarkTitle, style = MaterialTheme.typography.titleMedium, color = fg)
                        }
                        if (!memory.bookmarkDescription.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(memory.bookmarkDescription, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(memory.bookmarkUrl, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // AI summary
            if (!memory.aiSummary.isNullOrBlank()) {
                item {
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(scheme.tertiaryContainer)
                            .padding(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.memory_detail_section_ai_summary), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
                            if (isLocalAi) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.onTertiaryContainer.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Smartphone, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.detail_on_device).uppercase(), style = MaterialTheme.typography.labelMedium, color = scheme.onTertiaryContainer)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(memory.aiSummary, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)), color = scheme.onTertiaryContainer)
                    }
                }
            }

            // Audio
            if (memory.audioFilePath != null) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        AudioPlayer(
                            audioUri = memory.audioFilePath,
                            isPlaying = isPlaying,
                            progress = audioProgress,
                            durationString = audioTotalTime,
                            currentPositionString = audioCurrentTime,
                            onPlayClick = { if (isPlaying) onPauseAudio() else onPlayAudio(memory.audioFilePath) },
                            onSeek = onSeekAudio
                        )
                    }
                }
            }

            // Suggested actions
            if (!memory.aiActions.isNullOrEmpty()) {
                item {
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(scheme.surfaceContainer)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.memory_detail_section_suggested_actions), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                        memory.aiActions.forEach { action ->
                            ActionRow(
                                action = action,
                                onAdd = { onCreateTask(action.description, action.description, action.date, action.time, action.type) },
                                onEditFirst = {
                                    prefill = TaskEntity(
                                        memoryId = memory.id, title = action.description, description = action.description,
                                        dueDate = action.date, dueTime = action.time, type = action.type
                                    )
                                    showAddTaskSheet = true
                                }
                            )
                        }
                    }
                }
            }

            // Expandable text sections
            memory.userText?.takeIf { it.isNotBlank() }?.let {
                item { ExpandableSection(stringResource(R.string.memory_detail_section_your_note), it, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) }
            }
            memory.aiImageAnalysis?.takeIf { it.isNotBlank() }?.let {
                item { ExpandableSection(stringResource(R.string.memory_detail_section_image_analysis), it, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) }
            }
            memory.aiAudioTranscription?.takeIf { it.isNotBlank() }?.let {
                item { ExpandableSection(stringResource(R.string.memory_detail_section_audio_transcription), it, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) }
            }

            // Tags
            if (!memory.aiTags.isNullOrEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)) {
                        Text(stringResource(R.string.memory_detail_section_tags), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            memory.aiTags.forEach { tag ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.LocalOffer, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tag, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating toolbar: edit, collect, full text, delete, and a separate reminder FAB
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.BottomCenter).appNavigationBarsPadding().padding(bottom = 16.dp),
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            floatingActionButton = {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { prefill = null; showAddTaskSheet = true },
                    containerColor = scheme.primary, contentColor = scheme.onPrimary,
                    shape = RoundedCornerShape(20.dp)
                ) { Icon(Icons.Rounded.AddAlarm, stringResource(R.string.memory_detail_add_manual_reminder)) }
            },
            content = {
                ToolbarIcon(Icons.Rounded.Edit, stringResource(R.string.common_edit), onEdit)
                ToolbarIcon(Icons.Rounded.CreateNewFolder, stringResource(R.string.detail_action_collect)) { showCollect = true }
                ToolbarIcon(Icons.Rounded.Description, stringResource(R.string.detail_full_text)) { showFullText = true }
                ToolbarIcon(Icons.Rounded.Delete, stringResource(R.string.common_delete)) { showDelete = true }
            }
        )

        if (showFullscreenImage && imageUris.isNotEmpty()) {
            FullscreenImageViewer(
                imageUris = imageUris,
                initialIndex = fullscreenInitialIndex,
                onDismissRequest = { showFullscreenImage = false },
                onShare = onShare,
                onEdit = onEdit,
                memoryTitle = memory.aiTitle ?: stringResource(R.string.memory_detail_fallback),
                creationTimestamp = memory.creationTimestamp
            )
        }

        if (showAddTaskSheet) {
            MemSheet(onDismiss = { showAddTaskSheet = false; prefill = null }) {
                AddTaskSheet(
                    taskToEdit = prefill,
                    onDismiss = { showAddTaskSheet = false; prefill = null },
                    onSave = { title, description, date, time, type, _, _ ->
                        onCreateTask(title, description, date.toString(), time, type)
                        showAddTaskSheet = false
                        prefill = null
                    }
                )
            }
        }

        if (showCollect) {
            MemSheet(onDismiss = { showCollect = false }) {
                SelectCollectionSheet(
                    collections = collections,
                    onSelect = { onAddToCollection(it); showCollect = false },
                    onCreateNew = { showCollect = false; showCreateCollection = true },
                    onDismiss = { showCollect = false },
                    selectedCount = 1
                )
            }
        }
        if (showCreateCollection) {
            MemSheet(onDismiss = { showCreateCollection = false }) {
                CreateCollectionSheet(
                    onDismiss = { showCreateCollection = false },
                    onCreate = { n, d -> onCreateCollection(n, d); showCreateCollection = false }
                )
            }
        }
        if (showDelete) {
            ConfirmSheet(
                title = stringResource(R.string.detail_delete_title),
                body = stringResource(R.string.gallery_dialog_confirm_single_delete_body),
                confirmLabel = stringResource(R.string.common_delete),
                dismissLabel = stringResource(R.string.common_cancel),
                destructive = true,
                onConfirm = { showDelete = false; onDelete(memory) },
                onDismiss = { showDelete = false }
            )
        }
        if (showFullText) {
            MemSheet(onDismiss = { showFullText = false }) {
                SheetTitle(stringResource(R.string.detail_full_text))
                Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                    MarkdownText(allText.ifBlank { memory.aiTitle.orEmpty() }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (showMore) {
            MemSheet(onDismiss = { showMore = false }) {
                SheetTitle(stringResource(R.string.detail_more_title), memory.displayTitle(stringResource(R.string.gallery_untitled_memory)))
                MoreRow(Icons.Rounded.Edit, stringResource(R.string.common_edit)) { showMore = false; onEdit() }
                MoreRow(Icons.Rounded.Share, stringResource(R.string.common_share)) { showMore = false; onShare() }
                MoreRow(Icons.Rounded.Delete, stringResource(R.string.common_delete), danger = true) { showMore = false; showDelete = true }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = if (danger) scheme.error else scheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun DetailRoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)))
        Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun ActionRow(action: ActionDto, onAdd: () -> Unit, onEditFirst: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isEvent = action.type.equals("EVENT", ignoreCase = true)
    val whenText = listOfNotNull(
        action.date?.let { d -> runCatching { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) }.getOrDefault(d) },
        action.time
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (isEvent) Icons.Rounded.CalendarMonth else Icons.Rounded.TaskAlt, null, tint = scheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(action.description, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (whenText.isNotBlank()) Text(whenText, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        val label = stringResource(
            when {
                isEvent -> R.string.detail_add_event
                action.type.equals("REMINDER", ignoreCase = true) -> R.string.detail_add_reminder
                else -> R.string.detail_add_task
            }
        )
        val colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer)
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = onAdd, colors = colors) {
                    Icon(Icons.Rounded.CalendarMonth.takeIf { isEvent } ?: Icons.Rounded.TaskAlt, null, Modifier.size(SplitButtonDefaults.LeadingIconSize))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(checked = false, onCheckedChange = { onEditFirst() }, colors = colors) {
                    Icon(Icons.Rounded.ArrowDropDown, stringResource(R.string.detail_edit_first))
                }
            }
        )
    }
}

@Composable
fun ExpandableSection(title: String, content: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainerLow)
            .clickable { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.memory_detail_cd_collapse) else stringResource(R.string.memory_detail_cd_expand),
                tint = scheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                MarkdownText(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
