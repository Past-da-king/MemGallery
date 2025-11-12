package com.example.memg.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.memg.model.MemoryObject
import com.example.memg.ui.theme.DarkBlue
import com.example.memg.ui.theme.ErrorRed
import com.example.memg.ui.theme.LightBlue
import com.example.memg.ui.theme.LightGrey
import com.example.memg.ui.theme.MediumBlue
import com.example.memg.ui.theme.OffWhite
import com.example.memg.ui.theme.Slate
import com.example.memg.ui.theme.Teal
import com.example.memg.viewmodel.MemGalleryViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    memoryId: String,
    onNavigateBack: () -> Unit,
    onEditComplete: (MemoryObject) -> Unit,
    onDeleteComplete: () -> Unit
) {
    val viewModel: MemGalleryViewModel = hiltViewModel()
    val allMemories: List<MemoryObject> by viewModel.memories.collectAsState(initial = emptyList())
    val memory: MemoryObject? = remember(allMemories) {
        allMemories.find { it.id == memoryId }
    }

    if (memory == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Memory not found",
                color = OffWhite,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        return
    }

    val tagsList: List<String> = remember(memory.tags) {
        try {
            Gson().fromJson(memory.tags, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Scaffold(
        containerColor = DarkBlue,
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(
                    onClick = { /* TODO: Handle Edit */ },
                    containerColor = MediumBlue,
                    contentColor = Teal,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                }
                FloatingActionButton(
                    onClick = {
                        viewModel.deleteMemory(memory)
                        onDeleteComplete()
                    },
                    containerColor = ErrorRed,
                    contentColor = OffWhite,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Header with Image
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    AsyncImage(
                        model = memory.imageUri,
                        contentDescription = "Memory image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Black.copy(alpha = 0.8f)),
                                    startY = 600f
                                )
                            )
                    )
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(DarkBlue.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OffWhite
                        )
                    }
                }
            }

            // Content
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Title
                    Text(
                        text = memory.title,
                        color = OffWhite,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Summary
                    Text(
                        text = memory.summary,
                        color = OffWhite,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal
                    )

                    // Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoChip(icon = Icons.Default.Folder, text = memory.folder)
                        InfoChip(
                            icon = Icons.Default.CalendarToday,
                            text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(memory.timestamp)
                        )
                    }

                    // Tags
                    if (tagsList.isNotEmpty()) {
                        Section(title = "Tags") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagsList.forEach { tag ->
                                    TagChip(text = tag)
                                }
                            }
                        }
                    }

                    // Image Analysis
                    if (memory.imageAnalysis != null) {
                        Section(title = "Image Analysis") {
                            Text(
                                text = memory.imageAnalysis,
                                color = LightGrey,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    // Text Note
                    if (memory.text != null) {
                        Section(title = "Note") {
                            Text(
                                text = memory.text,
                                color = LightGrey,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    // Audio Note
                    if (memory.audioUri != null) {
                        Section(title = "Audio Note") {
                            if (memory.transcribedText != null) {
                                Text(
                                    text = memory.transcribedText,
                                    color = LightGrey,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }
                            Button(
                                onClick = { /* TODO: Handle play audio */ },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = DarkBlue
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Play Audio",
                                    color = DarkBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = OffWhite,
            fontWeight = FontWeight.Bold
        )
                    Column { // Provide ColumnScope explicitly
                        content()
                    }    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Slate,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(LightBlue.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "#$text",
            color = OffWhite,
            fontWeight = FontWeight.Medium
        )
    }
}