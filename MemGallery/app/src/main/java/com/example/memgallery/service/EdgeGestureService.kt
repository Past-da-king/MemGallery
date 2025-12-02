package com.example.memgallery.service

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
import androidx.core.app.NotificationCompat
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
    
    // Views for Left and Right handles
    private var leftView: View? = null
    private var rightView: View? = null
    
    // View for Quick Ball
    private var ballView: androidx.compose.ui.platform.ComposeView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var isBallExpanded = false
    private var ballLifecycleOwner: OverlayLifecycleOwner? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    
    // Gesture configs
    private val SWIPE_THRESHOLD = 100f
    private val DOUBLE_TAP_TIMEOUT = 300L
    private var lastTapTime = 0L

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
            .setContentTitle("Edge Gestures Active")
            .setContentText("Tap to configure")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(2002, notification)
    }

    private fun setupEdgeViews() {
        serviceScope.launch {
            // Combine returns Array<Any?> when > 5 flows
            combine(
                settingsRepository.edgeGestureSideFlow,
                settingsRepository.edgeGesturePositionYFlow,
                settingsRepository.edgeGestureHeightPercentFlow,
                settingsRepository.edgeGestureWidthFlow,
                settingsRepository.edgeGestureDualHandlesFlow,
                settingsRepository.edgeGestureVisibleFlow,
                settingsRepository.overlayStyleFlow
            ) { args ->
                val side = args[0] as String
                val posY = args[1] as Int
                val heightPercent = args[2] as Int
                val widthDp = args[3] as Int
                val dual = args[4] as Boolean
                val visible = args[5] as Boolean
                val style = args[6] as String
                
                updateOverlay(style, side, posY, heightPercent, widthDp, dual, visible)
            }.collect {}
        }
    }

    private fun updateOverlay(style: String, side: String, posY: Int, heightPercent: Int, widthDp: Int, dual: Boolean, visible: Boolean) {
        if (style == "BALL") {
            // Remove Edge Views
            removeEdgeViews()
            // Show Ball
            if (visible) updateBallView() else removeBallView()
        } else {
            // Remove Ball View
            removeBallView()
            // Show Edge Views
            updateEdgeViews(side, posY, heightPercent, widthDp, dual, visible)
        }
    }

    private fun removeEdgeViews() {
        leftView?.let { if (it.parent != null) windowManager.removeView(it) }
        leftView = null
        rightView?.let { if (it.parent != null) windowManager.removeView(it) }
        rightView = null
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
        if (ballView == null) {
            val density = resources.displayMetrics.density
            val ballSizePx = (56 * density).toInt()
            
            ballParams = WindowManager.LayoutParams(
                ballSizePx,
                ballSizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
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
                                isBallExpanded = expanded
                                updateBallWindowSize(expanded)
                            },
                            onNavigate = { action ->
                                executeAction(action)
                            },
                            onDrag = { delta ->
                                ballParams?.let { params ->
                                    params.x += delta.x.toInt()
                                    params.y += delta.y.toInt()
                                    windowManager.updateViewLayout(this, params)
                                }
                            },
                            onDragEnd = {
                                // Snap logic could go here
                            }
                        )
                    }
                }
            }
            windowManager.addView(ballView, ballParams)
        }
    }

    private fun updateBallWindowSize(expanded: Boolean) {
        ballParams?.let { params ->
            val density = resources.displayMetrics.density
            val ballSizePx = (56 * density).toInt()
            val expandedSizePx = (300 * density).toInt() // Enough for ball + menu
            val offsetDiff = (expandedSizePx - ballSizePx) / 2

            if (expanded) {
                params.width = expandedSizePx
                params.height = expandedSizePx
                params.x -= offsetDiff
                params.y -= offsetDiff
                // When expanded, we want to capture clicks outside to close
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            } else {
                params.width = ballSizePx
                params.height = ballSizePx
                params.x += offsetDiff
                params.y += offsetDiff
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            }
            windowManager.updateViewLayout(ballView, params)
        }
    }

    private fun updateEdgeViews(side: String, posY: Int, heightPercent: Int, widthDp: Int, dual: Boolean, visible: Boolean) {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val widthPx = (widthDp * density).toInt()
        val heightPx = (screenHeight * (heightPercent / 100f)).toInt()
        
        // Vertical Position calculation
        // posY is 0 (top) to 100 (bottom). 
        // Gravity.TOP starts at 0. So y = (screenHeight - heightPx) * (posY / 100)
        val yOffset = ((screenHeight - heightPx) * (posY / 100f)).toInt()

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density // Rounded pill
            setColor(Color.parseColor("#40888888")) // Default Grey with alpha
        }
        
        if (!visible) {
            bgDrawable.setColor(Color.TRANSPARENT)
        }

        // Helper to create/update view
        fun updateView(isLeft: Boolean, currentView: View?): View {
            val gravity = if (isLeft) Gravity.LEFT or Gravity.TOP else Gravity.RIGHT or Gravity.TOP
            
            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                this.y = yOffset
            }

            val view = currentView ?: View(this@EdgeGestureService).apply {
                setOnTouchListener { v, event ->
                    handleTouch(v, event, visible)
                    true
                }
            }
            
            view.background = bgDrawable

            if (view.parent != null) {
                windowManager.updateViewLayout(view, params)
            } else {
                try {
                    windowManager.addView(view, params)
                } catch (e: Exception) { e.printStackTrace() }
            }
            return view
        }

        // Handle Left View
        if (dual || side == "LEFT") {
            leftView = updateView(true, leftView)
        } else {
            leftView?.let { 
                if (it.parent != null) windowManager.removeView(it) 
            }
            leftView = null
        }

        // Handle Right View
        if (dual || side == "RIGHT") {
            rightView = updateView(false, rightView)
        } else {
            rightView?.let { 
                if (it.parent != null) windowManager.removeView(it) 
            }
            rightView = null
        }
    }

    private fun handleTouch(view: View, event: MotionEvent, isVisible: Boolean) {
        val baseColor = if (isVisible) Color.parseColor("#40888888") else Color.TRANSPARENT
        val activeColor = Color.parseColor("#808C25F4") // Primary color alpha

        val bg = view.background as? GradientDrawable ?: return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                startTime = System.currentTimeMillis()
                if (isVisible) bg.setColor(activeColor)
            }
            MotionEvent.ACTION_UP -> {
                if (isVisible) bg.setColor(baseColor)

                val endX = event.rawX
                val endY = event.rawY
                val diffX = endX - startX
                val diffY = endY - startY
                val duration = System.currentTimeMillis() - startTime

                if (abs(diffY) > abs(diffX) && abs(diffY) > SWIPE_THRESHOLD) {
                    // Vertical Swipe
                    if (diffY < 0) {
                        triggerAction("SWIPE_UP")
                    } else {
                        triggerAction("SWIPE_DOWN")
                    }
                } else if (abs(diffX) < 20 && abs(diffY) < 20 && duration < 200) {
                    // Tap
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                        triggerAction("DOUBLE_TAP")
                        lastTapTime = 0
                    } else {
                        lastTapTime = currentTime
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isVisible) bg.setColor(baseColor)
            }
        }
    }

    private fun triggerAction(gesture: String) {
        serviceScope.launch {
            val actionType = when (gesture) {
                "SWIPE_UP" -> settingsRepository.edgeGestureActionSwipeUpFlow.first()
                "SWIPE_DOWN" -> settingsRepository.edgeGestureActionSwipeDownFlow.first()
                "DOUBLE_TAP" -> settingsRepository.edgeGestureActionDoubleTapFlow.first()
                else -> "NONE"
            }

            executeAction(actionType)
        }
    }

    private fun executeAction(actionType: String) {
        // This maps the settings action to the OverlayService mode
        when (actionType) {
            "QUICK_CAPTURE" -> startOverlayService("ADD_MEMORY")
            "ADD_TASK" -> startOverlayService("ADD_TASK")
            "ADD_URL" -> startOverlayService("ADD_URL")
            "ADD_MEMORY" -> startOverlayService("ADD_MEMORY")
            "QUICK_AUDIO" -> startOverlayService("QUICK_AUDIO")
            "QUICK_TEXT" -> startOverlayService("QUICK_TEXT")
            "CAMERA" -> {
                // Launch App Camera
                val appCameraIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("navigate_to", Screen.CameraCapture.route)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(appCameraIntent)
            }
            "NONE" -> {}
        }
    }

    private fun startOverlayService(mode: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("mode", mode)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        leftView?.let { if (it.parent != null) windowManager.removeView(it) }
        rightView?.let { if (it.parent != null) windowManager.removeView(it) }
        ballView?.let { if (it.parent != null) windowManager.removeView(it) }
    }
}
