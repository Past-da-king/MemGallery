package com.example.memgallery.lifeagent.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * The daily collection. Books tomorrow's run first, so nothing below can break the chain. A run where one app
 * fails is still a success; only a run where every app failed on the network is retried (twice, with backoff).
 */
@HiltWorker
class LifeAgentCollectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: LifeAgentRepository,
    private val schedule: LifeAgentSchedule,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(KEY_SLOT) ?: LifeAgentSchedule.SLOT_A
        if (!settings.lifeAgentEnabledFlow.first() || settings.composioApiKey().isNullOrBlank()) {
            schedule.cancel()
            return Result.success()
        }
        schedule.enqueueDaily(
            LifeAgentSchedule.otherSlot(slot),
            settings.lifeAgentHourFlow.first(),
            settings.lifeAgentMinuteFlow.first(),
        )
        val result = try {
            repository.runCollection()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("LifeAgentWorker", "run failed", e)
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        return if (result.status == "failed" && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object { const val KEY_SLOT = "slot" }
}
