package com.example.memgallery

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import com.example.memgallery.service.MemoryProcessingWorker
import com.example.memgallery.service.ScreenshotObserver
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.service.EdgeGestureService
import com.example.memgallery.service.UpdateCheckWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MemGalleryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var screenshotObserver: ScreenshotObserver

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var agentSettings: com.example.memgallery.agent.AgentBridgeSettings

    @Inject
    lateinit var lifeAgentControls: com.example.memgallery.lifeagent.data.LifeAgentControls

    @Inject
    lateinit var lifeAgentRepository: com.example.memgallery.lifeagent.data.LifeAgentRepository

    override fun onCreate() {
        super.onCreate()
        screenshotObserver.start()
        enqueueMemoryProcessingWorker()
        enqueueUpdateCheckWorker()
        checkAndStartEdgeGestureService()
        checkAndStartAgentBridge()
        armLifeAgent()
    }

    /**
     * The Life Agent's daily collection is booked from here so it survives a reinstall and a reboot, and the home
     * screen widget is redrawn whenever the agent's own data changes (a collection, a keep, a tick, a snooze).
     */
    private fun armLifeAgent() {
        lifeAgentRepository.onDataChanged = {
            com.example.memgallery.lifeagent.widget.LifeAgentWidgetRefresh.enqueue(this@MemGalleryApplication)
        }
        applicationScope.launch { runCatching { lifeAgentControls.ensureScheduled() } }
        val work = PeriodicWorkRequestBuilder<com.example.memgallery.lifeagent.widget.LifeAgentWidgetWorker>(
            repeatInterval = 3,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LifeAgentWidgetRefresh",
            ExistingPeriodicWorkPolicy.KEEP,
            work,
        )
    }

    /**
     * Brings the bridge back on cold start, and arms the 15-minute sync floor. The floor
     * is scheduled whenever a hub is configured — it is the tier that still works when
     * Tailscale is off, the push socket is dead and the phone is in a pocket.
     */
    private fun checkAndStartAgentBridge() {
        applicationScope.launch {
            try {
                if (agentSettings.mirrorEnabled.first() || agentSettings.hubUrl.first() != null) {
                    com.example.memgallery.agent.sync.MirrorSyncWorker.schedule(this@MemGalleryApplication)
                }
                if (agentSettings.enabled.first()) {
                    com.example.memgallery.service.AgentBridgeService.start(this@MemGalleryApplication)
                }
            } catch (e: Exception) {
                Log.e("MemGalleryApp", "Failed to start Agent Bridge: ${e.message}")
            }
        }
    }

    private fun checkAndStartEdgeGestureService() {
        applicationScope.launch {
            val enabled = settingsRepository.edgeGestureEnabledFlow.first()
            if (enabled) {
                val intent = Intent(this@MemGalleryApplication, EdgeGestureService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Exception) {
                    // On Android 12+, we cannot start foreground service from background
                    Log.e("MemGalleryApp", "Failed to start EdgeGestureService: ${e.message}")
                }
            }
        }
    }

    private fun enqueueMemoryProcessingWorker() {
        val workRequest = PeriodicWorkRequestBuilder<MemoryProcessingWorker>(
            repeatInterval = 15, // Repeat every 15 minutes
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setInitialDelay(10, TimeUnit.SECONDS) // Initial delay to allow app to start
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MemoryProcessingWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun enqueueUpdateCheckWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            repeatInterval = 1, // Repeat every 1 day
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("MemGalleryApp", "Providing WorkManager Configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(hiltWorkerFactory)
                .build()
        }
}
