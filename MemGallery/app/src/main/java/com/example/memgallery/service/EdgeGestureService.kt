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
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class EdgeGestureService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var leftHandle: View? = null
    private var rightHandle: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        observeSettings()
    }

    private fun startForegroundService() {
        val channelId = "edge_gesture_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.notif_channel_edge_name), NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_edge_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(2002, notification)
    }

    private fun observeSettings() {
        serviceScope.launch {
            combine(
                settingsRepository.edgeGestureEnabledFlow,
                settingsRepository.edgeGestureSideFlow,
                settingsRepository.edgeGesturePositionYFlow,
                settingsRepository.edgeGestureHeightPercentFlow,
                settingsRepository.edgeGestureWidthFlow,
                settingsRepository.edgeGestureDualHandlesFlow,
                settingsRepository.edgeGestureVisibleFlow
            ) { enabled, side, posY, height, width, dual, visible ->
                updateHandles(enabled, side, posY, height, width, dual, visible)
            }.collect {}
        }
    }

    private fun updateHandles(
        enabled: Boolean,
        side: String,
        posY: Int,
        heightP: Int,
        widthD: Int,
        dual: Boolean,
        visible: Boolean
    ) {
        removeHandles()
        if (!enabled) return

        val density = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        val h = (screenH * (heightP / 100f)).toInt()
        val y = (screenH * (posY / 100f)).toInt() - (h / 2)
        val w = (widthD * density).toInt()

        if (dual || side == "LEFT") {
            leftHandle = createHandleView(visible)
            addHandleToWindow(leftHandle!!, true, w, h, y)
        }
        if (dual || side == "RIGHT") {
            rightHandle = createHandleView(visible)
            addHandleToWindow(rightHandle!!, false, w, h, y)
        }
    }

    private fun removeHandles() {
        leftHandle?.let { windowManager.removeView(it) }
        rightHandle?.let { windowManager.removeView(it) }
        leftHandle = null
        rightHandle = null
    }

    private fun createHandleView(visible: Boolean): View {
        return View(this).apply {
            setBackgroundColor(if (visible) Color.GRAY else Color.TRANSPARENT)
            alpha = 0.3f
            val detector = GestureDetector(this@EdgeGestureService, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    triggerGesture("DOUBLE_TAP")
                    return true
                }
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                    if (e1 == null) return false
                    val dy = e2.y - e1.y
                    if (dy < -100) triggerGesture("SWIPE_UP")
                    else if (dy > 100) triggerGesture("SWIPE_DOWN")
                    return true
                }
            })
            setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        }
    }

    private fun addHandleToWindow(view: View, left: Boolean, w: Int, h: Int, y: Int) {
        val params = WindowManager.LayoutParams(
            w, h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or Gravity.TOP
            this.x = 0
            this.y = y
        }
        windowManager.addView(view, params)
    }

    private fun triggerGesture(gesture: String) {
        serviceScope.launch {
            val action = when(gesture) {
                "SWIPE_UP" -> settingsRepository.edgeGestureActionSwipeUpFlow.first()
                "SWIPE_DOWN" -> settingsRepository.edgeGestureActionSwipeDownFlow.first()
                "DOUBLE_TAP" -> settingsRepository.edgeGestureActionDoubleTapFlow.first()
                else -> "NONE"
            }
            if (action != "NONE") {
                val intent = Intent(this@EdgeGestureService, OverlayService::class.java).apply {
                    putExtra("mode", action)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startService(intent)
            }
        }
    }

    private fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
        f1: kotlinx.coroutines.flow.Flow<T1>,
        f2: kotlinx.coroutines.flow.Flow<T2>,
        f3: kotlinx.coroutines.flow.Flow<T3>,
        f4: kotlinx.coroutines.flow.Flow<T4>,
        f5: kotlinx.coroutines.flow.Flow<T5>,
        f6: kotlinx.coroutines.flow.Flow<T6>,
        f7: kotlinx.coroutines.flow.Flow<T7>,
        transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
    ): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(listOf(f1, f2, f3, f4, f5, f6, f7)) { array ->
        @Suppress("UNCHECKED_CAST")
        transform(
            array[0] as T1, array[1] as T2, array[2] as T3,
            array[3] as T4, array[4] as T5, array[5] as T6, array[6] as T7
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        removeHandles()
        serviceScope.cancel()
    }
}
