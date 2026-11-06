package com.example.memgallery.lifeagent.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily collection at the user's hour and minute (default 17:00) and the hourly nudge check.
 *
 * The daily run is a chain of one-time jobs in two alternating slots: each run books the next day's run in the
 * OTHER slot before it starts, so a failure never breaks the chain and the time never drifts. WorkManager keeps
 * the queue across reboots. Call [ensureScheduled] on app start or when the feature screen opens, and
 * [reschedule] whenever the time, the key or the on/off switch changes (LifeAgentRepository's setters do).
 */
@Singleton
class LifeAgentSchedule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val wm get() = WorkManager.getInstance(context)

    /** Books the daily run and the nudge check when the feature is on and a key exists; cancels both otherwise. */
    suspend fun ensureScheduled() = apply(replace = false)

    /** Same, but moves an already booked run to the new time. */
    suspend fun reschedule() = apply(replace = true)

    fun cancel() {
        wm.cancelUniqueWork(slotName(SLOT_A))
        wm.cancelUniqueWork(slotName(SLOT_B))
        wm.cancelUniqueWork(NUDGE_NAME)
    }

    private suspend fun apply(replace: Boolean) {
        val enabled = settings.lifeAgentEnabledFlow.first()
        val hasKey = !settings.composioApiKey().isNullOrBlank()
        if (!enabled || !hasKey) { cancel(); return }
        val hour = settings.lifeAgentHourFlow.first()
        val minute = settings.lifeAgentMinuteFlow.first()
        if (replace) {
            wm.cancelUniqueWork(slotName(SLOT_A)); wm.cancelUniqueWork(slotName(SLOT_B))
            enqueueDaily(SLOT_A, hour, minute)
        } else if (!hasPending()) {
            enqueueDaily(SLOT_A, hour, minute)
        }
        wm.enqueueUniquePeriodicWork(
            NUDGE_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NudgeWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    private fun hasPending(): Boolean = listOf(SLOT_A, SLOT_B).any { slot ->
        try {
            wm.getWorkInfosForUniqueWork(slotName(slot)).get().any { !it.state.isFinished }
        } catch (e: Exception) { false }
    }

    /** Books the next run in [slot] (called by the worker for the opposite slot of the one that is running). */
    fun enqueueDaily(slot: String, hour: Int, minute: Int, now: Long = System.currentTimeMillis()) {
        val delay = nextRunDelayMs(now, hour, minute, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<LifeAgentCollectionWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(LifeAgentCollectionWorker.KEY_SLOT to slot))
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(slotName(slot), ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val TAG = "life_agent_daily"
        const val SLOT_A = "a"
        const val SLOT_B = "b"
        const val NUDGE_NAME = "life_agent_nudge"
        fun slotName(slot: String) = "life_agent_daily_$slot"
        fun otherSlot(slot: String) = if (slot == SLOT_A) SLOT_B else SLOT_A

        /** Milliseconds from [now] to the next [hour]:[minute] strictly in the future (tomorrow when it has passed). */
        fun nextRunDelayMs(now: Long, hour: Int, minute: Int, zone: ZoneId): Long {
            val nowZ = Instant.ofEpochMilli(now).atZone(zone)
            var next = nowZ.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone)
            if (!next.toInstant().isAfter(nowZ.toInstant().plusSeconds(60))) next = next.plusDays(1)
            return next.toInstant().toEpochMilli() - now
        }
    }
}
