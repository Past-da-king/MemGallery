package com.example.memgallery.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.components.AudioPlayer
import com.example.memgallery.ui.viewmodels.MemoryDetailViewModel

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
        topBar = {
            TopAppBar(
                title = { Text(memory?.aiTitle ?: "Memory") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Share") },
                icon = { Icon(Icons.Default.Share, contentDescription = "Share") },
                onClick = { /* TODO: Implement share functionality */ }
            )
        }
    ) { padding ->
        memory?.let {
            MemoryDetailContent(it, Modifier.padding(padding))
        } ?: run {
            // Show a loading indicator or an error message
        }
    }
}

@Composable
fun MemoryDetailContent(memory: MemoryEntity, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (memory.imageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(model = memory.imageUri),
                contentDescription = memory.aiTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (memory.audioFilePath != null) {
            AudioPlayer(audioUri = memory.audioFilePath)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (memory.userText != null) {
            Text("Your Note", style = MaterialTheme.typography.headlineSmall)
            Text(memory.userText, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Summary", style = MaterialTheme.typography.headlineSmall)
                Text(memory.aiSummary, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Tags", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            memory.aiTags.forEach { tag ->
                AssistChip(onClick = { /* No action */ }, label = { Text(tag) })
            }
        }
    }
}
