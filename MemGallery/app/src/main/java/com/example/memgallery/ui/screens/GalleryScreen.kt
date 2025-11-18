package com.example.memgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel()
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


    Scaffold(
        topBar = {
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
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("MemGallery") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.ApiKey.route) }) {
                            Icon(Icons.Default.Person, contentDescription = "API Key Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        floatingActionButton = {
            if (!selectionModeActive) {
                FloatingActionButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TextField(
                value = searchText,
                onValueChange = viewModel::onSearchTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search memories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "Images", "Notes", "Audio")
                filters.forEach { filter ->
                    Button(
                        onClick = { viewModel.onFilterSelected(filter) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(filter)
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedVisibility(
                        visible = highlightTag != null && highlightMemories.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        highlightMemories.firstOrNull()?.let { highlightMemory ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .then(
                                        if (highlightMemory.imageUri == null) {
                                            Modifier.background(generateRandomGradientBrush(highlightMemory.id))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        navController.navigate(Screen.Detail.createRoute(highlightMemory.id))
                                    }
                            ) {
                                if (highlightMemory.imageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(model = highlightMemory.imageUri),
                                        contentDescription = highlightMemory.aiTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                                startY = 300f
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "Memory related to ${highlightTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        highlightMemory.aiTitle ?: "No Title",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White
                                    )
                                    Text(
                                        highlightMemory.aiSummary ?: "No summary available.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.9f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "All Memories",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(8.dp))
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
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add a new memory",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    onHideSheet()
                    navController.navigate(Screen.TextInput.createRoute())
                }
            ) {
                Icon(Icons.Default.TextFields, contentDescription = "Add Text", modifier = Modifier.size(48.dp))
                Text("Add Text", style = MaterialTheme.typography.bodyLarge)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    onHideSheet()
                    imagePickerLauncher.launch("image/*")
                }
            ) {
                Icon(Icons.Default.Image, contentDescription = "Add Image", modifier = Modifier.size(48.dp))
                Text("Add Image", style = MaterialTheme.typography.bodyLarge)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    onHideSheet()
                    navController.navigate(Screen.CameraCapture.route)
                }
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Take Picture", modifier = Modifier.size(48.dp))
                Text("Take Picture", style = MaterialTheme.typography.bodyLarge)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    onHideSheet()
                    navController.navigate(Screen.AudioCapture.createRoute())
                }
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Add Audio", modifier = Modifier.size(48.dp))
                Text("Add Audio", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onHideSheet, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
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
