package com.example.memgallery.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.data.local.entity.MemoryEntity
import java.util.Random
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryCard(
    memory: MemoryEntity,
    isSelected: Boolean,
    onClick: (MemoryEntity) -> Unit,
    onLongClick: (MemoryEntity) -> Unit
) {
    val randomGradientBrush = remember(memory.id) {
        generateRandomGradientBrush(memory.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(memory) },
                onLongClick = { onLongClick(memory) }
            )
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Image or Placeholder
            if (memory.imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = memory.imageUri),
                    contentDescription = memory.aiTitle ?: "Memory",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp) // Constrain height for staggered look
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(randomGradientBrush)
                )
            }

            // Gradient Overlay for Text Protection
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.9f)
                            ),
                            startY = 0f
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = memory.aiTitle ?: "Untitled Memory",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!memory.aiSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = memory.aiSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Icons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (memory.imageUri != null) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Image",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (memory.audioFilePath != null) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Audio",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (!memory.userText.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "Text",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (memory.status == "PENDING") {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Pending",
                            tint = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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
