package com.example.memgallery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
fun TextInputScreen(
    navController: NavController,
    viewModel: MemoryCreationViewModel = hiltViewModel()
) {
    var userText by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is MemoryCreationUiState.Success) {
            // Navigate back to the gallery after successful creation
            navController.popBackStack(route = "gallery", inclusive = false)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a note") },
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

            if (uiState is MemoryCreationUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        viewModel.createMemory(
                            imageUri = null,
                            audioUri = null,
                            userText = userText
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userText.isNotBlank()
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
