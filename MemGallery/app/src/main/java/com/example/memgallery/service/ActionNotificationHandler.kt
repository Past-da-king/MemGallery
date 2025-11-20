package com.example.memgallery.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.data.remote.dto.ActionDto
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionNotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val CHANNEL_ID = "action_notifications"
        private const val CHANNEL_NAME = "Action Alerts"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for events and to-dos from your memories"
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun notifyAction(action: ActionDto, memoryId: Int) {
        // Check if notifications are enabled
        val notificationsEnabled = settingsRepository.notificationsEnabledFlow.first()
        if (!notificationsEnabled) return

        // Check filter
        val filter = settingsRepository.notificationFilterFlow.first()
        val shouldNotify = when (filter) {
            "ALL" -> true
            "EVENTS" -> action.type == "EVENT"
            "TODOS" -> action.type == "TODO"
            else -> false
        }

        if (!shouldNotify) return

        // Build notification
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("memoryId", memoryId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            memoryId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getActionTitle(action.type))
            .setContentText(action.description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted, skip notification
                return
            }
        }

        NotificationManagerCompat.from(context).notify(memoryId, notification)
    }

    private fun getActionTitle(type: String): String {
        return when (type) {
            "EVENT" -> "📅 Event Reminder"
            "TODO" -> "✅ To-Do Alert"
            "REMINDER" -> "🔔 Reminder"
            else -> "Action Alert"
        }
    }
}
