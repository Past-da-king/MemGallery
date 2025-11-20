package com.example.memgallery.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.viewmodels.AudioCaptureViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCaptureScreen(
    navController: NavController,
    existingImageUri: String?,
    existingAudioUri: String?,
    existingUserText: String?,
    viewModel: AudioCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingTime by viewModel.recordingTime.collectAsState()
    val amplitudes by viewModel.amplitudes.collectAsState()
    val recordedFilePath by viewModel.recordedFilePath.collectAsState()
    val error by viewModel.error.collectAsState()

    var hasPermission by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            hasPermission = true
        }
    }

    // Navigate when recording is finished and file is saved
    LaunchedEffect(recordedFilePath) {
        recordedFilePath?.let { path ->
            val fileUri = Uri.fromFile(File(path)).toString()
            navController.navigate(
                Screen.PostCapture.createRoute(
                    imageUri = existingImageUri,
                    audioUri = fileUri,
                    userText = existingUserText
                )
            ) {
                popUpTo(Screen.Gallery.route)
            }
        }
    }

    val formattedTime = remember(recordingTime) {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    // Pulsing animation for recording state
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isRecording) viewModel.stopRecording()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRecording) "Recording..." else "Ready to Record",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formattedTime,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Thin,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayLarge
                )
            }

            // Audio Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    AudioVisualizer(amplitudes = amplitudes)
                } else {
                    // Static line when not recording
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Record Button
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                )
                
                FloatingActionButton(
                    onClick = {
                        if (hasPermission) {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
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
        }
    }
}

@Composable
fun AudioVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barWidth = width / (amplitudes.size * 2f) // Spacing = barWidth
        val gap = barWidth
        
        val startX = (width - (amplitudes.size * (barWidth + gap))) / 2f

        amplitudes.forEachIndexed { index, amplitude ->
            // Animate height based on amplitude
            // Min height 4dp so it's always visible
            val barHeight = (height * amplitude).coerceAtLeast(10f)
            
            val x = startX + index * (barWidth + gap)
            val y = (height - barHeight) / 2f
            
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.6f + (amplitude * 0.4f)), // Opacity changes with loudness
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}
