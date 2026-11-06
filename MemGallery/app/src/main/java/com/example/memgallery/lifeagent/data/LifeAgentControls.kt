package com.example.memgallery.lifeagent.data

import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.lifeagent.work.LifeAgentSchedule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Life Agent settings the Connections screen edits: the user's Composio key, the on/off switch and the daily
 * time. Every setter reschedules the daily run, so the UI never touches WorkManager. The key is the user's own and
 * lives encrypted next to the other keys; it is never logged or exposed except as "set or not".
 */
@Singleton
class LifeAgentControls @Inject constructor(
    private val settings: SettingsRepository,
    private val schedule: LifeAgentSchedule,
) {
    val enabled: Flow<Boolean> = settings.lifeAgentEnabledFlow
    val hour: Flow<Int> = settings.lifeAgentHourFlow
    val minute: Flow<Int> = settings.lifeAgentMinuteFlow
    val lastRun: Flow<Long> = settings.lifeAgentLastRunFlow

    /** Emits whether a key is saved (never the key). */
    val hasKey: Flow<Boolean> = kotlinx.coroutines.flow.flow {
        settings.composioApiKeyFlow.collect { emit(!it.isNullOrBlank()) }
    }

    suspend fun saveKey(key: String) { settings.saveComposioApiKey(key); schedule.reschedule() }
    suspend fun clearKey() { settings.clearComposioApiKey(); schedule.reschedule() }
    suspend fun setEnabled(on: Boolean) { settings.setLifeAgentEnabled(on); schedule.reschedule() }
    suspend fun setTime(hour: Int, minute: Int) { settings.setLifeAgentTime(hour, minute); schedule.reschedule() }

    /** Call on app start or when the feature screen opens: books the daily run when it is on. */
    suspend fun ensureScheduled() = schedule.ensureScheduled()
}
