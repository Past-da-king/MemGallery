package com.example.memgallery.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.ui.viewmodels.ApiKeyUiState
import com.example.memgallery.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val autoIndexScreenshots by viewModel.autoIndexScreenshots.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiKeyUiState by viewModel.apiKeyUiState.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationFilter by viewModel.notificationFilter.collectAsState()
    val showInShareSheet by viewModel.showInShareSheet.collectAsState()
    val taskScreenEnabled by viewModel.taskScreenEnabled.collectAsState()
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionsToRequest = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.setAutoIndexScreenshots(true)
        } else {
            viewModel.setAutoIndexScreenshots(false)
        }
    }

    // Check permission on entry if feature is enabled
    LaunchedEffect(Unit) {
        if (autoIndexScreenshots) {
            val allGranted = permissionsToRequest.all { permission ->
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (!allGranted) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Validation Section (API Key)
            SettingsSection(title = "Validation") {
                ApiKeySection(
                    apiKey = apiKey,
                    uiState = apiKeyUiState,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    onValidate = viewModel::validateAndSaveKey,
                    onClear = viewModel::clearKey
                )
            }

            HorizontalDivider()

            // Screenshot Related Section
            SettingsSection(title = "Screenshot Related") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-index new screenshots",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Automatically process new screenshots with AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoIndexScreenshots,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            } else {
                                viewModel.setAutoIndexScreenshots(false)
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            // Notifications Section
            SettingsSection(title = "Notifications") {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Master Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Notifications",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Get notified about actions from memories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotifications(it) }
                        )
                    }

                    // Filter Options (Only show if notifications enabled)
                    if (notificationsEnabled) {
                        HorizontalDivider()

                        Text(
                            "Notify me for:",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        val filters = listOf(
                            "ALL" to "All Actions",
                            "EVENTS" to "Events Only",
                            "TODOS" to "To-Dos Only"
                        )

                        filters.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setNotificationFilter(value) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notificationFilter == value,
                                    onClick = { viewModel.setNotificationFilter(value) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Share Sheet Toggle
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show in Share Menu",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Allow sharing content from other apps to MemGallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showInShareSheet,
                            onCheckedChange = { viewModel.setShowInShareSheet(it) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Task Manager Section
            SettingsSection(title = "Task Manager") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Task Screen",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Show the task manager screen in the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = taskScreenEnabled,
                        onCheckedChange = { viewModel.setTaskScreenEnabled(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

@Composable
fun ApiKeySection(
    apiKey: String,
    uiState: ApiKeyUiState,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onClear: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Enter your Gemini API Key to enable AI features.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Toggle visibility")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is ApiKeyUiState.Success -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(uiState.message, color = MaterialTheme.colorScheme.primary)
                }
            }
            is ApiKeyUiState.Error -> {
                Text(uiState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            is ApiKeyUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
            }
            else -> {}
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onValidate,
                modifier = Modifier.weight(1f)
            ) {
                Text("Validate")
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
        }
    }
}
