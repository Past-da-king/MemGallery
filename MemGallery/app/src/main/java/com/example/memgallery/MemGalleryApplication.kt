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
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MemGalleryApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var screenshotObserver: ScreenshotObserver



    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        screenshotObserver.start()
        enqueueMemoryProcessingWorker()
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

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("MemGalleryApp", "Providing WorkManager Configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(hiltWorkerFactory)
                .build()
        }
}
