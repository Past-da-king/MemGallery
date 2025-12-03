package com.example.memgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.components.MemoryCard
import com.example.memgallery.ui.components.sheets.AddMemoriesToCollectionSheet
import com.example.memgallery.ui.viewmodels.CollectionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    navController: NavController,
    collectionId: Int,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(collectionId) {
        viewModel.loadCollection(collectionId)
    }

    val collectionName by viewModel.collectionName.collectAsState()
    val memories by viewModel.collectionMemories.collectAsState()
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val selectedMemoryIds by viewModel.selectedMemoryIds.collectAsState()
    val availableMemories by viewModel.availableMemories.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionModeActive) {
                        Text("${selectedMemoryIds.size} selected")
                    } else {
                        Text(collectionName, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionModeActive) {
                                viewModel.clearSelection()
                            } else {
                                navController.navigateUp()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selectionModeActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectionModeActive) {
                        IconButton(
                            onClick = { viewModel.removeSelectedMemoriesFromCollection(collectionId) },
                            enabled = selectedMemoryIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove from Collection")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.loadAvailableMemories(collectionId)
                                showAddSheet = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Memories")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (memories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No memories in this collection yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
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
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AddMemoriesToCollectionSheet(
                    memories = availableMemories,
                    onAdd = { selectedIds ->
                        viewModel.addMemoriesToCollection(collectionId, selectedIds)
                        showAddSheet = false
                    },
                    onDismiss = { showAddSheet = false }
                )
            }
        }
    }
}
