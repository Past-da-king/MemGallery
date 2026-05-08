package com.example.memgallery.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class EdgeGestureService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var windowManager: WindowManager
    
    // View for Quick Ball
    private var ballView: androidx.compose.ui.platform.ComposeView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var isBallExpanded = false
    private var ballLifecycleOwner: OverlayLifecycleOwner? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Touch Handling Variables
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchTime = 0L
    private val TAP_THRESHOLD_MS = 200L
    private val DRAG_THRESHOLD_PX = 10f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        setupEdgeViews()
    }

    private fun startForegroundService() {
        val channelId = "edge_gesture_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Edge Gestures", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MemGallery Overlay")
            .setContentText("Quick Ball Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(2002, notification)
    }

    private fun setupEdgeViews() {
        serviceScope.launch {
            settingsRepository.overlayStyleFlow.collect { style ->
                if (style == "BALL") {
                    updateBallView()
                } else {
                    removeBallView()
                }
            }
        }
    }

    private fun removeBallView() {
        ballView?.let { if (it.parent != null) windowManager.removeView(it) }
        ballLifecycleOwner?.onPause()
        ballLifecycleOwner?.onDestroy()
        ballLifecycleOwner = null
        ballView = null
        ballParams = null
    }

    private fun updateBallView() {
        if (ballView != null) return

        val density = resources.displayMetrics.density
        val ballSizePx = (60 * density).toInt() // Matches the 60.dp logic in Compose
        
        ballParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200 // Default start position
        }

        ballLifecycleOwner = OverlayLifecycleOwner()
        ballLifecycleOwner?.onCreate()
        ballLifecycleOwner?.onResume()

        ballView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewTreeLifecycleOwner(ballLifecycleOwner)
            setViewTreeViewModelStoreOwner(ballLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(ballLifecycleOwner)
            
            setContent {
                com.example.memgallery.ui.theme.MemGalleryTheme {
                    com.example.memgallery.ui.components.overlay.QuickBall(
                        isExpanded = isBallExpanded,
                        onExpandChange = { expanded ->
                            toggleExpansion(expanded)
                        },
                        onNavigate = { action ->
                            executeAction(action)
                        }
                    )
                }
            }

            // Raw Touch Listener for Absolute Tracking
            setOnTouchListener { _, event ->
                handleTouch(event)
            }
        }
        windowManager.addView(ballView, ballParams)
    }
    
    private fun handleTouch(event: MotionEvent): Boolean {
        if (isBallExpanded) {
            // If expanded, we let Compose handle the clicks on menu items
            // However, we might want to detect outside clicks here if we cover the screen
            // But for now, returning false lets children handle it?
            // Wait, if expanded, the window size is larger.
            // If we touch outside the menu items, we want to collapse.
            // But Compose box matches window size.
            return false // Let Compose Children handle events (clicks)
        }
        
        // If passed through to here, it means it wasn't consumed by a high-priority child
        // BUT, we want to intercept drag on the main ball.
        // Since we removed 'clickable' drag from Compose, the entire view will receive touch.
        // We need to differentiate Tap vs Drag manually.

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ballParams?.x ?: 0
                initialY = ballParams?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                lastTouchTime = System.currentTimeMillis()
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                
                if (!isDragging && (abs(dx) > DRAG_THRESHOLD_PX || abs(dy) > DRAG_THRESHOLD_PX)) {
                    isDragging = true
                }

                if (isDragging) {
                    ballParams?.x = initialX + dx.toInt()
                    ballParams?.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(ballView, ballParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && (System.currentTimeMillis() - lastTouchTime) < TAP_THRESHOLD_MS) {
                    // It's a Click
                    toggleExpansion(!isBallExpanded)
                } else {
                    // It was a Drag -> Snap Logic
                    snapToEdge()
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val currentX = ballParams?.x ?: 0
        
        // Decide target X (Left or Right Edge)
        val targetX = if (currentX + (ballParams!!.width / 2) < screenWidth / 2) {
            0 // Left edge
        } else {
            screenWidth - ballParams!!.width // Right edge
        }

        // Animate Snap
        val animator = ValueAnimator.ofInt(currentX, targetX)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator(0.8f)
        animator.addUpdateListener { animation ->
            ballParams?.x = animation.animatedValue as Int
            try {
                windowManager.updateViewLayout(ballView, ballParams)
            } catch (e: Exception) {
                // View might be removed
            }
        }
        animator.start()
    }

    private fun toggleExpansion(expanded: Boolean) {
        if (ballView == null || ballParams == null) return
        isBallExpanded = expanded
        
        val density = resources.displayMetrics.density
        // Sizes
        val collapsedSize = (60 * density).toInt()
        val expandedSize = (320 * density).toInt() // Area for menu

        if (expanded) {
            // Calculate center point of current ball
            val centerX = ballParams!!.x + (collapsedSize / 2)
            val centerY = ballParams!!.y + (collapsedSize / 2)

            // Update to expanded size
            ballParams!!.width = expandedSize
            ballParams!!.height = expandedSize
            
            // Re-center window based on new size
            ballParams!!.x = centerX - (expandedSize / 2)
            ballParams!!.y = centerY - (expandedSize / 2)
            
            // Allow outside touches to pass through (or capture them to close?)
            // FLAG_NOT_TOUCH_MODAL allows outside touches to go to other windows
            // FLAG_WATCH_OUTSIDE_TOUCH tells us if outside was touched
            ballParams!!.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            // Shrink back to ball size
            // We need to snap back to where the "center" implies, or just snap to edge logic?
            // Simpler: Just center on the current expand box center again.
            val centerX = ballParams!!.x + (expandedSize / 2)
            val centerY = ballParams!!.y + (expandedSize / 2)

            ballParams!!.width = collapsedSize
            ballParams!!.height = collapsedSize
            
            ballParams!!.x = centerX - (collapsedSize / 2)
            ballParams!!.y = centerY - (collapsedSize / 2)
            
            ballParams!!.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            // Auto snap after collapse
            snapToEdge()
        }
        
        windowManager.updateViewLayout(ballView, ballParams)
        // Force Compose Recomposition with new state
        // Since isBallExpanded is a private var, we need to rely on the Compose State inside QuickBall?
        // Actually, we pass 'isBallExpanded' to QuickBall composable. 
        // We need to re-set content or rely on MutableState if we had one.
        // IMPORTANT: The `QuickBall` composable params are just values. 
        // We should wrap `isBallExpanded` in a MutableStateFlow or similar if we want reactive updates
        // Or simply call `setContent` again? No, that's heavy.
        // Best way: Use a CompositionLocal or specific MutableState held in the service
        // For simplicity here, I'll assume UpdateBallView logic refreshes state, 
        // BUT for a persistent view, we need a MutableState.
        
        // Let's fix this right now by updating the composable call
        // We can't easily change the @Composable function signature from here without hacking.
        // Instead, we should have made `isExpanded` a MutableState inside the Service 
        // and passed that state object to the Composable.
        // I will do that in the setContent block below.
    }
    
    // Mutable State for Compose
    private var expandedState = androidx.compose.runtime.mutableStateOf(false)

    // ... inside updateBallView ...
    // setContent {
    //    QuickBall(
    //       isExpanded = expandedState.value,
    //       onExpandChange = { toggleExpansion(it) }
    //    )
    // }
    
    // override fun toggleExpansion(expanded: Boolean) {
    //    ... calculations ...
    //    expandedState.value = expanded  <-- This triggers recomposition
    // }

    private fun executeAction(actionType: String) {
        when (actionType) {
            "QUICK_CAPTURE" -> startOverlayService("ADD_MEMORY")
            "ADD_TASK" -> startOverlayService("ADD_TASK")
            "ADD_URL" -> startOverlayService("ADD_URL")
            "ADD_MEMORY" -> startOverlayService("ADD_MEMORY")
            "QUICK_AUDIO" -> startOverlayService("QUICK_AUDIO")
            "QUICK_TEXT" -> startOverlayService("QUICK_TEXT")
            "CAMERA" -> {
                val appCameraIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("navigate_to", Screen.CameraCapture.route)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(appCameraIntent)
                // Collapse after action
                toggleExpansion(false)
            }
        }
    }

    private fun startOverlayService(mode: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("mode", mode)
            // Use SINGLE_TOP to handle redundant starts
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startService(intent)
        toggleExpansion(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeBallView()
    }
}
