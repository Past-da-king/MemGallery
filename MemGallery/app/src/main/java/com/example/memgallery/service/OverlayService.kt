package com.example.memgallery.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.memgallery.MainActivity
import com.example.memgallery.navigation.Screen
import com.example.memgallery.ui.theme.MemGalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var lifecycleOwner: OverlayLifecycleOwner

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        lifecycleOwner.onResume()
        return START_NOT_STICKY
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
                MemGalleryTheme {
                    OverlayContent(
                        onDismiss = { stopSelf() },
                        onNavigate = { route ->
                            val mainIntent = Intent(context, MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                putExtra("navigate_to", route)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(mainIntent)
                            stopSelf()
                        }
                    )
                }
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.onPause()
        lifecycleOwner.onDestroy()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}

@Composable
private fun OverlayContent(
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Trigger animation on mount
    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun dismiss() {
        scope.launch {
            isVisible = false
            // Wait for animation to finish before dismissing service
            kotlinx.coroutines.delay(300) 
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim
        if (isVisible) {
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
            visible = isVisible,
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
                tonalElevation = 6.dp
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
                    
                    AddContentSheetContent(
                        onNavigate = { route ->
                            isVisible = false
                            onNavigate(route)
                        },
                        onHideSheet = { dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddContentSheetContent(
    onNavigate: (String) -> Unit,
    onHideSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding() // Add padding for navigation bar
    ) {
        Text(
            "Create New",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val items = listOf(
            Triple("Text Note", Icons.Default.TextFields) {
                onHideSheet()
                onNavigate(Screen.TextInput.createRoute())
            },
            Triple("Upload Image", Icons.Default.Image) {
                onHideSheet()
                onNavigate(Screen.Gallery.route)
            },
            Triple("Take Photo", Icons.Default.PhotoCamera) {
                onHideSheet()
                onNavigate(Screen.CameraCapture.route)
            },
            Triple("Record Audio", Icons.Default.Mic) {
                onHideSheet()
                onNavigate(Screen.AudioCapture.createRoute())
            },
            Triple("Save Bookmark", Icons.Default.Bookmark) {
                onHideSheet()
                onNavigate(Screen.BookmarkInput.createRoute())
            }
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { (label, icon, action) ->
                Card(
                    onClick = action,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
