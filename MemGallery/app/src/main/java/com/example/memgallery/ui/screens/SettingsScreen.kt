package com.example.memgallery.ui.screens

import com.example.memgallery.data.remote.ai.GeminiModels

import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.HeaderBackButton
import com.example.memgallery.ui.design.SegmentedChoice
import com.example.memgallery.ui.design.colors
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.example.memgallery.ui.design.MemSheet
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


    val aiProviderForHero by viewModel.aiProvider.collectAsState()
    val localModelPathForHero by viewModel.localModelPath.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        SettingsHeader(onBack = { navController.navigateUp() })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActiveProviderCard(provider = aiProviderForHero, localModelLoaded = !localModelPathForHero.isNullOrBlank())
            // AI: one provider per job, keys and models, web search
            SettingsCard(
                icon = Icons.Default.Key,
                title = stringResource(R.string.settings_card_ai_title),
                description = stringResource(R.string.settings_card_ai_description),
                grouped = true
            ) {
                AiSettingsSection(viewModel)
            }

            SettingsCard(
                icon = Icons.Default.Language,
                title = stringResource(R.string.websearch_title),
                description = stringResource(R.string.websearch_hint),
                grouped = true
            ) {
                WebSearchSection(viewModel)
            }

            // Appearance Card
            SettingsCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_card_appearance_title),
                description = stringResource(R.string.settings_card_appearance_description),
                grouped = true
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
                description = stringResource(R.string.settings_card_features_description),
                grouped = true
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
                        },
                        shape = groupedShape(0, 4), tone = com.example.memgallery.ui.design.Tone.Sky
                    )


                    SettingToggleItem(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.settings_toggle_highlight_title),
                        description = stringResource(R.string.settings_toggle_highlight_description),
                        checked = viewModel.showHighlights.collectAsState().value,
                        onCheckedChange = { viewModel.setShowHighlights(it) },
                        shape = groupedShape(1, 4), tone = com.example.memgallery.ui.design.Tone.Tertiary
                    )


                    // Task Screen
                    SettingToggleItem(
                        icon = Icons.Default.Task,
                        title = stringResource(R.string.settings_toggle_task_manager_title),
                        description = stringResource(R.string.settings_toggle_task_manager_description),
                        checked = taskScreenEnabled,
                        onCheckedChange = { viewModel.setTaskScreenEnabled(it) },
                        shape = groupedShape(2, 4), tone = com.example.memgallery.ui.design.Tone.Success
                    )


                    // Share Menu
                    SettingToggleItem(
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.settings_toggle_share_menu_title),
                        description = stringResource(R.string.settings_toggle_share_menu_description),
                        checked = showInShareSheet,
                        onCheckedChange = { viewModel.setShowInShareSheet(it) },
                        shape = groupedShape(3, 4), tone = com.example.memgallery.ui.design.Tone.Amber
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
            NotificationsCard(
                enabled = notificationsEnabled,
                filter = notificationFilter,
                onToggle = { viewModel.toggleNotifications(it) },
                onFilter = { viewModel.setNotificationFilter(it) }
            )

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

            // Agent Access, Advanced settings, About: one grouped list of navigation rows
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsNavRow(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.agent_access_title),
                    subtitle = stringResource(R.string.agent_access_subtitle),
                    shape = groupedShape(0, 3),
                    onClick = { navController.navigate(com.example.memgallery.navigation.Screen.AgentAccess.route) }
                )
                SettingsNavRow(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.settings_card_advanced_title),
                    subtitle = stringResource(R.string.settings_card_advanced_description),
                    shape = groupedShape(1, 3),
                    onClick = { navController.navigate(com.example.memgallery.navigation.Screen.AdvancedSettings.route) }
                )
                SettingsNavRow(
                    icon = Icons.Default.Update,
                    title = stringResource(R.string.settings_card_about_title),
                    subtitle = stringResource(R.string.settings_version_label, com.example.memgallery.BuildConfig.VERSION_NAME),
                    shape = groupedShape(2, 3),
                    badge = latestAvailableVersion != null && latestAvailableVersion != com.example.memgallery.BuildConfig.VERSION_NAME,
                    onClick = { showAboutScreen = true }
                )
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

/** Notifications: the master switch and, when on, which actions to notify for (a connected button group, not radio buttons). */
@Composable
fun NotificationsCard(enabled: Boolean, filter: String, onToggle: (Boolean) -> Unit, onFilter: (String) -> Unit) {
    SettingsCard(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.settings_card_notifications_title),
        description = stringResource(R.string.settings_card_notifications_description),
        grouped = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingToggleItem(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.settings_toggle_notifications_title),
                description = stringResource(R.string.settings_toggle_notifications_description),
                checked = enabled,
                onCheckedChange = onToggle,
                shape = groupedShape(0, if (enabled) 2 else 1),
                tone = com.example.memgallery.ui.design.Tone.Tertiary
            )
            if (enabled) {
                Column(
                    Modifier.fillMaxWidth().clip(groupedShape(1, 2)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_notify_for_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    SegmentedChoice(
                        options = listOf(
                            "ALL" to stringResource(R.string.settings_notify_filter_all),
                            "EVENTS" to stringResource(R.string.settings_notify_filter_events),
                            "TODOS" to stringResource(R.string.settings_notify_filter_todos)
                        ),
                        selectedKey = filter,
                        onSelect = onFilter
                    )
                }
            }
        }
    }
}

/** Corner shape for item [index] of [count] in a grouped list: big outer corners, small inner corners. */
fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 28.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner, topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner, bottomEnd = if (index == count - 1) outer else inner
    )
}

/** Header of the Settings page: back arrow, version eyebrow and the big title on the blob. */
@Composable
fun SettingsHeader(onBack: () -> Unit, version: String = com.example.memgallery.BuildConfig.VERSION_NAME) {
    BlobHeader {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, bottom = 50.dp), verticalAlignment = Alignment.Top) {
            HeaderBackButton(onBack, Modifier.padding(top = 6.dp))
            Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
                Text(
                    stringResource(R.string.settings_header_eyebrow, version).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** The card at the top: which AI is doing the work and whether anything leaves the phone. */
@Composable
fun ActiveProviderCard(provider: String, localModelLoaded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val isLocal = provider == "LOCAL"
    val name = when (provider) {
        "LOCAL" -> stringResource(R.string.processing_provider_local)
        "GROQ" -> stringResource(R.string.settings_ai_provider_groq)
        "GEMINI" -> stringResource(R.string.settings_ai_provider_gemini)
        "DEEPSEEK" -> stringResource(R.string.ai_provider_deepseek)
        else -> stringResource(R.string.settings_ai_provider_custom)
    }
    val overline = stringResource(if (isLocal) R.string.settings_hero_local_overline else R.string.settings_hero_cloud_overline)
    val body = stringResource(
        when {
            isLocal && localModelLoaded -> R.string.settings_hero_local_loaded
            isLocal -> R.string.settings_hero_local_missing
            else -> R.string.settings_hero_cloud_body
        }
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.primaryContainer)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isLocal) Icons.Default.Smartphone else Icons.Default.Cloud, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(overline.uppercase(), style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.headlineLarge, color = scheme.onPrimaryContainer)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer.copy(alpha = 0.85f))
    }
}

/** A titled section: small caps label, then its content in grouped containers. */
@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String,
    grouped: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (grouped) {
            Column { content() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp)
            ) { content() }
        }
    }
}

/** A row that opens another page: tonal icon square, title, supporting text and an arrow. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    badge: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (badge) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.error))
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = scheme.onSurfaceVariant)
    }
}

/** A switch row: tonal icon square, title, supporting text and a switch that shows a check when on. */
@Composable
fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    tone: com.example.memgallery.ui.design.Tone = com.example.memgallery.ui.design.Tone.Tertiary,
    container: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val (toneBg, toneFg) = tone.colors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (container) Modifier.clip(shape).background(scheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp) else Modifier)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(toneBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = toneFg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
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
    val scheme = MaterialTheme.colorScheme
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showAccent = !dynamicThemingEnabled || !dynamicSupported
    val rowCount = 2 + (if (dynamicSupported) 1 else 0) + (if (showAccent) 1 else 0)
    var row = 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Theme mode
        Column(
            Modifier.fillMaxWidth().clip(groupedShape(row++, rowCount)).background(scheme.surfaceContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_section_theme_mode), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            SegmentedChoice(
                options = listOf(
                    "LIGHT" to stringResource(R.string.settings_theme_light),
                    "SYSTEM" to stringResource(R.string.settings_theme_system),
                    "DARK" to stringResource(R.string.settings_theme_dark),
                ),
                selectedKey = appThemeMode,
                onSelect = onAppThemeModeChange,
                icons = mapOf("LIGHT" to Icons.Default.LightMode, "DARK" to Icons.Default.DarkMode),
                selectedCheck = true
            )
        }

        if (dynamicSupported) {
            SettingToggleItem(
                icon = Icons.Default.ColorLens,
                title = stringResource(R.string.settings_toggle_dynamic_color_title),
                description = stringResource(R.string.settings_toggle_dynamic_color_description),
                checked = dynamicThemingEnabled,
                onCheckedChange = onDynamicThemingChange,
                shape = groupedShape(row++, rowCount),
                tone = com.example.memgallery.ui.design.Tone.Tertiary
            )
        }

        // Accent colour (only when dynamic colour is off or not supported)
        if (showAccent) {
            Column(
                Modifier.fillMaxWidth().clip(groupedShape(row++, rowCount)).background(scheme.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.settings_section_accent_color), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val defaultSwatch = com.example.memgallery.ui.theme.MemTokens.Light.primary.toArgb()
                    val colors = listOf(
                        defaultSwatch,
                        0xFFBF0031.toInt(), 0xFF006D3B.toInt(), 0xFF006874.toInt(), 0xFF3A5BA9.toInt(), 0xFF825500.toInt(),
                        0xFF009688.toInt(), 0xFF3F51B5.toInt(), 0xFFCDDC39.toInt(), 0xFFFFC107.toInt(), 0xFFFF5722.toInt(),
                        0xFFE91E63.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(), 0xFF9C27B0.toInt(), 0xFF2196F3.toInt(),
                        0xFF4CAF50.toInt(), 0xFFFFEB3B.toInt(), 0xFF9E9E9E.toInt(), 0xFF000000.toInt()
                    )
                    colors.forEach { color ->
                        // The first swatch is the app's own palette: choosing it clears the custom colour.
                        val isSelected = (selectedColor == color) || (selectedColor == -1 && color == defaultSwatch)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { onSelectedColorChange(if (color == defaultSwatch) -1 else color) }
                                .then(if (isSelected) Modifier.border(3.dp, scheme.onSurface, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        SettingToggleItem(
            icon = Icons.Default.DarkMode,
            title = stringResource(R.string.settings_toggle_amoled_title),
            description = stringResource(R.string.settings_toggle_amoled_description),
            checked = amoledModeEnabled,
            onCheckedChange = onAmoledModeChange,
            shape = groupedShape(row++, rowCount),
            tone = com.example.memgallery.ui.design.Tone.Primary
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
    
    MemSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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

private val FALLBACK_GEMINI_MODELS = listOf(GeminiModels.FALLBACK_DEFAULT)

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

    MemSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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

