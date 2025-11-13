package com.example.memg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "com.example.memg.START_OVERLAY_SERVICE"
        const val ACTION_STOP = "com.example.memg.STOP_OVERLAY_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
                addOverlayView()
            }
            ACTION_STOP -> {
                stopOverlayService()
            }
        }
        return START_STICKY
    }

    private fun addOverlayView() {
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            // Overlay already exists, do nothing
            return
        }
        val overlayEdge = sharedPreferences.getString("overlay_edge", "Right") ?: "Right"
        val overlayWidth = sharedPreferences.getInt("overlay_width", 36)
        val overlayVerticalOffset = sharedPreferences.getInt("overlay_vertical_offset", 0)
        val overlayOpacity = sharedPreferences.getInt("overlay_opacity", 50)
        val swipeSensitivity = sharedPreferences.getInt("swipe_sensitivity", 100)
        val cooldownTime = sharedPreferences.getInt("cooldown_time", 800)

        overlayView = OverlayView(
            context = this,
            initialOpacity = overlayOpacity,
            swipeSensitivity = swipeSensitivity,
            cooldownTime = cooldownTime
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.width = overlayWidth.dpToPx()
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT

        when (overlayEdge) {
            "Left" -> layoutParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            "Right" -> layoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            "Top" -> {
                layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = overlayWidth.dpToPx()
            }
            "Bottom" -> {
                layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                layoutParams.height = overlayWidth.dpToPx()
            }
        }
        layoutParams.y = overlayVerticalOffset.dpToPx() // Apply vertical offset

        windowManager.addView(overlayView, layoutParams)
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun stopOverlayService() {
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MemGallery Overlay")
            .setContentText("Overlay is active for quick captures.")
            .setSmallIcon(com.example.memg.R.mipmap.ic_launcher) // Use your app's launcher icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOverlayService()
    }
}