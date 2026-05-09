package com.example.memgallery.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.memgallery.BuildConfig
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.data.remote.github.GitHubService
import com.example.memgallery.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val githubService: GitHubService,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val response = githubService.getLatestRelease()
            if (response.isSuccessful) {
                val latestRelease = response.body() ?: return Result.failure()
                val latestVersion = latestRelease.tagName
                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(latestVersion, currentVersion)) {
                    settingsRepository.setLatestAvailableVersion(latestVersion)
                    settingsRepository.setLatestChangeLog(latestRelease.body)
                    settingsRepository.setHasShownUpdateLog(false)
                    
                    showUpdateNotification(latestVersion)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.removePrefix("v").split(".")
        val currentClean = current.removePrefix("v").split(".")

        for (i in 0 until minOf(latestClean.size, currentClean.size)) {
            val l = latestClean[i].toIntOrNull() ?: 0
            val c = currentClean[i].toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return latestClean.size > currentClean.size
    }

    private fun showUpdateNotification(version: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "updates_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notif_channel_updates_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            // Use standard app icon if specific update icon is missing
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(applicationContext.getString(R.string.notif_update_title))
            .setContentText(applicationContext.getString(R.string.notif_update_text, version))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }
}
