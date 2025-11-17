package com.example.memgallery.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable
fun MemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit
) {
    val randomGradientBrush = remember(memory.id) {
        generateRandomGradientBrush(memory.id)
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (memory.imageUri == null) {
                        Modifier.background(randomGradientBrush)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (memory.imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = memory.imageUri),
                    contentDescription = memory.aiTitle ?: "Pending Memory",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Gradient overlay
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
            // Content on top of the gradient
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = memory.aiTitle ?: "Pending...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (memory.imageUri != null) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = "Image",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (memory.audioFilePath != null) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Audio",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (!memory.userText.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "Text",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (memory.status == "PENDING") {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Pending",
                            tint = Color.White.copy(alpha = 0.8f)
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
