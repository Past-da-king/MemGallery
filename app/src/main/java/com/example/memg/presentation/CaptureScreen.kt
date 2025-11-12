package com.example.memg.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import coil.compose.AsyncImage
import com.example.memg.model.MemoryObject
import com.example.memg.ui.theme.DarkBlue
import com.example.memg.ui.theme.ErrorRed
import com.example.memg.ui.theme.LightBlue
import com.example.memg.ui.theme.MediumBlue
import com.example.memg.ui.theme.OffWhite
import com.example.memg.ui.theme.Slate
import com.example.memg.ui.theme.Teal
import com.example.memg.util.AudioRecorder
import com.example.memg.util.ScreenCaptureService
import com.example.memg.viewmodel.MemGalleryViewModel
import com.google.gson.Gson
import java.io.File
import java.util.Date

// Enum for different capture modes
enum class CaptureMode {
    NONE, IMAGE, TEXT, AUDIO, SCREEN, POST_CAPTURE_OPTIONS
}

@Composable
fun CaptureScreen(
    onCaptureComplete: (MemoryObject) -> Unit,
    onNavigateBack: () -> Unit,
    startScreenCapture: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: MemGalleryViewModel = hiltViewModel()

    var captureMode by remember { mutableStateOf(CaptureMode.NONE) }
    var textInput by remember { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var recordedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var currentAudioFilePath by remember { mutableStateOf<String?>(null) }

    val audioRecorder = remember { AudioRecorder(context) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        Log.d("CaptureScreen", "cameraLauncher result: success = $success, capturedImageUri = $capturedImageUri")
        if (success) {
            if (capturedImageUri != null) {
                val file = File(capturedImageUri!!.path!!)
                if (file.exists()) {
                    Log.d("CaptureScreen", "Image captured and file exists: $capturedImageUri")
                    captureMode = CaptureMode.POST_CAPTURE_OPTIONS
                } else {
                    Log.e("CaptureScreen", "Image captured successfully, but file does not exist at $capturedImageUri")
                    capturedImageUri = null
                }
            } else {
                Log.e("CaptureScreen", "Image captured successfully, but capturedImageUri is null")
            }
        } else {
            capturedImageUri = null
            Log.e("CaptureScreen", "Image capture failed or cancelled")
        }
    }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, launch camera
            val photoFile = File(context.externalCacheDir, "IMG_${System.currentTimeMillis()}.jpg")
            val newUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            capturedImageUri = newUri
            Log.d("CaptureScreen", "Launching camera with newUri: $newUri")
            cameraLauncher.launch(newUri)
        } else {
            // Permission denied
            Log.e("CaptureScreen", "Camera permission denied")
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start audio recording
            currentAudioFilePath = audioRecorder.startRecording()
            isRecordingAudio = true
            captureMode = CaptureMode.AUDIO
        } else {
            // Permission denied
            Log.e("CaptureScreen", "Record audio permission denied")
        }
    }

    // Broadcast receiver for screen capture success
    val screenCaptureReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ScreenCaptureService.ACTION_CAPTURE_SUCCESS) {
                    val filePath = intent.getStringExtra(ScreenCaptureService.EXTRA_FILE_PATH)
                    if (filePath != null) {
                        capturedImageUri = Uri.fromFile(File(filePath))
                        Log.d("CaptureScreen", "Screen captured: $capturedImageUri")
                        captureMode = CaptureMode.POST_CAPTURE_OPTIONS
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(ScreenCaptureService.ACTION_CAPTURE_SUCCESS)
        LocalBroadcastManager.getInstance(context).registerReceiver(screenCaptureReceiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(screenCaptureReceiver)
            if (isRecordingAudio) {
                audioRecorder.stopRecording()
            }
        }
    }

    // Function to reset capture state
    fun resetCaptureState() {
        captureMode = CaptureMode.NONE
        textInput = ""
        isRecordingAudio = false
        capturedImageUri = null
        recordedAudioUri = null
        currentAudioFilePath = null
        audioRecorder.stopRecording() // Ensure recorder is stopped
    }

    // Function to save memory
    fun saveMemory() {
        val image = capturedImageUri?.toString()
        val text = textInput.ifBlank { null }
        val audio = recordedAudioUri?.toString()

        val summary: String
        val tags: List<String>
        val folder: String

        when {
            image != null -> {
                summary = "Captured Image"
                tags = listOf("image", "capture")
                folder = "Images"
            }
            text != null -> {
                summary = text.take(50)
                tags = listOf("text", "note")
                folder = "Notes"
            }
            audio != null -> {
                summary = "Recorded Audio"
                tags = listOf("audio", "voice")
                folder = "Audio Notes"
            }
            else -> {
                // Should not happen if save button is enabled correctly
                onNavigateBack()
                return
            }
        }

        val memory = MemoryObject(
            imageUri = image,
            audioUri = audio,
            text = text,
            summary = summary,
            tags = Gson().toJson(tags),
            folder = folder
        )
        onCaptureComplete(memory)
        resetCaptureState()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBlue)
    ) {
        // Back button
        IconButton(
            onClick = {
                if (captureMode != CaptureMode.NONE) {
                    resetCaptureState()
                } else {
                    onNavigateBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(MediumBlue)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = OffWhite
            )
        }

        // Main content based on capture mode
        AnimatedContent(
            targetState = captureMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CaptureModeTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetMode ->
            when (targetMode) {
                CaptureMode.NONE, CaptureMode.IMAGE, CaptureMode.SCREEN -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CaptureModeButton(
                            icon = Icons.Default.PhotoCamera,
                            text = "Capture Image"
                        ) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val photoFile = File(context.externalCacheDir, "IMG_${System.currentTimeMillis()}.jpg")
                                val newUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                cameraLauncher.launch(newUri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        CaptureModeButton(
                            icon = Icons.Default.TextFields,
                            text = "Add Text Note"
                        ) {
                            captureMode = CaptureMode.TEXT
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        CaptureModeButton(
                            icon = Icons.Default.Mic,
                            text = "Record Audio"
                        ) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                currentAudioFilePath = audioRecorder.startRecording()
                                isRecordingAudio = true
                                captureMode = CaptureMode.AUDIO
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        CaptureModeButton(
                            icon = Icons.Default.Monitor,
                            text = "Capture Screen"
                        ) {
                            startScreenCapture()
                        }
                    }
                }
                CaptureMode.TEXT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Text Note",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = OffWhite,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Write your note here...", color = Slate) },
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { saveMemory() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            enabled = textInput.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                tint = DarkBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Save Text Memory", color = DarkBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                CaptureMode.AUDIO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Audio Recording",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = OffWhite,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        if (isRecordingAudio) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Recording",
                                tint = ErrorRed,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Recording...",
                                color = ErrorRed,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            IconButton(
                                onClick = {
                                    audioRecorder.stopRecording()
                                    recordedAudioUri = Uri.fromFile(File(currentAudioFilePath!!))
                                    isRecordingAudio = false
                                },
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Recording",
                                    tint = OffWhite,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        } else if (recordedAudioUri != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Recording Complete",
                                tint = Teal,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Recording Complete!",
                                color = Teal,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { saveMemory() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = DarkBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Save Audio Memory", color = DarkBlue, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Tap the microphone to start recording audio.",
                                color = Slate,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        currentAudioFilePath = audioRecorder.startRecording()
                                        isRecordingAudio = true
                                    } else {
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Teal)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Start Recording",
                                    tint = DarkBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
                CaptureMode.POST_CAPTURE_OPTIONS -> {
                    Log.d("CaptureScreen", "POST_CAPTURE_OPTIONS: capturedImageUri = $capturedImageUri")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 80.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Add Details to Memory",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = OffWhite,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        capturedImageUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Captured Content",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MediumBlue),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { captureMode = CaptureMode.TEXT },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(LightBlue)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = "Add Text",
                                        tint = OffWhite,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            currentAudioFilePath = audioRecorder.startRecording()
                                            isRecordingAudio = true
                                            captureMode = CaptureMode.AUDIO
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(LightBlue)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record Audio",
                                        tint = OffWhite,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { saveMemory() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = DarkBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Save Memory", color = DarkBlue, fontWeight = FontWeight.Bold)
                            }
                        } ?: run {
                            Text(
                                text = "No content to add details to.",
                                color = Slate,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CaptureModeButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(80.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MediumBlue),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Teal,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                color = OffWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}