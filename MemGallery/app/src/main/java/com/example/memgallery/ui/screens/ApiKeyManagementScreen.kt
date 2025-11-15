package com.example.memgallery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.memgallery.ui.viewmodels.ApiKeyViewModel
import com.example.memgallery.ui.viewmodels.ApiKeyUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagementScreen(
    navController: androidx.navigation.NavController,
    viewModel: ApiKeyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Key Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            Text(
                "Enter your key to enable AI-powered analysis features.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { viewModel.onApiKeyChange(it) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = "Toggle password visibility")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is ApiKeyUiState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.primary)
                    }
                }
                is ApiKeyUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                }
                is ApiKeyUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
                else -> {}
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.validateAndSaveKey() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Validate Key")
            }

            TextButton(
                onClick = { viewModel.clearKey() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Key")
            }
        }
    }
}
