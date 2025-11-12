package com.example.memg.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.memg.model.MemoryObject
import com.example.memg.ui.theme.*
import com.example.memg.model.Folder
import com.example.memg.viewmodel.MemGalleryViewModel
import com.google.accompanist.flowlayout.FlowRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryScreen(
    navigateToDetail: (String) -> Unit,
    navigateToAdd: () -> Unit,
    navigateToSettings: () -> Unit
) {
    val viewModel: MemGalleryViewModel = hiltViewModel()
    val memories by viewModel.memories.collectAsState(initial = emptyList())
    val folders by viewModel.folders.collectAsState(initial = emptyList())
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var isFoldersModalVisible by remember { mutableStateOf(false) }
    
    val filteredMemories = remember(memories, searchQuery, selectedFolder) {
        memories.filter { memory ->
            val tagsList: List<String> = try {
                Gson().fromJson(memory.tags, object : TypeToken<List<String>>() {}.type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            val matchesSearch = searchQuery.isEmpty() ||
                    memory.text?.contains(searchQuery, ignoreCase = true) == true ||
                    memory.summary.contains(searchQuery, ignoreCase = true) ||
                    tagsList.any { it.contains(searchQuery, ignoreCase = true) }
            
            val matchesFolder = selectedFolder?.let { 
                memory.folder == it 
            } ?: true
            
            matchesSearch && matchesFolder
        }
    }
    
    Scaffold(
        containerColor = DarkBlue,
        floatingActionButton = {
            FloatingActionButton(
                onClick = navigateToAdd,
                containerColor = Teal,
                contentColor = DarkBlue,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Memory"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MemGallery",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OffWhite,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = navigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Slate
                    )
                }
            }
            
            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search memories...", color = Slate) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MediumBlue,
                    unfocusedContainerColor = MediumBlue,
                    disabledContainerColor = MediumBlue,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Teal,
                    focusedTextColor = OffWhite,
                    unfocusedTextColor = OffWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Slate
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Slate
                            )
                        }
                    }
                }
            )
            
            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedFolder != null) {
                    FilterChip(
                        label = selectedFolder!!,
                        onDismiss = { selectedFolder = null },
                        isSelected = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                IconButton(
                    onClick = { isFoldersModalVisible = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (selectedFolder != null) Teal.copy(alpha = 0.2f) else MediumBlue,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (selectedFolder != null) Icons.Default.FilterAlt else Icons.Default.FilterList,
                        contentDescription = "Filter by folder",
                        tint = if (selectedFolder != null) Teal else Slate
                    )
                }
            }
            
            // Memory grid
            if (filteredMemories.isEmpty()) {
                EmptyState(searchQuery, selectedFolder)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMemories, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            onClick = { navigateToDetail(memory.id) }
                        )
                    }
                }
            }
        }
    }
    
    // Folders modal
    if (isFoldersModalVisible) {
        FoldersModal(
            folders = folders,
            selectedFolder = selectedFolder,
            onDismiss = { isFoldersModalVisible = false },
            onFolderSelected = { folderName ->
                selectedFolder = if (selectedFolder == folderName) null else folderName
                isFoldersModalVisible = false
            }
        )
    }
}

@Composable
fun EmptyState(searchQuery: String, selectedFolder: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = "No memories",
                tint = Slate,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "No memories found",
                color = OffWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (searchQuery.isNotEmpty() || selectedFolder != null) {
                    "Try adjusting your search or filter."
                } else {
                    "Tap the '+' button to capture your first memory."
                },
                color = Slate,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun FoldersModal(
    folders: List<Folder>,
    selectedFolder: String?,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Folder", color = OffWhite, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders) { folder ->
                    val isSelected = selectedFolder == folder.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Teal.copy(alpha = 0.2f) else MediumBlue)
                            .clickable { onFolderSelected(folder.name) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Folder else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (isSelected) Teal else Slate,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                color = OffWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = folder.itemCount.toString(),
                            color = Slate
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Teal)
            }
        },
        containerColor = DarkBlue,
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryCard(
    memory: MemoryObject,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MediumBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (memory.imageUri != null) {
                AsyncImage(
                    model = memory.imageUri,
                    contentDescription = "Memory image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(LightBlue),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        memory.audioUri != null -> Icons.Default.Mic
                        memory.text != null -> Icons.Default.TextFields
                        else -> Icons.Default.Inbox // Fallback icon
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Content type icon",
                        tint = OffWhite,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = memory.title,
                    color = OffWhite,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(memory.timestamp),
                    color = Slate,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FilterChip(label: String, onDismiss: () -> Unit, isSelected: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (isSelected) Teal.copy(alpha = 0.2f) else MediumBlue,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Teal else Slate,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = if (isSelected) Teal else Slate,
            modifier = Modifier
                .size(18.dp)
                .clickable { onDismiss() }
        )
    }
}