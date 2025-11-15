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
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputScreen(
    navController: NavController,
    existingImageUri: String?,
    existingAudioUri: String?,
    existingUserText: String?
) {
    var userText by remember { mutableStateOf(existingUserText ?: "") }

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

            Button(
                onClick = {
                    navController.navigate(
                        Screen.PostCapture.createRoute(
                            imageUri = existingImageUri,
                            audioUri = existingAudioUri,
                            userText = userText
                        )
                    ) {
                        // Ensure we don't have a deep back stack of creation screens
                        popUpTo(Screen.Gallery.route)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = userText.isNotBlank()
            ) {
                Text("Continue")
            }
        }
    }
}
