package com.example.memgallery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class EdgeGestureService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var windowManager: WindowManager
    private var gestureView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
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
        setupEdgeView()
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

    private fun setupEdgeView() {
        serviceScope.launch {
            val side = settingsRepository.edgeGestureSideFlow.first()
            val gravity = if (side == "LEFT") Gravity.LEFT or Gravity.CENTER_VERTICAL else Gravity.RIGHT or Gravity.CENTER_VERTICAL
            
            // Width: 60px touch area
            // Height: 30% of screen (User asked for 33.3% or less)
            val width = 60
            val height = (resources.displayMetrics.heightPixels * 0.30).toInt()

            layoutParams = WindowManager.LayoutParams(
                width,
                height,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
            }

            gestureView = View(this@EdgeGestureService).apply {
                // Visual indicator: Semi-transparent pill
                setBackgroundColor(Color.parseColor("#40888888")) // Grey with alpha
                
                // Touch Listener
                setOnTouchListener { _, event ->
                    handleTouch(event)
                    true // Consume event
                }
            }

            try {
                windowManager.addView(gestureView, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                startTime = System.currentTimeMillis()
                // Visual feedback
                gestureView?.setBackgroundColor(Color.parseColor("#808C25F4")) // Primary color alpha
            }
            MotionEvent.ACTION_UP -> {
                // Reset visual
                gestureView?.setBackgroundColor(Color.parseColor("#40888888"))

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
                gestureView?.setBackgroundColor(Color.parseColor("#40888888"))
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
            "QUICK_CAPTURE" -> startOverlayService("ADD_MEMORY") // Previously "Quick Capture" triggered add memory sheet
            "ADD_TASK" -> startOverlayService("ADD_TASK")
            "ADD_URL" -> startOverlayService("ADD_URL")
            "ADD_MEMORY" -> startOverlayService("ADD_MEMORY")
            "QUICK_AUDIO" -> startOverlayService("QUICK_AUDIO") // Added
            "QUICK_TEXT" -> startOverlayService("QUICK_TEXT") // Added
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
        if (gestureView != null) {
            try {
                windowManager.removeView(gestureView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
