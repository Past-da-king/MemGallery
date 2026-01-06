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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
    val overlayStyle by viewModel.overlayStyle.collectAsState()
    val maxToolCalls by viewModel.maxToolCalls.collectAsState()

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
                title = { Text("Advanced Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. AI System Prompt Section
            SettingsCard(
                icon = Icons.Default.Psychology,
                title = "Custom AI Persona",
                description = "Define how the AI behaves"
            ) {
                Column {
                    Text(
                        "System Prompt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "These instructions are added to every AI request and take precedence over default behavior.",
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
                                    "Tap to set a custom persona...",
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
                                Text("Edit Prompt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (showSystemPromptEditor) {
                FullScreenTextEditor(
                    title = "System Prompt",
                    initialText = userSystemPrompt,
                    placeholder = "e.g., 'Be sarcastic', 'Focus on technical details'",
                    onDismiss = { showSystemPromptEditor = false },
                    onSave = {
                        viewModel.onUserSystemPromptChange(it)
                        viewModel.saveUserSystemPrompt()
                        showSystemPromptEditor = false
                        android.widget.Toast.makeText(context, "Persona Saved", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }



            // 1.5 User Context & Memory Section
            SettingsCard(
                icon = Icons.Default.Memory,
                title = "User Context & Memory",
                description = "Manage what the AI knows"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Context Summary Field
                    Text(
                        "User Context Summary",
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
                                    "No context generated yet. Tap to edit manually or generate below.",
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
                                Text("Open Full Editor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
                                Text("Generating...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Regenerate Context")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Frequency Settings
                    Text(
                        "Auto-Generation Frequency",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "How often should the AI scan your memories to update its context?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("Always", "Daily", "Weekly", "Manual")
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
                FullScreenTextEditor(
                    title = "User Context",
                    initialText = userContextSummary,
                    placeholder = "Enter your custom context here...",
                    onDismiss = { showUserContextEditor = false },
                    onSave = {
                        viewModel.onUserContextSummaryChange(it)
                        viewModel.saveUserContextSummary()
                        showUserContextEditor = false
                        android.widget.Toast.makeText(context, "User Context Saved", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. Behavior Configuration Section
            SettingsCard(
                icon = Icons.Default.Tune,
                title = "Behavior",
                description = "Customize app interactions"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // 1.8 Max Tool Calls Section
                    Text(
                        "Max AI Tool Calls: ${maxToolCalls}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Limit the number of steps the AI can take to solve a query.",
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
                        title = "Auto-Generate Reminders",
                        description = "Allow AI to automatically create tasks and reminders from your memories",
                        checked = autoRemindersEnabled,
                        onCheckedChange = viewModel::setAutoRemindersEnabled
                    )

                    SettingToggleItem(
                        icon = Icons.Default.Mic,
                        title = "Audio Auto-Start",
                        description = "Start recording immediately when sheet opens",
                        checked = audioAutoStart,
                        onCheckedChange = viewModel::setAudioAutoStart
                    )

                    HorizontalDivider()

                    Text("Post-Capture Action", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose what happens after capturing a note, audio, or link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = postCaptureBehavior == "FOREGROUND",
                            onClick = { viewModel.setPostCaptureBehavior("FOREGROUND") },
                            label = { Text("Open App") },
                            leadingIcon = if (postCaptureBehavior == "FOREGROUND") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = postCaptureBehavior == "BACKGROUND",
                            onClick = { viewModel.setPostCaptureBehavior("BACKGROUND") },
                            label = { Text("Stay in Background") },
                            leadingIcon = if (postCaptureBehavior == "BACKGROUND") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }

            // 3. Overlay & Gestures Section
            SettingsCard(
                icon = Icons.Default.Swipe,
                title = "Overlay & Gestures",
                description = "Configure how you access the overlay"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Overlay Style Selector
                    Text("Overlay Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf("Edge Gesture", "Quick Ball")
                        val values = listOf("EDGE", "BALL")
                        values.forEachIndexed { index, value ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                onClick = { viewModel.setOverlayStyle(value) },
                                selected = overlayStyle == value
                            ) {
                                Text(options[index])
                            }
                        }
                    }

                    HorizontalDivider()

                    if (overlayStyle == "EDGE") {
                        // Edge Gesture Settings
                        SettingToggleItem(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Enable Edge Gesture",
                            description = "Show a handle on the screen edge",
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
                                    Text("Overlay permission required", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        HorizontalDivider()

                        // Appearance
                        Text("Appearance & Position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        SettingToggleItem(
                            icon = Icons.Default.Visibility,
                            title = "Visible Handle",
                            description = "Show the visual indicator",
                            checked = isVisible,
                            onCheckedChange = viewModel::setEdgeGestureVisible
                        )

                        if (!dualHandles) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val options = listOf("Left Edge", "Right Edge")
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
                            title = "Dual Handles",
                            description = "Show handles on both sides",
                            checked = dualHandles,
                            onCheckedChange = viewModel::setEdgeGestureDualHandles
                        )

                        // Sliders
                        Text("Vertical Position: $positionY%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = positionY.toFloat(),
                            onValueChange = { viewModel.setEdgeGesturePositionY(it.toInt()) },
                            valueRange = 0f..100f
                        )

                        Text("Height: $heightPercent%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = heightPercent.toFloat(),
                            onValueChange = { viewModel.setEdgeGestureHeightPercent(it.toInt()) },
                            valueRange = 10f..100f
                        )

                        Text("Thickness: ${widthDp}dp", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = widthDp.toFloat(),
                            onValueChange = { viewModel.setEdgeGestureWidth(it.toInt()) },
                            valueRange = 10f..60f
                        )

                        HorizontalDivider()

                        // Gesture Mappings
                        Text("Gesture Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        ActionDropdown(
                            label = "Swipe Up",
                            selectedAction = swipeUpAction,
                            onActionSelected = viewModel::setEdgeGestureActionSwipeUp
                        )

                        ActionDropdown(
                            label = "Swipe Down",
                            selectedAction = swipeDownAction,
                            onActionSelected = viewModel::setEdgeGestureActionSwipeDown
                        )

                        ActionDropdown(
                            label = "Double Tap",
                            selectedAction = doubleTapAction,
                            onActionSelected = viewModel::setEdgeGestureActionDoubleTap
                        )
                    } else {
                        // Quick Ball Settings
                        Text(
                            "Quick Ball is active. You can drag it anywhere on the screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        SettingToggleItem(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Enable Overlay",
                            description = "Show the Quick Ball",
                            checked = edgeGestureEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } else {
                                    viewModel.setEdgeGestureEnabled(enabled)
                                }
                            }
                        )
                    }
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
    val actions = mapOf(
        "NONE" to "None",
        "QUICK_CAPTURE" to "Quick Capture (Menu)",
        "ADD_TASK" to "Add Task Sheet",
        "ADD_URL" to "Add URL Sheet",
        "ADD_MEMORY" to "Add Memory Sheet",
        "QUICK_AUDIO" to "Quick Audio",
        "QUICK_TEXT" to "Quick Text",
        "CAMERA" to "Open Camera"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = actions[selectedAction] ?: "None",
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onSave(text) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
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