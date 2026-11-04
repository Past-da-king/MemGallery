package com.example.memgallery.agent.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val TAG = "MirrorSyncWorker"

/**
 * Tier 3 — the floor.
 *
 * Fifteen minutes is Android's minimum period for periodic work, and that is fine: this
 * tier is not here to be fast, it is here to be *certain*. Tailscale can be off, the push
 * socket can be dead, the ntfy topic can be rate-limited, and the operator's agents still
 * eventually see reality. Everything above this is an optimisation on latency.
 */
@HiltWorker
class MirrorSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hubSync: HubSync
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        hubSync.syncNow()
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Periodic sync failed", e)
        // Retry rather than fail: a hub that's briefly down shouldn't cost a whole cycle.
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "agent_mirror_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MirrorSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
