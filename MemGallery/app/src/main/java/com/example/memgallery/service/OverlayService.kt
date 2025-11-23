package com.example.memgallery.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.memgallery.MainActivity
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.data.repository.MemoryRepository
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.components.sheets.*
import com.example.memgallery.ui.theme.MemGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var memoryRepository: MemoryRepository

    @Inject
    lateinit var taskDao: TaskDao

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var lifecycleOwner: OverlayLifecycleOwner
    private val serviceScope = MainScope()
    
    private var currentMode by mutableStateOf("ADD_MEMORY")
    private var overlayAudioRecorder: OverlayAudioRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.onCreate()
        overlayAudioRecorder = OverlayAudioRecorder(this, serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("mode") ?: "ADD_MEMORY"
        currentMode = mode
        
        if (overlayView == null) {
            showOverlay()
        }
        lifecycleOwner.onResume()
        return START_NOT_STICKY
    }

    private fun handlePostCapture(newItemUri: String? = null, newItemId: Long? = null) {
        serviceScope.launch {
            val behavior = settingsRepository.postCaptureBehaviorFlow.first()
            if (behavior == "FOREGROUND") {
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    if (newItemId != null) {
                        putExtra("navigate_to", Screen.Detail.createRoute(newItemId.toInt()))
                    } else {
                        // If we don't have ID immediately (pending), just go to gallery
                        putExtra("navigate_to", Screen.Gallery.route)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            } else {
                // Background - show toast
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Saved to MemGallery", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveTask(title: String, description: String, date: java.time.LocalDate, time: String?, type: String, isRecurring: Boolean, rule: String?) {
        serviceScope.launch {
            val newTask = TaskEntity(
                title = title,
                description = description,
                dueDate = date.toString(),
                dueTime = time,
                type = type,
                isRecurring = isRecurring,
                recurrenceRule = rule,
                status = "PENDING"
            )
            taskDao.insertTask(newTask)
            handlePostCapture() // Task saving logic usually keeps user in flow, but respecting setting is fine
        }
    }

    private fun saveUrl(url: String) {
        serviceScope.launch {
            val result = memoryRepository.savePendingMemory(
                imageUri = null,
                audioUri = null,
                userText = null,
                bookmarkUrl = url
            )
            val id = result.getOrNull()
            handlePostCapture(newItemId = id)
        }
    }

    private fun saveText(text: String) {
        serviceScope.launch {
            val result = memoryRepository.savePendingMemory(
                imageUri = null,
                audioUri = null,
                userText = text
            )
            val id = result.getOrNull()
            handlePostCapture(newItemId = id)
        }
    }

    private fun saveAudio(path: String) {
        serviceScope.launch {
            val uri = Uri.fromFile(java.io.File(path)).toString()
            val result = memoryRepository.savePendingMemory(
                imageUri = null,
                audioUri = uri,
                userText = null
            )
            val id = result.getOrNull()
            handlePostCapture(newItemId = id)
        }
    }

    private fun showOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val dynamicTheming by settingsRepository.dynamicThemingEnabledFlow.collectAsState(initial = true)
                val appThemeMode by settingsRepository.appThemeModeFlow.collectAsState(initial = "SYSTEM")
                val amoledMode by settingsRepository.amoledModeEnabledFlow.collectAsState(initial = false)
                val selectedColor by settingsRepository.selectedColorFlow.collectAsState(initial = -1)
                
                // Settings for Audio
                val audioAutoStart by settingsRepository.audioAutoStartFlow.collectAsState(initial = true)

                MemGalleryTheme(
                    dynamicColor = dynamicTheming,
                    appThemeMode = appThemeMode,
                    amoledMode = amoledMode,
                    customColor = selectedColor
                ) {
                    if (overlayAudioRecorder != null) {
                        OverlayContent(
                            mode = currentMode,
                            audioAutoStart = audioAutoStart,
                            onDismiss = { stopSelf() },
                            onNavigate = { route ->
                                val mainIntent = Intent(context, MainActivity::class.java).apply {
                                    action = Intent.ACTION_VIEW
                                    putExtra("navigate_to", route)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                startActivity(mainIntent)
                                stopSelf()
                            },
                            onSaveTask = ::saveTask,
                            onSaveUrl = ::saveUrl,
                            onSaveText = ::saveText,
                            onSaveAudio = ::saveAudio,
                            audioRecorder = overlayAudioRecorder!!
                        )
                    }
                }
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.onPause()
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
        overlayAudioRecorder?.cleanup()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}

class OverlayAudioRecorder(private val context: android.content.Context, private val scope: kotlinx.coroutines.CoroutineScope) {
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var outputFile: java.io.File? = null

    var isRecording by mutableStateOf(false)
        private set
    var recordingTime by mutableLongStateOf(0L)
        private set
    var amplitudes by mutableStateOf(List(30) { 0f })
        private set
    var recordedFilePath by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun startRecording() {
        if (isRecording) return

        val fileName = "AUD_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.m4a"
        outputFile = java.io.File(context.externalCacheDir, fileName)

        mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.media.MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            android.media.MediaRecorder()
        }.apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile?.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                error = null
                startTimerAndPolling()
            } catch (e: java.io.IOException) {
                e.printStackTrace()
                error = "Failed to start recording"
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                error = "Failed to start recording"
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        var success = false
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            success = true
        } catch (e: RuntimeException) {
            e.printStackTrace()
            outputFile?.delete()
            error = "Recording failed. Too short?"
        } finally {
            mediaRecorder = null
            recordingJob?.cancel()
            isRecording = false
            
            if (success && outputFile?.exists() == true) {
                recordedFilePath = outputFile?.absolutePath
            } else {
                recordedFilePath = null
                if (!success && error == null) error = "Recording failed."
            }
        }
    }

    private fun startTimerAndPolling() {
        recordingJob = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                val elapsedTime = System.currentTimeMillis() - startTime
                recordingTime = elapsedTime / 1000

                mediaRecorder?.maxAmplitude?.let { maxAmp ->
                    val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                    val currentList = amplitudes.toMutableList()
                    currentList.removeAt(0)
                    currentList.add(normalized)
                    amplitudes = currentList
                }
                delay(50)
            }
        }
    }
    
    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
        mediaRecorder?.release()
    }
}

@Composable
private fun OverlayContent(
    mode: String,
    audioAutoStart: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onSaveTask: (String, String, java.time.LocalDate, String?, String, Boolean, String?) -> Unit,
    onSaveUrl: (String) -> Unit,
    onSaveText: (String) -> Unit,
    onSaveAudio: (String) -> Unit,
    audioRecorder: OverlayAudioRecorder
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch {
            visibleState.targetState = false
            delay(300) 
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim
        if (visibleState.currentState || visibleState.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismiss() }
                    )
            )
        }

        // Sheet
        androidx.compose.animation.AnimatedVisibility(
            visibleState = visibleState,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
        ) {
            // Draggable State
            var offsetY by remember { mutableFloatStateOf(0f) }
            val draggableState = rememberDraggableState { delta ->
                val newOffset = offsetY + delta
                offsetY = newOffset.coerceAtLeast(0f) // Only allow dragging down
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetY.toInt()) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            if (offsetY > 150f) { // Threshold to dismiss
                                dismiss()
                            } else {
                                offsetY = 0f // Snap back
                            }
                        }
                    )
                    .windowInsetsPadding(WindowInsets.ime), // Handle keyboard
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .padding(vertical = 22.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    
                    // Content Switcher
                    when (mode) {
                        "ADD_MEMORY" -> {
                            AddMemorySheet(
                                onTextNote = { 
                                    dismiss()
                                    onNavigate(Screen.TextInput.createRoute())
                                },
                                onUploadImage = {
                                    dismiss()
                                    onNavigate(Screen.Gallery.route) 
                                },
                                onTakePhoto = {
                                    dismiss()
                                    onNavigate(Screen.CameraCapture.route)
                                },
                                onRecordAudio = {
                                    dismiss()
                                    onNavigate(Screen.AudioCapture.createRoute())
                                },
                                onSaveBookmark = {
                                    dismiss()
                                    onNavigate(Screen.BookmarkInput.createRoute())
                                }
                            )
                        }
                        "ADD_TASK" -> {
                            AddTaskSheet(
                                taskToEdit = null,
                                onDismiss = { dismiss() },
                                onSave = { title, desc, date, time, type, recur, rule ->
                                    onSaveTask(title, desc, date, time, type, recur, rule)
                                    dismiss()
                                }
                            )
                        }
                        "ADD_URL" -> {
                            AddUrlSheet(
                                onDismiss = { dismiss() },
                                onAddLink = { url ->
                                    onSaveUrl(url)
                                    dismiss()
                                }
                            )
                        }
                        "QUICK_AUDIO" -> {
                            com.example.memgallery.ui.components.sheets.QuickAudioSheet(
                                onDismiss = { dismiss() },
                                onRecordingSaved = { path ->
                                    onSaveAudio(path)
                                    dismiss()
                                },
                                isRecording = audioRecorder.isRecording,
                                recordingTime = audioRecorder.recordingTime,
                                amplitudes = audioRecorder.amplitudes,
                                recordedFilePath = audioRecorder.recordedFilePath,
                                error = audioRecorder.error,
                                onStartRecording = { audioRecorder.startRecording() },
                                onStopRecording = { audioRecorder.stopRecording() },
                                autoStart = audioAutoStart
                            )
                        }
                        "QUICK_TEXT" -> {
                            QuickTextSheet(
                                onDismiss = { dismiss() },
                                onSaveText = { text ->
                                    onSaveText(text)
                                    dismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}