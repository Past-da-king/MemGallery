package com.example.memgallery.ui.screens

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
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
import com.example.memgallery.navigation.Screen
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.DisposableEffect

private const val TAG = "AudioCaptureScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCaptureScreen(
    navController: NavController,
    existingImageUri: String?,
    existingAudioUri: String?,
    existingUserText: String?
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val mediaRecorder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (!isGranted) {
            errorMessage = "Microphone permission denied. Cannot record audio."
        }
    }

    // Request permission when the screen is first launched
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            hasPermission = true // Permissions are granted by default on older Android versions
        }
    }

    // Timer effect
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0L // Reset timer when recording starts
            while (isRecording) {
                delay(1000L)
                recordingTime++
            }
        }
    }

    // Release MediaRecorder resources when the composable leaves the composition
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                stopRecording(mediaRecorder)
            }
            mediaRecorder.release()
        }
    }

    val formattedTime = remember(recordingTime) {
        val minutes = recordingTime / 60
        val seconds = recordingTime % 60
        String.format("%02d:%02d", minutes, seconds)
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

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Start/Stop Button
            FloatingActionButton(
                onClick = {
                    if (!hasPermission) {
                        errorMessage = "Microphone permission is required to record audio."
                        return@FloatingActionButton
                    }

                    if (isRecording) {
                        stopRecording(mediaRecorder)
                        isRecording = false
                        audioFilePath?.let { path ->
                            Log.d(TAG, "Recording stopped. File path: $path")
                            val fileUri = Uri.fromFile(File(path)).toString()
                            Log.d(TAG, "Navigating with file URI: $fileUri")
                            navController.navigate(
                                Screen.PostCapture.createRoute(
                                    imageUri = existingImageUri,
                                    audioUri = fileUri,
                                    userText = existingUserText
                                )
                            ) {
                                popUpTo(Screen.Gallery.route)
                            }
                        } ?: run {
                            errorMessage = "Failed to save audio: file path is null."
                            Log.e(TAG, "audioFilePath is null after stopping recording.")
                            navController.popBackStack()
                        }
                    } else {
                        errorMessage = null
                        startRecording(context, mediaRecorder) { path ->
                            audioFilePath = path
                            isRecording = true
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
    Log.d(TAG, "Starting recording. Attempting to save to: $filePath")

    try {
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(filePath)
            prepare()
            start()
        }
        Log.d(TAG, "Recording started successfully.")
        onStart(filePath)
    } catch (e: IOException) {
        Log.e(TAG, "startRecording failed with IOException", e)
        e.printStackTrace()
    } catch (e: IllegalStateException) {
        Log.e(TAG, "startRecording failed with IllegalStateException", e)
        e.printStackTrace()
    }
}

private fun stopRecording(mediaRecorder: MediaRecorder) {
    try {
        mediaRecorder.stop()
        mediaRecorder.reset()
        Log.d(TAG, "MediaRecorder stopped and reset successfully.")
    } catch (e: IllegalStateException) {
        Log.e(TAG, "stopRecording failed with IllegalStateException", e)
        e.printStackTrace()
    } catch (e: RuntimeException) {
        Log.e(TAG, "stopRecording failed with RuntimeException", e)
        e.printStackTrace()
    }
}
