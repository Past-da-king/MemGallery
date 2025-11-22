package com.example.memgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.components.MemoryCard
import com.example.memgallery.ui.viewmodels.GalleryViewModel
import com.example.memgallery.data.local.entity.MemoryEntity
import kotlinx.coroutines.launch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import java.util.Random
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel(),
    openAddSheet: Boolean = false
) {
    val memories by viewModel.memories.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val highlightTag by viewModel.highlightTag.collectAsState()
    val highlightMemories by viewModel.highlightMemories.collectAsState()
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val selectedMemoryIds by viewModel.selectedMemoryIds.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            navController.navigate(Screen.PostCapture.createRoute(imageUri = uri.toString())) {
                popUpTo(Screen.Gallery.route) { inclusive = false }
            }
        }
    }

    var showMemoryOptionsSheet by remember { mutableStateOf(false) }
    var selectedMemoryForOptions by remember { mutableStateOf<MemoryEntity?>(null) }
    var showDeleteMultipleMemoriesDialog by remember { mutableStateOf(false) }

    // State to control Search Bar visibility (Drop Down)
    var isSearchBarVisible by remember { mutableStateOf(true) }

    // Header Height State for Padding
    val localDensity = LocalDensity.current
    var headerHeightDp by remember { mutableStateOf(0.dp) }

    // Scroll detection to toggle search bar visibility
    // Update search bar visibility based on scroll
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        if (gridState.firstVisibleItemIndex > 0) {
            isSearchBarVisible = false
        } else if (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
             isSearchBarVisible = true
        }
    }

    // Handle shortcut to open add sheet
    LaunchedEffect(openAddSheet) {
        if (openAddSheet) {
            showBottomSheet = true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!selectionModeActive) {
                FloatingActionButton(
                    onClick = { showBottomSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    // Content starts exactly where the header ends
                    top = headerHeightDp + 16.dp, 
                    bottom = padding.calculateBottomPadding() + 80.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {

                // Highlight Section
                if (highlightTag != null && highlightMemories.isNotEmpty()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        highlightMemories.firstOrNull()?.let { highlightMemory ->
                            HighlightMemoryCard(
                                memory = highlightMemory,
                                tag = highlightTag!!,
                                onClick = { navController.navigate(Screen.Detail.createRoute(highlightMemory.id)) }
                            )
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            text = "All Memories",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(memories) { memory ->
                    val isSelected = selectedMemoryIds.contains(memory.id)
                    MemoryCard(
                        memory = memory,
                        isSelected = isSelected,
                        onClick = { clickedMemory ->
                            if (selectionModeActive) {
                                viewModel.toggleMemorySelection(clickedMemory.id)
                            } else {
                                navController.navigate(Screen.Detail.createRoute(clickedMemory.id))
                            }
                        },
                        onLongClick = { longPressedMemory ->
                            if (!selectionModeActive) {
                                viewModel.toggleSelectionMode()
                            }
                            viewModel.toggleMemorySelection(longPressedMemory.id)
                        }
                    )
                }
            }

            // Floating Header with Seamless Gradient Blur
            val backgroundColor = MaterialTheme.colorScheme.background
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { coordinates ->
                        headerHeightDp = with(localDensity) { coordinates.size.height.toDp() }
                    }
                    .drawBehind {
                        // Gradient fades out over the bottom ~35% of the header
                        // using multiple stops for a very smooth, feathered transition.
                        val solidStop = 0.65f 
                        
                        val brush = Brush.verticalGradient(
                            0.0f to backgroundColor,
                            solidStop to backgroundColor,
                            0.70f to backgroundColor.copy(alpha = 0.6f),
                            0.78f to backgroundColor.copy(alpha = 0.35f),
                            0.85f to backgroundColor.copy(alpha = 0.2f),
                            0.90f to backgroundColor.copy(alpha = 0.1f),
                            0.95f to backgroundColor.copy(alpha = 0.05f),
                            1.0f to backgroundColor.copy(alpha = 0.0f)
                        )
                        drawRect(brush)
                    }
            ) {
                if (selectionModeActive) {
                    TopAppBar(
                        title = { Text("${selectedMemoryIds.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    if (selectedMemoryIds.size == 1) {
                                        val singleSelectedMemoryId = selectedMemoryIds.first()
                                        selectedMemoryForOptions = memories.firstOrNull { it.id == singleSelectedMemoryId }
                                        showMemoryOptionsSheet = true
                                    } else if (selectedMemoryIds.size > 1) {
                                        showDeleteMultipleMemoriesDialog = true
                                    }
                                },
                                enabled = selectedMemoryIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent // Transparent to let the gradient background show
                        )
                    )
                } else {
                    // Fixed Header for Normal Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding() // Adds padding for status bar
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MemGallery",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Search Icon (Visible when Search Bar is hidden)
                            AnimatedVisibility(
                                visible = !isSearchBarVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(
                                    onClick = { isSearchBarVisible = true }
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            // Settings Icon
                            IconButton(
                                onClick = { navController.navigate(Screen.Settings.route) },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Collapsible Search Bar & Filters (Drop Down)
                AnimatedVisibility(
                    visible = isSearchBarVisible && !selectionModeActive,
                    enter = androidx.compose.animation.expandVertically() + fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        // Search Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            TextField(
                                value = searchText,
                                onValueChange = viewModel::onSearchTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search memories...", style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                singleLine = true
                            )
                        }

                        // Filters
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            val filters = listOf("All", "Images", "Notes", "Audio", "Bookmarks")
                            items(filters) { filter ->
                                    val isSelected = selectedFilter == filter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.onFilterSelected(filter) },
                                        label = { Text(filter) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                            }
                        }
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AddContentSheet(
                    navController = navController,
                    onHideSheet = {
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    imagePickerLauncher = imagePickerLauncher
                )
            }
        }

        if (showMemoryOptionsSheet) {
            selectedMemoryForOptions?.let { memory ->
                ModalBottomSheet(
                    onDismissRequest = { showMemoryOptionsSheet = false },
                    sheetState = sheetState
                ) {
                    MemoryOptionsSheet(
                        memory = memory,
                        onDismiss = { showMemoryOptionsSheet = false },
                        viewModel = viewModel,
                        navController = navController
                    )
                }
            }
        }

        if (showDeleteMultipleMemoriesDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteMultipleMemoriesDialog = false },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to permanently delete these ${selectedMemoryIds.size} memories?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteSelectedMemories()
                        showDeleteMultipleMemoriesDialog = false
                    }) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteMultipleMemoriesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoryOptionsSheet(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    viewModel: GalleryViewModel,
    navController: NavController
) {
    var showDeleteMediaDialog by remember { mutableStateOf(false) }
    var showDeleteFullMemoryDialog by remember { mutableStateOf(false) }
    var showHideMemoryPrompt by remember { mutableStateOf(false) } // For after media deletion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Options for ${memory.aiTitle ?: "Memory"}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDeleteMediaDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Media")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showDeleteFullMemoryDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Full Memory")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.hideMemory(memory.id)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hide Memory")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Dismiss")
        }
    }

    // Dialogs
    if (showDeleteMediaDialog) {
        DeleteMediaDialog(
            memory = memory,
            onDismiss = { showDeleteMediaDialog = false },
            onConfirm = { deleteImage, deleteAudio ->
                viewModel.deleteMedia(memory.id, deleteImage, deleteAudio)
                // Check if all media is deleted to prompt for hiding
                if ((deleteImage && memory.imageUri != null) && (deleteAudio && memory.audioFilePath != null)) {
                    showHideMemoryPrompt = true
                } else if (deleteImage && memory.imageUri != null && memory.audioFilePath == null) {
                    showHideMemoryPrompt = true
                } else if (deleteAudio && memory.audioFilePath != null && memory.imageUri == null) {
                    showHideMemoryPrompt = true
                }
                showDeleteMediaDialog = false
                onDismiss()
            }
        )
    }

    if (showDeleteFullMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFullMemoryDialog = false },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to permanently delete this memory?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteFullMemory(memory.id)
                    showDeleteFullMemoryDialog = false
                    onDismiss()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteFullMemoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHideMemoryPrompt) {
        AlertDialog(
            onDismissRequest = { showHideMemoryPrompt = false },
            title = { Text("Hide Memory?") },
            text = { Text("All media has been deleted. Do you want to hide this memory from the gallery?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.hideMemory(memory.id)
                    showHideMemoryPrompt = false
                    onDismiss()
                }) {
                    Text("Hide")
                }
            },
            dismissButton = {
                Button(onClick = { showHideMemoryPrompt = false }) {
                    Text("Keep Visible")
                }
            }
        )
    }
}

@Composable
private fun DeleteMediaDialog(
    memory: MemoryEntity,
    onDismiss: () -> Unit,
    onConfirm: (deleteImage: Boolean, deleteAudio: Boolean) -> Unit
) {
    var deleteImage by remember { mutableStateOf(false) }
    var deleteAudio by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Media") },
        text = {
            Column {
                Text("Which media do you want to delete?")
                Spacer(modifier = Modifier.height(8.dp))
                if (memory.imageUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteImage, onCheckedChange = { deleteImage = it })
                        Text("Image")
                    }
                }
                if (memory.audioFilePath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteAudio, onCheckedChange = { deleteAudio = it })
                        Text("Audio")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteImage, deleteAudio) },
                enabled = deleteImage || deleteAudio
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddContentSheet(
    navController: NavController,
    onHideSheet: () -> Unit,
    imagePickerLauncher: ActivityResultLauncher<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            "Create New",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val items = listOf(
            Triple("Text Note", Icons.Default.TextFields) {
                onHideSheet()
                navController.navigate(Screen.TextInput.createRoute())
            },
            Triple("Upload Image", Icons.Default.Image) {
                onHideSheet()
                imagePickerLauncher.launch("image/*")
            },
            Triple("Take Photo", Icons.Default.PhotoCamera) {
                onHideSheet()
                navController.navigate(Screen.CameraCapture.route)
            },
            Triple("Record Audio", Icons.Default.Mic) {
                onHideSheet()
                navController.navigate(Screen.AudioCapture.createRoute())
            },
            Triple("Save Bookmark", Icons.Default.Bookmark) {
                onHideSheet()
                navController.navigate(Screen.BookmarkInput.createRoute())
            }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { (label, icon, action) ->
                Card(
                    onClick = action,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun generateRandomGradientBrush(seed: Int): Brush {
    val colors = listOf(
        listOf(Color(0xFF6A11CB), Color(0xFF2575FC)), // Purple to Blue
        listOf(Color(0xFFFC00FF), Color(0xFF00DBDE)), // Pink to Cyan
        listOf(Color(0xFFF7971E), Color(0xFFFFD200)), // Orange to Yellow
        listOf(Color(0xFFEE0979), Color(0xFFFF6A00)), // Red to Orange
        listOf(Color(0xFF00C6FF), Color(0xFF0072FF))  // Light Blue to Dark Blue
    )
    val random = java.util.Random(seed.toLong())
    val selectedColors = colors[random.nextInt(colors.size)]
    return Brush.verticalGradient(selectedColors)
}

@Composable
fun HighlightMemoryCard(
    memory: MemoryEntity,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (memory.imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = memory.imageUri),
                    contentDescription = memory.aiTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(generateRandomGradientBrush(memory.id))
                )
            }

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 200f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                SuggestionChip(
                    onClick = { },
                    label = { Text("Featured: $tag") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = memory.aiTitle ?: "Untitled Memory",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (memory.aiSummary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = memory.aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
