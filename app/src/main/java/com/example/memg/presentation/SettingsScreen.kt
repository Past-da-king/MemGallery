package com.example.memg.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.memg.ui.theme.DarkBlue
import com.example.memg.ui.theme.ErrorRed
import com.example.memg.ui.theme.LightBlue
import com.example.memg.ui.theme.LightGrey
import com.example.memg.ui.theme.MediumBlue
import com.example.memg.ui.theme.OffWhite
import com.example.memg.ui.theme.Slate
import com.example.memg.ui.theme.Teal
import com.example.memg.viewmodel.MemGalleryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: MemGalleryViewModel = hiltViewModel()
    val apiKey by viewModel.apiKey.collectAsState(initial = "")
    val isEnabled by viewModel.isAiEnabled.collectAsState(initial = false)
    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState(initial = "30")

    var currentApiKey by remember { mutableStateOf(apiKey) }
    var currentIsEnabled by remember { mutableStateOf(isEnabled) }
    var currentAutoDeleteDays by remember { mutableStateOf(autoDeleteDays) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(apiKey) { currentApiKey = apiKey }
    LaunchedEffect(isEnabled) { currentIsEnabled = isEnabled }
    LaunchedEffect(autoDeleteDays) { currentAutoDeleteDays = autoDeleteDays }

    Scaffold(
        containerColor = DarkBlue,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = OffWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OffWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBlue)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // AI Integration Section
            SettingsSection(title = "AI Integration") {
                SettingsRow(title = "Enable AI Features") {
                    Switch(
                        checked = currentIsEnabled,
                        onCheckedChange = { currentIsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Teal,
                            checkedThumbColor = OffWhite,
                            uncheckedTrackColor = LightBlue,
                            uncheckedThumbColor = Slate
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SettingsTextField(
                    label = "Gemini API Key",
                    value = currentApiKey,
                    onValueChange = { currentApiKey = it },
                    placeholder = "Enter your Gemini API key",
                    isPassword = true
                )
                Text(
                    "Get your API key from Google AI Studio.",
                    color = Slate,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Privacy & Data Section
            SettingsSection(title = "Privacy & Data") {
                SettingsTextField(
                    label = "Auto-delete Memories (days)",
                    value = currentAutoDeleteDays,
                    onValueChange = { currentAutoDeleteDays = it },
                    placeholder = "0 to disable"
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsButton("Export All Data", containerColor = MediumBlue, contentColor = OffWhite) { /* TODO */ }
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton("Clear All Memories", containerColor = ErrorRed.copy(alpha = 0.2f), contentColor = ErrorRed) { /* TODO */ }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        viewModel.saveSettings(currentApiKey, currentIsEnabled, currentAutoDeleteDays)
                        isSaving = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                enabled = !isSaving
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Save Settings",
                    color = DarkBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OffWhite,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(title: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MediumBlue, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = LightGrey, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Slate, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Slate) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MediumBlue,
                unfocusedContainerColor = MediumBlue,
                disabledContainerColor = MediumBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Teal,
                focusedTextColor = OffWhite,
                unfocusedTextColor = OffWhite
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (isPassword) {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = Slate
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsButton(text: String, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}