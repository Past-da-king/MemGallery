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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.ui.viewmodels.ApiKeyUiState
import com.example.memgallery.ui.viewmodels.BackupUiState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
    
    // Updates State
    val latestAvailableVersion by viewModel.latestAvailableVersion.collectAsState()
    val latestChangeLog by viewModel.latestChangeLog.collectAsState()
    val hasShownUpdateLog by viewModel.hasShownUpdateLog.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    
    var showAboutScreen by remember { mutableStateOf(false) }
    var showUpdateLogSheet by remember { mutableStateOf(false) }
    
    // Performance: Only collect these when AboutScreen is shown
    val lastSeenVersion by if (showAboutScreen) viewModel.lastSeenVersion.collectAsState() else remember { mutableStateOf<String?>(null) }
    
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


    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                title = stringResource(R.string.settings_card_ai_title),
                description = stringResource(R.string.settings_card_ai_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Provider Selector
                    val aiProvider by viewModel.aiProvider.collectAsState()

                    Text(
                        stringResource(R.string.settings_ai_provider_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                            onClick = { viewModel.setAiProvider("GEMINI") },
                            selected = aiProvider == "GEMINI"
                        ) {
                            Text(stringResource(R.string.settings_ai_provider_gemini))
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                            onClick = { viewModel.setAiProvider("GROQ") },
                            selected = aiProvider == "GROQ"
                        ) {
                            Text(stringResource(R.string.settings_ai_provider_groq))
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                            onClick = { viewModel.setAiProvider("LOCAL") },
                            selected = aiProvider == "LOCAL"
                        ) {
                            Text(stringResource(R.string.settings_ai_provider_local))
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                            onClick = { viewModel.setAiProvider("OPENAI_COMPATIBLE") },
                            selected = aiProvider == "OPENAI_COMPATIBLE"
                        ) {
                            Text(stringResource(R.string.settings_ai_provider_custom))
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Show appropriate API key section based on selected provider
                    if (aiProvider == "GEMINI") {
                        val geminiModelId by viewModel.geminiModelId.collectAsState()
                        val availableGeminiModels by viewModel.availableGeminiModels.collectAsState()
                        val isLoadingGeminiModels by viewModel.isLoadingGeminiModels.collectAsState()
                        var showGeminiModelSheet by remember { mutableStateOf(false) }

                        Text(
                            stringResource(R.string.settings_gemini_api_key_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        GeminiModelSelection(
                            selectedModelId = geminiModelId,
                            onClick = {
                                showGeminiModelSheet = true
                                if (availableGeminiModels.isEmpty()) viewModel.fetchGeminiModels()
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ApiKeySection(
                            apiKey = apiKey,
                            uiState = apiKeyUiState,
                            onApiKeyChange = viewModel::onApiKeyChange,
                            onValidate = viewModel::validateAndSaveKey,
                            onClear = viewModel::clearKey
                        )

                        if (showGeminiModelSheet) {
                            GeminiModelSheet(
                                currentModelId = geminiModelId,
                                availableModels = availableGeminiModels,
                                isLoading = isLoadingGeminiModels,
                                onModelSelected = {
                                    viewModel.setGeminiModelId(it)
                                    showGeminiModelSheet = false
                                },
                                onRefresh = viewModel::fetchGeminiModels,
                                onDismiss = { showGeminiModelSheet = false }
                            )
                        }
                    } else if (aiProvider == "GROQ") {
                        val groqApiKey by viewModel.groqApiKey.collectAsState()
                        val groqApiKeyUiState by viewModel.groqApiKeyUiState.collectAsState()
                        val selectedModelId by viewModel.groqModelId.collectAsState()
                        var showModelSheet by remember { mutableStateOf(false) }
                        
                        Text(
                            stringResource(R.string.settings_groq_api_key_label),
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
                    } else if (aiProvider == "LOCAL") {
                        // LOCAL Provider
                        val localModelPath by viewModel.localModelPath.collectAsState()
                        val localModelImportState by viewModel.localModelImportState.collectAsState()
                        
                        val modelPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            uri?.let { viewModel.importLocalModel(it) }
                        }

                        Text(
                            stringResource(R.string.settings_local_model_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Text(
                            stringResource(R.string.settings_local_model_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        if (localModelPath.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { modelPickerLauncher.launch(arrayOf("*/*")) }, // Allow all files as bin/task might vary
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_local_model_import))
                            }
                            
                            // Download Link Helper
                            TextButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/index#models"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_local_model_download_link))
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.settings_local_model_loaded), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        localModelPath!!.substringAfterLast("/"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.clearLocalModel() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.settings_local_model_delete))
                                    }
                                }
                            }
                        }

                        // Import Status
                        if (localModelImportState is ApiKeyUiState.Loading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Text(stringResource(R.string.settings_local_model_importing), style = MaterialTheme.typography.bodySmall)
                        } else if (localModelImportState is ApiKeyUiState.Error) {
                             Text(
                                stringResource(R.string.settings_error_with_message, (localModelImportState as ApiKeyUiState.Error).message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else if (aiProvider == "OPENAI_COMPATIBLE") {
                        val customBaseUrl by viewModel.customBaseUrl.collectAsState()
                        val customModelName by viewModel.customModelName.collectAsState()
                        val customApiKey by viewModel.customApiKey.collectAsState()
                        val customUiState by viewModel.customUiState.collectAsState()

                        Text(
                            stringResource(R.string.settings_custom_provider_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Text(
                             stringResource(R.string.settings_custom_provider_description),
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        CustomProviderSection(
                            baseUrl = customBaseUrl,
                            modelName = customModelName,
                            apiKey = customApiKey,
                            uiState = customUiState,
                            onBaseUrlChange = viewModel::setCustomBaseUrl,
                            onModelNameChange = viewModel::setCustomModelName,
                            onApiKeyChange = viewModel::onCustomApiKeyChange,
                            onSave = viewModel::validateAndSaveCustomSettings,
                            onClearKey = viewModel::clearCustomKey
                        )
                    }
                }
            }
            
            // Appearance Card
            SettingsCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_card_appearance_title),
                description = stringResource(R.string.settings_card_appearance_description)
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
                title = stringResource(R.string.settings_card_features_title),
                description = stringResource(R.string.settings_card_features_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Screenshot Auto-indexing
                    SettingToggleItem(
                        icon = Icons.Default.Screenshot,
                        title = stringResource(R.string.settings_toggle_auto_index_title),
                        description = stringResource(R.string.settings_toggle_auto_index_description),
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
                        title = stringResource(R.string.settings_toggle_highlight_title),
                        description = stringResource(R.string.settings_toggle_highlight_description),
                        checked = viewModel.showHighlights.collectAsState().value,
                        onCheckedChange = { viewModel.setShowHighlights(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Task Screen
                    SettingToggleItem(
                        icon = Icons.Default.Task,
                        title = stringResource(R.string.settings_toggle_task_manager_title),
                        description = stringResource(R.string.settings_toggle_task_manager_description),
                        checked = taskScreenEnabled,
                        onCheckedChange = { viewModel.setTaskScreenEnabled(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Share Menu
                    SettingToggleItem(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_toggle_share_menu_title),
                        description = stringResource(R.string.settings_toggle_share_menu_description),
                        checked = showInShareSheet,
                        onCheckedChange = { viewModel.setShowInShareSheet(it) }
                    )
                }
            }

            // Data Management Card
            SettingsCard(
                icon = Icons.Default.Save,
                title = stringResource(R.string.settings_card_data_title),
                description = stringResource(R.string.settings_card_data_description)
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
                        Text(stringResource(R.string.settings_button_export_backup))
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_button_import_backup))
                    }
                    
                    if (backupUiState is BackupUiState.Loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }

            // Notifications Card
            SettingsCard(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_card_notifications_title),
                description = stringResource(R.string.settings_card_notifications_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Master Toggle
                    SettingToggleItem(
                        icon = Icons.Default.NotificationsActive,
                        title = stringResource(R.string.settings_toggle_notifications_title),
                        description = stringResource(R.string.settings_toggle_notifications_description),
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.toggleNotifications(it) }
                    )

                    // Filter Options
                    if (notificationsEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            stringResource(R.string.settings_notify_for_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 8.dp)
                        )

                        val filters = listOf(
                            "ALL" to stringResource(R.string.settings_notify_filter_all),
                            "EVENTS" to stringResource(R.string.settings_notify_filter_events),
                            "TODOS" to stringResource(R.string.settings_notify_filter_todos)
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
                title = stringResource(R.string.settings_card_support_title),
                description = stringResource(R.string.settings_card_support_description)
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
                                stringResource(R.string.settings_section_send_feedback_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.settings_section_send_feedback_description),
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
                            text = stringResource(R.string.settings_card_advanced_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_card_advanced_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.settings_cd_open_advanced),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // About & Updates (Full Screen Entry)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                onClick = { showAboutScreen = true }
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
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_card_about_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_version_label, com.example.memgallery.BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (latestAvailableVersion != null && latestAvailableVersion != com.example.memgallery.BuildConfig.VERSION_NAME) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.settings_cd_open_about),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAboutScreen) {
        AboutScreenOverlay(
            viewModel = viewModel,
            onBack = { showAboutScreen = false }
        )
        BackHandler { showAboutScreen = false }
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
            label = { Text(stringResource(R.string.settings_gemini_api_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_gemini_placeholder)) },
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
                        contentDescription = stringResource(R.string.settings_cd_toggle_visibility),
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
                            contentDescription = stringResource(R.string.common_success),
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
                            contentDescription = stringResource(R.string.common_error),
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
                        stringResource(R.string.common_validating),
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
                Text(stringResource(R.string.common_validate))
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
                Text(stringResource(R.string.common_clear))
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
            label = { Text(stringResource(R.string.settings_groq_api_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_groq_placeholder)) },
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
                        contentDescription = stringResource(R.string.settings_cd_toggle_visibility),
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
                            contentDescription = stringResource(R.string.common_success),
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
                            contentDescription = stringResource(R.string.common_error),
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
                        stringResource(R.string.common_saving),
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
                Text(stringResource(R.string.common_save))
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
                Text(stringResource(R.string.common_clear))
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
            stringResource(R.string.settings_section_theme_mode),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = listOf(
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_dark)
            )
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
                title = stringResource(R.string.settings_toggle_dynamic_color_title),
                description = stringResource(R.string.settings_toggle_dynamic_color_description),
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
                stringResource(R.string.settings_section_accent_color),
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
            title = stringResource(R.string.settings_toggle_amoled_title),
            description = stringResource(R.string.settings_toggle_amoled_description),
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
                   text = stringResource(R.string.settings_field_model),
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
               contentDescription = stringResource(R.string.settings_cd_select_model)
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
                stringResource(R.string.settings_sheet_select_groq_model),
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
                                        contentDescription = stringResource(R.string.common_selected),
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
                                    label = stringResource(R.string.settings_badge_rpm),
                                    value = model.rpm.toString(),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                LimitBadge(
                                    label = stringResource(R.string.settings_badge_tpm),
                                    value = if (model.tpm >= 1000) "${model.tpm/1000}K" else model.tpm.toString(),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    textColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                LimitBadge(
                                    label = stringResource(R.string.settings_badge_rpd),
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenOverlay(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val latestAvailableVersion by viewModel.latestAvailableVersion.collectAsState()
    val latestChangeLog by viewModel.latestChangeLog.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    var showLogSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // App Icon
            Surface(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = com.example.memgallery.R.mipmap.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.settings_cd_app_icon),
                        modifier = Modifier.size(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_about_brand),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )

            Text(
                text = stringResource(R.string.settings_version_label, com.example.memgallery.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Update Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (latestAvailableVersion != null && latestAvailableVersion != com.example.memgallery.BuildConfig.VERSION_NAME) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.settings_about_update_available_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.settings_about_update_available_body, latestAvailableVersion ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Status Message
                    when (updateCheckState) {
                        is ApiKeyUiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(stringResource(R.string.settings_about_checking), style = MaterialTheme.typography.bodySmall)
                        }
                        is ApiKeyUiState.Success -> {
                            Text(
                                (updateCheckState as ApiKeyUiState.Success).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ApiKeyUiState.Error -> {
                            Text(
                                (updateCheckState as ApiKeyUiState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                           Text(stringResource(R.string.settings_about_up_to_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_about_check_updates))
                    }

                    if (latestChangeLog != null) {
                        OutlinedButton(
                            onClick = { showLogSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.List, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_about_view_changelog))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                stringResource(R.string.settings_about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    if (showLogSheet && latestChangeLog != null) {
        com.example.memgallery.ui.components.ChangeLogBottomSheet(
            versionName = latestAvailableVersion ?: com.example.memgallery.BuildConfig.VERSION_NAME,
            changeLog = latestChangeLog!!,
            onDismiss = { showLogSheet = false }
        )
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

@Composable
fun CustomProviderSection(
    baseUrl: String,
    modelName: String,
    apiKey: String,
    uiState: ApiKeyUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = { Text(stringResource(R.string.settings_custom_base_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_custom_base_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = modelName,
            onValueChange = onModelNameChange,
            label = { Text(stringResource(R.string.settings_custom_model_name_label)) },
            placeholder = { Text(stringResource(R.string.settings_custom_model_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.settings_custom_api_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (apiKey.isNotEmpty()) {
                    IconButton(onClick = onClearKey) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                    }
                }
            }
        )

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is ApiKeyUiState.Loading
        ) {
            if (uiState is ApiKeyUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.common_validating))
            } else {
                Text(stringResource(R.string.settings_custom_button_validate_save))
            }
        }

        if (uiState is ApiKeyUiState.Success) {
            Text(
                uiState.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        } else if (uiState is ApiKeyUiState.Error) {
            Text(
                uiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val FALLBACK_GEMINI_MODELS = listOf(
    "gemini-3.1-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.5-pro",
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite"
)

private fun describeGeminiModelResId(id: String): Int = when {
    id.contains("flash-lite") -> R.string.settings_model_helper_fastest
    id.contains("pro") -> R.string.settings_model_helper_highest
    id.contains("flash") -> R.string.settings_model_helper_balanced
    else -> 0
}

@Composable
fun GeminiModelSelection(
    selectedModelId: String,
    onClick: () -> Unit
) {
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
                    text = stringResource(R.string.settings_field_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedModelId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.settings_cd_select_model)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiModelSheet(
    currentModelId: String,
    availableModels: List<String>,
    isLoading: Boolean,
    onModelSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectiveModels = remember(availableModels) {
        if (availableModels.isEmpty()) FALLBACK_GEMINI_MODELS
        else (availableModels + FALLBACK_GEMINI_MODELS).distinct()
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    stringResource(R.string.settings_sheet_select_gemini_model),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_cd_refresh_models)
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                effectiveModels.forEach { modelId ->
                    val isSelected = modelId == currentModelId
                    val descriptionResId = describeGeminiModelResId(modelId)
                    val description = if (descriptionResId != 0) stringResource(descriptionResId) else ""
                    Card(
                        onClick = { onModelSelected(modelId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (description.isNotEmpty()) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.common_selected),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

