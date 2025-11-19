package com.example.memgallery.ui.screens

import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.components.AudioPlayer
import com.example.memgallery.ui.components.FullscreenImageViewer
import com.example.memgallery.ui.viewmodels.MemoryDetailViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import androidx.compose.ui.graphics.toArgb
import android.util.TypedValue
import io.noties.markwon.image.ImagesPlugin


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
                title = { Text(memory?.aiTitle ?: "Memory", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        memory?.let {
                            navController.navigate("post_capture_edit/${it.id}")
                        }
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Implement share functionality */ }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
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
    var showFullscreenImage by remember { mutableStateOf(false) }
    var fullscreenImageUri by remember { mutableStateOf<String?>(null) }
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
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        fullscreenImageUri = memory.imageUri
                        showFullscreenImage = true
                    },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(16.dp))
        }



        if (showFullscreenImage && fullscreenImageUri != null) {
            FullscreenImageViewer(
                imageUri = fullscreenImageUri!!,
                onDismissRequest = { showFullscreenImage = false },
                memoryTitle = memory.aiTitle ?: "Memory",
                creationTimestamp = memory.creationTimestamp
            )
        }

        if (memory.audioFilePath != null) {
            AudioPlayer(audioUri = memory.audioFilePath)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (memory.userText != null) {
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Full Note", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Text(memory.userText, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (memory.aiAudioTranscription != null) {
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AI Audio Transcription", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    MarkdownText(memory.aiAudioTranscription, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (memory.aiImageAnalysis != null) {
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("AI Image Analysis", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    MarkdownText(memory.aiImageAnalysis, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Summary", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(memory.aiSummary.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Tags", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisAlignment = FlowMainAxisAlignment.Start,
            crossAxisAlignment = FlowCrossAxisAlignment.Center,
            mainAxisSpacing = 8.dp,
            crossAxisSpacing = 8.dp
        ) {
            memory.aiTags.orEmpty().forEach { tag ->
                SuggestionChip(
                    onClick = { /* No action */ },
                    label = { Text(tag) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date Captured
        Text("Date Captured", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date(memory.creationTimestamp)),
            style = MaterialTheme.typography.bodyLarge
        )
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
                // Apply color and size from the style
                setTextColor(onSurfaceColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
                // Note: Setting fontFamily directly on TextView from Compose TextStyle is not straightforward.
                // Markwon might handle some font styling, but for direct TextView control, it's more complex.
                // For now, focus on color and size as requested.
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}