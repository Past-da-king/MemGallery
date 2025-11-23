package com.example.memgallery.ui.components.sheets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.memgallery.ui.screens.AudioVisualizer
import kotlinx.coroutines.delay

@Composable
fun QuickAudioSheet(
    onDismiss: () -> Unit,
    onRecordingSaved: (String) -> Unit,
    isRecording: Boolean,
    recordingTime: Long,
    amplitudes: List<Float>,
    recordedFilePath: String?,
    error: String?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current

    // Handle permission locally in the sheet UI (Check only)
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Effect to auto-start recording if permission granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && !isRecording) {
            onStartRecording()
        }
    }

    // Effect for completion
    LaunchedEffect(recordedFilePath) {
        if (recordedFilePath != null) {
            // Wait a beat for animation
            delay(500)
            onRecordingSaved(recordedFilePath)
        }
    }

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val formattedTime = remember(recordingTime) {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Quick Audio Note",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (!hasPermission) {
            // Permission denied state
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Microphone Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Permission")
                }
            }
        } else {
            // Recording State
            // Time
            Text(
                text = formattedTime,
                fontSize = 64.sp,
                fontWeight = FontWeight.Thin,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AudioVisualizer(amplitudes = amplitudes)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Record Button
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isRecording) "Tap to stop" else "Tap to record",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
