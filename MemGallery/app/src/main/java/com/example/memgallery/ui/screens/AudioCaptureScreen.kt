package com.example.memgallery.ui.screens

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.memgallery.ui.viewmodels.MemoryCreationViewModel
import com.example.memgallery.ui.viewmodels.MemoryCreationUiState
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCaptureScreen(
    navController: NavController,
    viewModel: MemoryCreationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }

    val mediaRecorder = remember { MediaRecorder() }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            // Permission granted, start recording automatically
            startRecording(context, mediaRecorder) { path ->
                audioFilePath = path
                isRecording = true
            }
        } else {
            // Permission denied, show a message or navigate back
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            hasPermission = true // Permissions are granted by default on older Android versions
            startRecording(context, mediaRecorder) { path ->
                audioFilePath = path
                isRecording = true
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000L)
                recordingTime++
            }
        }
    }

    val formattedTime = remember(recordingTime) {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is MemoryCreationUiState.Success) {
            navController.popBackStack(route = "gallery", inclusive = false)
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Audio") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isRecording) {
                            stopRecording(mediaRecorder)
                        }
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Timer
            Text(
                text = formattedTime,
                fontSize = 80.sp,
                fontWeight = FontWeight.Light
            )

            // Placeholder for Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Waveform placeholder", modifier = Modifier.align(Alignment.Center))
            }

            // Stop/Save Button
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        stopRecording(mediaRecorder)
                        isRecording = false
                        audioFilePath?.let { path ->
                            viewModel.createMemory(
                                imageUri = null,
                                audioUri = path,
                                userText = null
                            )
                        } ?: run {
                            // Handle error: audioFilePath is null
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun startRecording(context: Context, mediaRecorder: MediaRecorder, onStart: (String) -> Unit) {
    val fileName = "AUD_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
    val filePath = File(context.externalCacheDir, fileName).absolutePath

    try {
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(filePath)
            prepare()
            start()
        }
        onStart(filePath)
    } catch (e: IOException) {
        e.printStackTrace()
        // Handle error
    }
}

private fun stopRecording(mediaRecorder: MediaRecorder) {
    try {
        mediaRecorder.stop()
        mediaRecorder.reset()
    } catch (e: Exception) {
        e.printStackTrace()
        // Handle error
    }
}