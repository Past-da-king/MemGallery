package com.example.memgallery.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCaptureScreen(
    navController: NavController,
    initialImageUri: String?,
    initialAudioUri: String?,
    initialUserText: String?,
    viewModel: MemoryCreationViewModel = hiltViewModel()
) {
    val draftImageUri by viewModel.draftImageUri.collectAsState()
    val draftAudioUri by viewModel.draftAudioUri.collectAsState()
    val draftUserText by viewModel.draftUserText.collectAsState()

    val uiState by viewModel.uiState.collectAsState()

    // Set initial draft values from navigation arguments, only if they are not null
    LaunchedEffect(initialImageUri, initialAudioUri, initialUserText) {
        if (initialImageUri != null) viewModel.setDraftImageUri(initialImageUri)
        if (initialAudioUri != null) viewModel.setDraftAudioUri(initialAudioUri)
        if (initialUserText != null) viewModel.setDraftUserText(initialUserText)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is MemoryCreationUiState.Success -> {
                navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
                viewModel.resetState()
            }
            is MemoryCreationUiState.Error -> {
                // Optionally show a SnackBar with the error message
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Memory Captured") },
                actions = {
                    IconButton(onClick = {
                        navController.popBackStack(route = Screen.Gallery.route, inclusive = false)
                        viewModel.resetState()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Captured Content Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (draftImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = Uri.parse(draftImageUri!!)),
                            contentDescription = "Captured Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("Add an Image, Audio, or Text", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (draftAudioUri != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Audio Added")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audio added")
                    }
                }
                if (draftUserText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = "Text Added")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(draftUserText!!, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button Group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add Audio Button
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.AudioCapture.createRoute(
                                imageUri = draftImageUri,
                                audioUri = draftAudioUri,
                                userText = draftUserText
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Add Audio", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Audio")
                }

                // Add Text Button
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.TextInput.createRoute(
                                imageUri = draftImageUri,
                                audioUri = draftAudioUri,
                                userText = draftUserText
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "Add Text", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Text")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Now Button
            Button(
                onClick = { viewModel.createMemory() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                enabled = uiState !is MemoryCreationUiState.Loading
            ) {
                if (uiState is MemoryCreationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.AddCircle, contentDescription = "Save Now", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Now")
                }
            }

            if (uiState is MemoryCreationUiState.Error) {
                Text(
                    text = (uiState as MemoryCreationUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Progress Indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Processing with Gemini…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Summarizing, tagging, organizing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
