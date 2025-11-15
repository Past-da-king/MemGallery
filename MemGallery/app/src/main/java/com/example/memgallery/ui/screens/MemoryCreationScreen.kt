package com.example.memgallery.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCreationScreen(
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

    LaunchedEffect(uiState) {
        if (uiState is MemoryCreationUiState.Success) {
            navController.popBackStack()
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Memory") },
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
            OutlinedTextField(
                value = userText,
                onValueChange = { userText = it },
                label = { Text("Your thoughts...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Image")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (imageUri != null) "Image Selected" else "Add Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is MemoryCreationUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        viewModel.createMemory(
                            imageUri = imageUri?.toString(),
                            audioUri = null, // Audio not implemented yet
                            userText = userText
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
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
