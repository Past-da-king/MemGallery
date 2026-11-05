package com.example.memgallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.components.sheets.AddMemoriesToCollectionSheet
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.CookieIconButton
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.HeaderBackButton
import com.example.memgallery.ui.design.HeaderTitleRow
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.MemoryTile
import com.example.memgallery.ui.design.RoundIconButton
import com.example.memgallery.ui.design.fullBleed
import androidx.compose.foundation.lazy.staggeredgrid.items
import com.example.memgallery.ui.viewmodels.CollectionDetailViewModel

@Composable
fun CollectionDetailScreen(
    navController: NavController,
    collectionId: Int,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(collectionId) { viewModel.loadCollection(collectionId) }

    val collectionName by viewModel.collectionName.collectAsState()
    val memories by viewModel.collectionMemories.collectAsState()
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val selectedMemoryIds by viewModel.selectedMemoryIds.collectAsState()
    val availableMemories by viewModel.availableMemories.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    val untitled = stringResource(R.string.memory_card_untitled)
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(scheme.surface)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalItemSpacing = 4.dp
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.fullBleed(16.dp)) {
                    BlobHeader {
                        Box(Modifier.padding(start = 8.dp, top = 4.dp)) {
                            if (selectionModeActive) {
                                RoundIconButton(Icons.Rounded.Close, stringResource(R.string.gallery_cd_cancel_selection), viewModel::clearSelection)
                            } else {
                                HeaderBackButton({ navController.navigateUp() })
                            }
                        }
                        HeaderTitleRow(
                            eyebrow = if (selectionModeActive) stringResource(R.string.gallery_selection_count, selectedMemoryIds.size)
                            else androidx.compose.ui.res.pluralStringResource(R.plurals.collections_n_memories, memories.size, memories.size),
                            title = collectionName
                        ) {
                            if (selectionModeActive) {
                                RoundIconButton(
                                    Icons.Rounded.Delete, stringResource(R.string.collection_cd_remove_from_collection),
                                    onClick = { if (selectedMemoryIds.isNotEmpty()) viewModel.removeSelectedMemoriesFromCollection(collectionId) }
                                )
                            } else {
                                CookieIconButton(Icons.Rounded.Add, stringResource(R.string.collection_cd_add_memories), onClick = {
                                    viewModel.loadAvailableMemories(collectionId)
                                    showAddSheet = true
                                })
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (memories.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = stringResource(R.string.collection_empty),
                        body = stringResource(R.string.collections_empty_body),
                        modifier = Modifier.padding(top = 16.dp),
                        primaryLabel = stringResource(R.string.collection_cd_add_memories),
                        primaryIcon = Icons.Rounded.Add,
                        onPrimary = {
                            viewModel.loadAvailableMemories(collectionId)
                            showAddSheet = true
                        }
                    )
                }
            } else {
                items(memories.size, key = { memories[it].id }) { i ->
                    val memory = memories[i]
                    MemoryTile(
                        memory = memory,
                        selected = selectedMemoryIds.contains(memory.id),
                        onClick = {
                            if (selectionModeActive) viewModel.toggleMemorySelection(memory.id)
                            else navController.navigate(Screen.Detail.createRoute(memory.id))
                        },
                        onLongClick = {
                            if (!selectionModeActive) viewModel.toggleSelectionMode()
                            viewModel.toggleMemorySelection(memory.id)
                        },
                        untitledLabel = untitled
                    )
                }
            }
        }

        if (showAddSheet) {
            MemSheet(onDismiss = { showAddSheet = false }) {
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
