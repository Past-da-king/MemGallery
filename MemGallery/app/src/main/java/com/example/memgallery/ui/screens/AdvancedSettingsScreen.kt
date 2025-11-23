package com.example.memgallery.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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

    // Permission Handling
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LaunchedEffect(edgeGestureEnabled) {
        if (edgeGestureEnabled && hasOverlayPermission) {
            startEdgeGestureService(context)
        } else {
            stopEdgeGestureService(context)
        }
    }

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
                    OutlinedTextField(
                        value = userSystemPrompt,
                        onValueChange = viewModel::onUserSystemPromptChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = { Text("e.g., 'Be sarcastic', 'Focus on technical details', 'Use bullet points only'") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.saveUserSystemPrompt()
                            android.widget.Toast.makeText(context, "Saved!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Prompt")
                    }
                }
            }

            // 2. Edge Gesture Section
            SettingsCard(
                icon = Icons.Default.Swipe,
                title = "Edge Gesture",
                description = "Global gestures to trigger actions"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Main Toggle
                    SettingToggleItem(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "Enable Edge Gesture",
                        description = "Show a small handle on the screen edge",
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

                    // Side Selection
                    Text("Position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    
                    Text(
                        "The gesture handle is small (max 33% height) and positioned on the selected edge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
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
    val actions = mapOf(
        "NONE" to "None",
        "QUICK_CAPTURE" to "Quick Capture (Menu)",
        "ADD_TASK" to "Add Task Sheet",
        "ADD_URL" to "Add URL Sheet",
        "ADD_MEMORY" to "Add Memory Sheet",
        "QUICK_AUDIO" to "Quick Audio",
        "QUICK_TEXT" to "Quick Text"
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

private fun startEdgeGestureService(context: Context) {
    val intent = Intent(context, EdgeGestureService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopEdgeGestureService(context: Context) {
    val intent = Intent(context, EdgeGestureService::class.java)
    context.stopService(intent)
}
