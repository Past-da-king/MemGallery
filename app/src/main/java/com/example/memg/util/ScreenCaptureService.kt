package com.example.memg.util

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.memg.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenCaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    companion object {
        const val ACTION_START = "com.example.memg.util.START_CAPTURE"
        const val ACTION_STOP = "com.example.memg.util.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val NOTIFICATION_CHANNEL_ID = "ScreenCaptureChannel"
        const val NOTIFICATION_ID = 123
        const val ACTION_CAPTURE_SUCCESS = "com.example.memg.CAPTURE_SUCCESS"
        const val EXTRA_FILE_PATH = "filePath"
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    startScreenCapture()
                }
            }
            ACTION_STOP -> {
                stopScreenCapture()
            }
        }
        return START_NOT_STICKY
    }

    private fun startScreenCapture() {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                Log.d("ScreenCaptureService", "MediaProjection stopped.")
                stopScreenCapture()
            }
        }, null)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                saveBitmap(bitmap)
                stopScreenCapture()
            }
        }, null)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "screenshot_$timestamp.png"
        val file = File(getExternalFilesDir(null), fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.d("ScreenCaptureService", "Screenshot saved to ${file.absolutePath}")
            broadcastCaptureSuccess(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun broadcastCaptureSuccess(filePath: String) {
        val intent = Intent(ACTION_CAPTURE_SUCCESS).apply {
            putExtra(EXTRA_FILE_PATH, filePath)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(object : MediaProjection.Callback() {}) // Unregister the callback
        mediaProjection?.stop()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("MemGallery")
                .setContentText("Capturing screen...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("MemGallery")
                .setContentText("Capturing screen...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_DEFAULT) // For older APIs
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}