package com.example.memgallery.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.ui.viewmodels.ApiKeyUiState
import com.example.memgallery.ui.viewmodels.BackupUiState
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
    val backupUiState by viewModel.backupUiState.collectAsState()
    
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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBackup(it) }
    }

    LaunchedEffect(backupUiState) {
        when (val state = backupUiState) {
            is BackupUiState.Success -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetBackupState()
            }
            is BackupUiState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetBackupState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Provider & API Configuration Card
            SettingsCard(
                icon = Icons.Default.Key,
                title = "AI Configuration",
                description = "Choose your AI provider and configure API keys"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Provider Selector
                    val aiProvider by viewModel.aiProvider.collectAsState()
                    
                    Text(
                        "AI Provider",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { viewModel.setAiProvider("GEMINI") },
                            selected = aiProvider == "GEMINI"
                        ) {
                            Text("Gemini")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { viewModel.setAiProvider("GROQ") },
                            selected = aiProvider == "GROQ"
                        ) {
                            Text("Groq")
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Show appropriate API key section based on selected provider
                    if (aiProvider == "GEMINI") {
                        Text(
                            "Gemini API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ApiKeySection(
                            apiKey = apiKey,
                            uiState = apiKeyUiState,
                            onApiKeyChange = viewModel::onApiKeyChange,
                            onValidate = viewModel::validateAndSaveKey,
                            onClear = viewModel::clearKey
                        )
                    } else {
                        val groqApiKey by viewModel.groqApiKey.collectAsState()
                        val groqApiKeyUiState by viewModel.groqApiKeyUiState.collectAsState()
                        val selectedModelId by viewModel.groqModelId.collectAsState()
                        var showModelSheet by remember { mutableStateOf(false) }
                        
                        Text(
                            "Groq API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        GridModelSelection(
                             selectedModelId = selectedModelId,
                             onClick = { showModelSheet = true }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        GroqApiKeySection(
                            apiKey = groqApiKey,
                            uiState = groqApiKeyUiState,
                            onApiKeyChange = viewModel::onGroqApiKeyChange,
                            onValidate = viewModel::validateAndSaveGroqKey,
                            onClear = viewModel::clearGroqKey
                        )
                        
                        if (showModelSheet) {
                            GroqModelSheet(
                                currentModelId = selectedModelId,
                                onModelSelected = { 
                                    viewModel.setGroqModelId(it)
                                    showModelSheet = false
                                },
                                onDismiss = { showModelSheet = false }
                            )
                        }
                    }
                }
            }
            
            // Appearance Card
            SettingsCard(
                icon = Icons.Default.Palette,
                title = "Appearance",
                description = "Customize look and feel"
            ) {
                AppearanceSection(
                    appThemeMode = viewModel.appThemeMode.collectAsState().value,
                    dynamicThemingEnabled = viewModel.dynamicThemingEnabled.collectAsState().value,
                    amoledModeEnabled = viewModel.amoledModeEnabled.collectAsState().value,
                    selectedColor = viewModel.selectedColor.collectAsState().value,
                    onAppThemeModeChange = viewModel::setAppThemeMode,
                    onDynamicThemingChange = viewModel::setDynamicThemingEnabled,
                    onAmoledModeChange = viewModel::setAmoledModeEnabled,
                    onSelectedColorChange = viewModel::setSelectedColor
                )
            }

            // Features Card
            SettingsCard(
                icon = Icons.Default.PhotoLibrary,
                title = "Features",
                description = "Manage app features and capabilities"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Screenshot Auto-indexing
                    SettingToggleItem(
                        icon = Icons.Default.Screenshot,
                        title = "Auto-index Screenshots",
                        description = "Automatically process new screenshots with AI",
                        checked = autoIndexScreenshots,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            } else {
                                viewModel.setAutoIndexScreenshots(false)
                            }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingToggleItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Show Highlighted Memory",
                        description = "Showcase a random memory at the top",
                        checked = viewModel.showHighlights.collectAsState().value,
                        onCheckedChange = { viewModel.setShowHighlights(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Task Screen
                    SettingToggleItem(
                        icon = Icons.Default.Task,
                        title = "Task Manager",
                        description = "Show task manager screen in the app",
                        checked = taskScreenEnabled,
                        onCheckedChange = { viewModel.setTaskScreenEnabled(it) }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Share Menu
                    SettingToggleItem(
                        icon = Icons.Default.Share,
                        title = "Share Menu",
                        description = "Show MemGallery in system share menu",
                        checked = showInShareSheet,
                        onCheckedChange = { viewModel.setShowInShareSheet(it) }
                    )
                }
            }

            // Data Management Card
            SettingsCard(
                icon = Icons.Default.Save,
                title = "Data Management",
                description = "Backup and restore your memories"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { 
                            val fileName = "memgallery_backup_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.zip"
                            exportLauncher.launch(fileName) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Backup")
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Backup")
                    }
                    
                    if (backupUiState is BackupUiState.Loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }

            // Notifications Card
            SettingsCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Manage notification preferences"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Master Toggle
                    SettingToggleItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Enable Notifications",
                        description = "Get notified about actions from memories",
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.toggleNotifications(it) }
                    )

                    // Filter Options
                    if (notificationsEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Text(
                            "Notify me for:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 8.dp)
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setNotificationFilter(value) }
                                    .padding(horizontal = 40.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notificationFilter == value,
                                    onClick = { viewModel.setNotificationFilter(value) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            
            // Support & Feedback Card
            SettingsCard(
                icon = Icons.Default.Info,
                title = "Support",
                description = "Get help and provide feedback"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { navController.navigate("feedback") }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                "Send Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Report bugs or suggest new features",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Advanced Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                onClick = { navController.navigate(com.example.memgallery.navigation.Screen.AdvancedSettings.route) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Advanced",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AI Persona & Edge Gestures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Advanced Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            content()
        }
    }
}

@Composable
fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Gemini API Key") },
            placeholder = { Text("AIza...") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = image,
                        contentDescription = "Toggle visibility",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Messages
        when (uiState) {
            is ApiKeyUiState.Success -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            uiState.message,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is ApiKeyUiState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is ApiKeyUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Validating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            else -> {}
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = onValidate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = apiKey.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Validate")
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = apiKey.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
fun GroqApiKeySection(
    apiKey: String,
    uiState: ApiKeyUiState,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    onClear: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Groq API Key") },
            placeholder = { Text("gsk_...") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = image,
                        contentDescription = "Toggle visibility",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Messages
        when (uiState) {
            is ApiKeyUiState.Success -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            uiState.message,
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is ApiKeyUiState.Error -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is ApiKeyUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Saving...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            else -> {}
        }

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = onValidate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = apiKey.isNotBlank(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = apiKey.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    appThemeMode: String,
    dynamicThemingEnabled: Boolean,
    amoledModeEnabled: Boolean,
    selectedColor: Int,
    onAppThemeModeChange: (String) -> Unit,
    onDynamicThemingChange: (Boolean) -> Unit,
    onAmoledModeChange: (Boolean) -> Unit,
    onSelectedColorChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Theme Mode
        Text(
            "Theme Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = listOf("Light", "System", "Dark")
            val values = listOf("LIGHT", "SYSTEM", "DARK")
            
            values.forEachIndexed { index, value ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onAppThemeModeChange(value) },
                    selected = appThemeMode == value
                ) {
                    Text(options[index])
                }
            }
        }

        HorizontalDivider()

        // Dynamic Color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingToggleItem(
                icon = Icons.Default.ColorLens,
                title = "Dynamic Color",
                description = "Use system wallpaper colors",
                checked = dynamicThemingEnabled,
                onCheckedChange = onDynamicThemingChange
            )
        }

        // Custom Color Picker (only if Dynamic Color is off or not supported)
        if (!dynamicThemingEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                "Accent Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp), // Add padding for better touch area
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Use spacedBy for consistent spacing
            ) {
                val colors = listOf(
                    0xFF6750A4.toInt(), // Purple (Default)
                    0xFFBF0031.toInt(), // Red
                    0xFF006D3B.toInt(), // Green
                    0xFF006874.toInt(), // Cyan
                    0xFF3A5BA9.toInt(), // Blue
                    0xFF825500.toInt(), // Orange
                    0xFF009688.toInt(), // Teal
                    0xFF3F51B5.toInt(), // Indigo
                    0xFFCDDC39.toInt(), // Lime
                    0xFFFFC107.toInt(), // Amber
                    0xFFFF5722.toInt(), // Deep Orange
                    0xFFE91E63.toInt(), // Pink
                    0xFF795548.toInt(), // Brown
                    0xFF607D8B.toInt(), // Blue Grey
                    0xFF9C27B0.toInt(), // Deep Purple
                    0xFF2196F3.toInt(), // Light Blue
                    0xFF4CAF50.toInt(), // Light Green
                    0xFFFFEB3B.toInt(), // Yellow
                    0xFF9E9E9E.toInt(), // Grey
                    0xFF000000.toInt()  // Black
                )
                
                colors.forEach { color ->
                    val isSelected = (selectedColor == color) || (selectedColor == -1 && color == 0xFF6750A4.toInt())
                    Box(
                        modifier = Modifier
                            .size(48.dp) // Slightly larger touch target
                            .clip(CircleShape)
                            .background(Color(color))
                            .clickable { onSelectedColorChange(color) }
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        3.dp, // Thicker border for better visibility
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (color == 0xFFFFFFFF.toInt()) Color.Black else Color.White, // Handle white color checkmark
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // AMOLED Mode
        SettingToggleItem(
            icon = Icons.Default.DarkMode,
            title = "AMOLED Mode",
            description = "Pure black background in dark mode",
            checked = amoledModeEnabled,
            onCheckedChange = onAmoledModeChange
        )
    }
}

@Composable
fun GridModelSelection(
    selectedModelId: String,
    onClick: () -> Unit
) {
    val model = com.example.memgallery.data.remote.ai.GroqModels.models.find { it.id == selectedModelId }
        ?: com.example.memgallery.data.remote.ai.GroqModels.models.first()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
           Column(modifier = Modifier.weight(1f)) {
               Text(
                   text = "Model",
                   style = MaterialTheme.typography.labelSmall,
                   color = MaterialTheme.colorScheme.onSurfaceVariant
               )
               Text(
                   text = model.id.split("/").last(), // Show simple name
                   style = MaterialTheme.typography.titleSmall,
                   fontWeight = FontWeight.Bold,
                   color = MaterialTheme.colorScheme.onSurface
               )
           }
           Icon(
               imageVector = Icons.Default.ArrowDropDown,
               contentDescription = "Select Model"
           )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroqModelSheet(
    currentModelId: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Select Groq Model",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                com.example.memgallery.data.remote.ai.GroqModels.models.forEach { model ->
                    val isSelected = model.id == currentModelId
                    Card(
                        onClick = { onModelSelected(model.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.id,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (model.description.isNotEmpty()) {
                                         Text(
                                            text = model.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                   }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Rate Limits Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LimitBadge(
                                    label = "RPM", 
                                    value = model.rpm.toString(), 
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                LimitBadge(
                                    label = "TPM", 
                                    value = if (model.tpm >= 1000) "${model.tpm/1000}K" else model.tpm.toString(), 
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    textColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                LimitBadge(
                                    label = "RPD", 
                                    value = if (model.rpd >= 1000) "${model.rpd/1000}K" else model.rpd.toString(), 
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LimitBadge(
    label: String,
    value: String,
    color: Color,
    textColor: Color
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        }
    }
}
