package com.example.memgallery.ui.screens

import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.remote.dto.ActionDto
import com.example.memgallery.ui.components.AudioPlayer
import com.example.memgallery.ui.components.FullscreenImageViewer
import com.example.memgallery.ui.components.sheets.AddTaskSheet
import com.example.memgallery.ui.viewmodels.MemoryDetailViewModel
import com.example.memgallery.utils.ActionHandler
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(
    navController: NavController,
    memoryId: Int,
    viewModel: MemoryDetailViewModel = hiltViewModel()
) {
    viewModel.loadMemory(memoryId)
    val memory by viewModel.memory.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Implement share functionality */ },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
        }
    ) { padding ->
        memory?.let {
            MemoryDetailContent(
                memory = it,
                onNavigateUp = { navController.navigateUp() },
                onEdit = { navController.navigate("post_capture_edit/${it.id}") },
                bottomPadding = padding.calculateBottomPadding(),
                onCreateTask = { title, desc, date, time, type ->
                    viewModel.createTask(it.id, title, desc, date, time, type)
                }
            )
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailContent(
    memory: MemoryEntity,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    onCreateTask: (String, String, String?, String?, String) -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    var showFullscreenImage by remember { mutableStateOf(false) }
    var fullscreenImageUri by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showAddTaskSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val density = LocalDensity.current
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val hasImage = memory.imageUri != null
        
        // Anchors:
        // Top (Expanded): 0f (Sheet covers everything)
        // Initial: If image exists, start at 25% from top (covering 75%). If no image, start at 0.
        // Collapsed (Bottom): If image exists, allow drag to bottom (minus lip). If no image, lock at 0.
        
        val expandedOffset = 0f
        val collapsedOffset = if (hasImage) screenHeightPx - with(density) { 80.dp.toPx() } else 0f
        val initialOffset = if (hasImage) screenHeightPx * 0.25f else 0f
        
        // State for sheet offset
        var sheetOffset by remember { mutableStateOf(initialOffset) }

        // Nested Scroll Connection
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    
                    // If scrolling UP (delta < 0) and sheet is not fully expanded
                    if (delta < 0 && sheetOffset > expandedOffset) {
                        val newOffset = (sheetOffset + delta).coerceAtLeast(expandedOffset)
                        val consumed = newOffset - sheetOffset
                        sheetOffset = newOffset
                        return Offset(0f, consumed) // Consume all delta used for expanding
                    }
                    
                    // If scrolling DOWN (delta > 0) and list is at the top
                    if (delta > 0) {
                        val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        if (isAtTop) {
                            val newOffset = (sheetOffset + delta).coerceAtMost(collapsedOffset)
                            val consumed = newOffset - sheetOffset
                            sheetOffset = newOffset
                            return Offset(0f, consumed) // Consume all delta used for collapsing
                        }
                    }

                    return Offset.Zero
                }
            }
        }

        // 1. Background Image (Fixed)
        if (memory.imageUri != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberAsyncImagePainter(model = memory.imageUri),
                    contentDescription = memory.aiTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent, 
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
            }
        } else {
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // 2. Draggable Sheet
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(x = 0, y = sheetOffset.roundToInt()) }
                .nestedScroll(nestedScrollConnection), // Attach nested scroll here
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                val newOffset = (sheetOffset + delta).coerceIn(expandedOffset, collapsedOffset)
                                sheetOffset = newOffset
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Content List
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 0.dp) 
                ) {
                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    // Title & Date
                    item {
                        Text(
                            text = memory.aiTitle ?: "Untitled Memory",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = SimpleDateFormat("MMMM dd, yyyy • hh:mm a", Locale.US).format(Date(memory.creationTimestamp)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Bookmark Details
                    if (memory.bookmarkUrl != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(16.dp),
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(memory.bookmarkUrl))
                                    androidx.core.content.ContextCompat.startActivity(context, intent, null)
                                }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Bookmark", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    if (!memory.bookmarkTitle.isNullOrBlank()) {
                                        Text(memory.bookmarkTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    
                                    if (!memory.bookmarkDescription.isNullOrBlank()) {
                                        Text(memory.bookmarkDescription, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    
                                    Text(memory.bookmarkUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // AI Summary
                    if (!memory.aiSummary.isNullOrBlank()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AI Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(memory.aiSummary, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Audio Player
                    if (memory.audioFilePath != null) {
                        item {
                            AudioPlayer(audioUri = memory.audioFilePath)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // User Note (Expandable)
                    if (memory.userText != null) {
                        item {
                            ExpandableSection(title = "Your Note", content = memory.userText)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // AI Analysis Sections (Expandable)
                    if (memory.aiImageAnalysis != null) {
                        item {
                            ExpandableSection(title = "Image Analysis", content = memory.aiImageAnalysis)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    if (memory.aiAudioTranscription != null) {
                        item {
                            ExpandableSection(title = "Audio Transcription", content = memory.aiAudioTranscription)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Tags
                    if (!memory.aiTags.isNullOrEmpty()) {
                        item {
                            Text("Tags", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                mainAxisAlignment = FlowMainAxisAlignment.Start,
                                crossAxisAlignment = FlowCrossAxisAlignment.Center,
                                mainAxisSpacing = 8.dp,
                                crossAxisSpacing = 8.dp
                            ) {
                                memory.aiTags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = { /* No action */ },
                                        label = { Text(tag) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        border = null
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Actions
                    item {
                        Text("Suggested Actions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (!memory.aiActions.isNullOrEmpty()) {
                        item {
                            val context = LocalContext.current
                            memory.aiActions.forEach { action ->
                                ActionCard(action = action, onAction = {
                                    ActionHandler.handleAction(context, action)
                                })
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Manual Reminder Button
                    item {
                        OutlinedButton(
                            onClick = { showAddTaskSheet = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Manual Reminder", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Manual Reminder")
                        }
                    }
                    
                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(bottomPadding + 80.dp))
                    }
                }
            }
        }

        // Top Bar Actions (Floating)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onNavigateUp,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            IconButton(
                onClick = onEdit,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }

        if (showFullscreenImage && fullscreenImageUri != null) {
            FullscreenImageViewer(
                imageUri = fullscreenImageUri!!,
                onDismissRequest = { showFullscreenImage = false },
                memoryTitle = memory.aiTitle ?: "Memory",
                creationTimestamp = memory.creationTimestamp
            )
        }
        
        if (showAddTaskSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddTaskSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AddTaskSheet(
                    taskToEdit = null,
                    onDismiss = { showAddTaskSheet = false },
                    onSave = { title, description, date, time, type, _, _ ->
                        onCreateTask(title, description, date.toString(), time, type)
                        showAddTaskSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun ExpandableSection(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownText(content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun ActionCard(action: ActionDto, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(action.type, style = MaterialTheme.typography.labelMedium)
                Text(action.description, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onAction) {
                Icon(Icons.Default.Check, contentDescription = "Do Action")
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle) {
    val context = LocalContext.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = ScrollingMovementMethod()
                setTextColor(onSurfaceColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}