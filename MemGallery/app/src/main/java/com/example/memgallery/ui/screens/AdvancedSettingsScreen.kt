package com.example.memgallery.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.R
import com.example.memgallery.service.EdgeGestureService
import com.example.memgallery.ui.viewmodels.SettingsViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // State
    val userSystemPrompt by viewModel.userSystemPrompt.collectAsState()
    val edgeGestureEnabled by viewModel.edgeGestureEnabled.collectAsState()
    val edgeGestureSide by viewModel.edgeGestureSide.collectAsState()
    val swipeUpAction by viewModel.edgeGestureActionSwipeUp.collectAsState()
    val swipeDownAction by viewModel.edgeGestureActionSwipeDown.collectAsState()
    val doubleTapAction by viewModel.edgeGestureActionDoubleTap.collectAsState()

    // New State
    val positionY by viewModel.edgeGesturePositionY.collectAsState()
    val heightPercent by viewModel.edgeGestureHeightPercent.collectAsState()
    val widthDp by viewModel.edgeGestureWidth.collectAsState()
    val dualHandles by viewModel.edgeGestureDualHandles.collectAsState()
    val isVisible by viewModel.edgeGestureVisible.collectAsState()
    val audioAutoStart by viewModel.audioAutoStart.collectAsState()
    val postCaptureBehavior by viewModel.postCaptureBehavior.collectAsState()
    val autoRemindersEnabled by viewModel.autoRemindersEnabled.collectAsState()
    val syncDeletionEnabled by viewModel.syncDeletionEnabled.collectAsState()
    val maxToolCalls by viewModel.maxToolCalls.collectAsState()
    val externalTaskManager by viewModel.externalTaskManager.collectAsState()

    // User Context State
    val userContextSummary by viewModel.userContextSummary.collectAsState()
    val userContextFrequency by viewModel.userContextGenerationFrequency.collectAsState()
    val isGeneratingContext by viewModel.isGeneratingContext.collectAsState()
    
    // Editor States
    var showSystemPromptEditor by remember { mutableStateOf(false) }
    var showUserContextEditor by remember { mutableStateOf(false) }

    // Permission Handling
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_settings_title)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. AI System Prompt Section
            SettingsCard(
                icon = Icons.Default.Psychology,
                title = stringResource(R.string.advanced_card_persona_title),
                description = stringResource(R.string.advanced_card_persona_description)
            ) {
                Column {
                    Text(
                        stringResource(R.string.advanced_section_system_prompt),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.advanced_system_prompt_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // Preview Card
                    Surface(
                        onClick = { showSystemPromptEditor = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            if (userSystemPrompt.isBlank()) {
                                Text(
                                    stringResource(R.string.advanced_system_prompt_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                Text(
                                    userSystemPrompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.advanced_button_edit_prompt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (showSystemPromptEditor) {
                val personaSavedMessage = stringResource(R.string.advanced_toast_persona_saved)
                FullScreenTextEditor(
                    title = stringResource(R.string.advanced_editor_system_prompt_title),
                    initialText = userSystemPrompt,
                    placeholder = stringResource(R.string.advanced_editor_system_prompt_placeholder),
                    onDismiss = { showSystemPromptEditor = false },
                    onSave = {
                        viewModel.onUserSystemPromptChange(it)
                        viewModel.saveUserSystemPrompt()
                        showSystemPromptEditor = false
                        android.widget.Toast.makeText(context, personaSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }



            // 1.5 User Context & Memory Section
            SettingsCard(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.advanced_card_user_context_title),
                description = stringResource(R.string.advanced_card_user_context_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Context Summary Field
                    Text(
                        stringResource(R.string.advanced_section_user_context),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Preview Card
                    Surface(
                        onClick = { showUserContextEditor = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            if (userContextSummary.isBlank()) {
                                Text(
                                    stringResource(R.string.advanced_user_context_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                Text(
                                    userContextSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 5,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.advanced_button_open_full_editor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = viewModel::generateUserContext,
                            enabled = !isGeneratingContext
                        ) {
                            if (isGeneratingContext) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.advanced_button_generating))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.advanced_button_regenerate_context))
                            }
                        }
                    }

                    HorizontalDivider()

                    // Frequency Settings
                    Text(
                        stringResource(R.string.advanced_section_auto_gen_frequency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.advanced_auto_gen_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(
                            stringResource(R.string.advanced_freq_always),
                            stringResource(R.string.advanced_freq_daily),
                            stringResource(R.string.advanced_freq_weekly),
                            stringResource(R.string.advanced_freq_manual)
                        )
                        val values = listOf("ALWAYS", "DAILY", "WEEKLY", "MANUAL")
                        values.forEachIndexed { index, value ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                onClick = { viewModel.setUserContextGenerationFrequency(value) },
                                selected = userContextFrequency == value
                            ) {
                                Text(
                                    options[index],
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            
            if (showUserContextEditor) {
                val userContextSavedMessage = stringResource(R.string.advanced_toast_user_context_saved)
                FullScreenTextEditor(
                    title = stringResource(R.string.advanced_editor_user_context_title),
                    initialText = userContextSummary,
                    placeholder = stringResource(R.string.advanced_editor_user_context_placeholder),
                    onDismiss = { showUserContextEditor = false },
                    onSave = {
                        viewModel.onUserContextSummaryChange(it)
                        viewModel.saveUserContextSummary()
                        showUserContextEditor = false
                        android.widget.Toast.makeText(context, userContextSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. Behavior Configuration Section
            SettingsCard(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.advanced_card_behavior_title),
                description = stringResource(R.string.advanced_card_behavior_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // 1.8 Max Tool Calls Section
                    Text(
                        stringResource(R.string.advanced_max_tool_calls, maxToolCalls),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.advanced_max_tool_calls_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = maxToolCalls.toFloat(),
                        onValueChange = { viewModel.setMaxToolCalls(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    HorizontalDivider()

                    SettingToggleItem(
                        icon = Icons.Default.Event,
                        title = stringResource(R.string.advanced_toggle_auto_reminders_title),
                        description = stringResource(R.string.advanced_toggle_auto_reminders_description),
                        checked = autoRemindersEnabled,
                        onCheckedChange = viewModel::setAutoRemindersEnabled
                    )

                    SettingToggleItem(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.advanced_toggle_sync_delete_title),
                        description = stringResource(R.string.advanced_toggle_sync_delete_description),
                        checked = syncDeletionEnabled,
                        onCheckedChange = viewModel::setSyncDeletionEnabled
                    )

                    SettingToggleItem(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.advanced_toggle_audio_autostart_title),
                        description = stringResource(R.string.advanced_toggle_audio_autostart_description),
                        checked = audioAutoStart,
                        onCheckedChange = viewModel::setAudioAutoStart
                    )

                    HorizontalDivider()

                    Text(stringResource(R.string.advanced_section_post_capture), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.advanced_post_capture_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = postCaptureBehavior == "FOREGROUND",
                            onClick = { viewModel.setPostCaptureBehavior("FOREGROUND") },
                            label = { Text(stringResource(R.string.advanced_post_capture_open_app)) },
                            leadingIcon = if (postCaptureBehavior == "FOREGROUND") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = postCaptureBehavior == "BACKGROUND",
                            onClick = { viewModel.setPostCaptureBehavior("BACKGROUND") },
                            label = { Text(stringResource(R.string.advanced_post_capture_stay_background)) },
                            leadingIcon = if (postCaptureBehavior == "BACKGROUND") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }

                    HorizontalDivider()

                    // External Task Manager Integration
                    Text(stringResource(R.string.advanced_section_external_task_manager), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.advanced_external_task_manager_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExternalTaskManagerDropdown(
                        selectedManager = externalTaskManager,
                        onManagerSelected = viewModel::setExternalTaskManager
                    )
                }
            }

            // 3. Overlay & Gestures Section
            SettingsCard(
                icon = Icons.Default.Swipe,
                title = stringResource(R.string.advanced_card_overlay_title),
                description = stringResource(R.string.advanced_card_overlay_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Edge Gesture Settings
                    SettingToggleItem(
                        icon = Icons.Default.PowerSettingsNew,
                        title = stringResource(R.string.advanced_toggle_edge_gesture_title),
                        description = stringResource(R.string.advanced_toggle_edge_gesture_description),
                        checked = edgeGestureEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !Settings.canDrawOverlays(context)) {
                                // Request Permission
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } else {
                                viewModel.setEdgeGestureEnabled(enabled)
                            }
                        }
                    )

                    if (!hasOverlayPermission && edgeGestureEnabled) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.advanced_overlay_permission_required), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    HorizontalDivider()

                    // Appearance
                    Text(stringResource(R.string.advanced_section_appearance_position), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    SettingToggleItem(
                        icon = Icons.Default.Visibility,
                        title = stringResource(R.string.advanced_toggle_visible_handle_title),
                        description = stringResource(R.string.advanced_toggle_visible_handle_description),
                        checked = isVisible,
                        onCheckedChange = viewModel::setEdgeGestureVisible
                    )

                    if (!dualHandles) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(
                                stringResource(R.string.advanced_edge_left),
                                stringResource(R.string.advanced_edge_right)
                            )
                            val values = listOf("LEFT", "RIGHT")
                            values.forEachIndexed { index, value ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    onClick = { viewModel.setEdgeGestureSide(value) },
                                    selected = edgeGestureSide == value
                                ) {
                                    Text(options[index])
                                }
                            }
                        }
                    }

                    SettingToggleItem(
                        icon = Icons.Default.CompareArrows,
                        title = stringResource(R.string.advanced_toggle_dual_handles_title),
                        description = stringResource(R.string.advanced_toggle_dual_handles_description),
                        checked = dualHandles,
                        onCheckedChange = viewModel::setEdgeGestureDualHandles
                    )

                    // Sliders
                    Text(stringResource(R.string.advanced_slider_vertical_position, positionY), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = positionY.toFloat(),
                        onValueChange = { viewModel.setEdgeGesturePositionY(it.toInt()) },
                        valueRange = 0f..100f
                    )

                    Text(stringResource(R.string.advanced_slider_height, heightPercent), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = heightPercent.toFloat(),
                        onValueChange = { viewModel.setEdgeGestureHeightPercent(it.toInt()) },
                        valueRange = 10f..100f
                    )

                    Text(stringResource(R.string.advanced_slider_thickness, widthDp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = widthDp.toFloat(),
                        onValueChange = { viewModel.setEdgeGestureWidth(it.toInt()) },
                        valueRange = 10f..60f
                    )

                    HorizontalDivider()

                    // Gesture Mappings
                    Text(stringResource(R.string.advanced_section_gesture_actions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    ActionDropdown(
                        label = stringResource(R.string.advanced_gesture_swipe_up),
                        selectedAction = swipeUpAction,
                        onActionSelected = viewModel::setEdgeGestureActionSwipeUp
                    )

                    ActionDropdown(
                        label = stringResource(R.string.advanced_gesture_swipe_down),
                        selectedAction = swipeDownAction,
                        onActionSelected = viewModel::setEdgeGestureActionSwipeDown
                    )

                    ActionDropdown(
                        label = stringResource(R.string.advanced_gesture_double_tap),
                        selectedAction = doubleTapAction,
                        onActionSelected = viewModel::setEdgeGestureActionDoubleTap
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDropdown(
    label: String,
    selectedAction: String,
    onActionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.common_none)
    val actions = mapOf(
        "NONE" to noneLabel,
        "QUICK_CAPTURE" to stringResource(R.string.advanced_action_quick_capture_menu),
        "ADD_TASK" to stringResource(R.string.advanced_action_add_task_sheet),
        "ADD_URL" to stringResource(R.string.advanced_action_add_url_sheet),
        "ADD_MEMORY" to stringResource(R.string.advanced_action_add_memory_sheet),
        "QUICK_AUDIO" to stringResource(R.string.advanced_action_quick_audio),
        "QUICK_TEXT" to stringResource(R.string.advanced_action_quick_text),
        "CAMERA" to stringResource(R.string.advanced_action_open_camera)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = actions[selectedAction] ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            actions.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onActionSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalTaskManagerDropdown(
    selectedManager: String,
    onManagerSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.common_none)
    val managers = mapOf(
        "NONE" to stringResource(R.string.advanced_external_none),
        "TICKTICK" to stringResource(R.string.advanced_external_ticktick),
        "GOOGLE_TASKS" to stringResource(R.string.advanced_external_google_tasks),
        "TODOIST" to stringResource(R.string.advanced_external_todoist),
        "SHARE" to stringResource(R.string.advanced_external_system_share)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = managers[selectedManager] ?: noneLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.advanced_target_app_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            managers.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onManagerSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenTextEditor(

    title: String,
    initialText: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { onSave(text) }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_save))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}