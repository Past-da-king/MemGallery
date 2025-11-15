package com.example.memgallery.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCreationScreen(
    navController: NavController,
    viewModel: MemoryCreationViewModel = hiltViewModel()
) {
    var userText by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Launch the image picker automatically when the screen is composed
    LaunchedEffect(Unit) {
        if (imageUri == null) {
            imagePickerLauncher.launch("image/*")
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MemoryCreationUiState.Success) {
            navController.popBackStack(route = "gallery", inclusive = false)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add an image") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = imageUri),
                    contentDescription = "Selected image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    label = { Text("Add a note... (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // Show a message or a button to re-pick if the user cancelled
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No image selected. Go back to try again.")
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is MemoryCreationUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        viewModel.createMemory(
                            imageUri = imageUri?.toString(),
                            audioUri = null,
                            userText = userText
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = imageUri != null
                ) {
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
        }
    }
}
