package com.example.memgallery.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.datastore.preferences.core.*
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.ui.widget.CaptureBarWidget
import com.example.memgallery.ui.widget.MemoryPulseWidget
import com.example.memgallery.ui.widget.WidgetKeys
import com.example.memgallery.ui.widget.WidgetMemoryItem
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first

@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val memoryDao: MemoryDao,
    private val settingsRepository: com.example.memgallery.data.repository.SettingsRepository,
    private val gson: Gson
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val today = java.time.LocalDate.now().toString()
            val memories = memoryDao.getPulseMemories(today)
            
            // Fetch current theme settings
            val themeColor = settingsRepository.selectedColorFlow.first()
            val themeMode = settingsRepository.appThemeModeFlow.first()
            val amoledMode = settingsRepository.amoledModeEnabledFlow.first()
            val dynamicTheming = settingsRepository.dynamicThemingEnabledFlow.first()

            val widgetItems = memories.map { pulseMemory ->
                val memory = pulseMemory.memory
                
                val formattedDate = try {
                    if (pulseMemory.taskDueDate != null) {
                        val date = LocalDate.parse(pulseMemory.taskDueDate)
                        val dateStr = date.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
                        if (pulseMemory.taskDueTime != null) {
                            "$dateStr • ${pulseMemory.taskDueTime}"
                        } else {
                            dateStr
                        }
                    } else null
                } catch (e: Exception) {
                    null
                }

                WidgetMemoryItem(
                    id = memory.id,
                    title = memory.aiTitle ?: memory.userText?.take(30) ?: "Untitled Memory",
                    summary = memory.aiSummary ?: memory.userText ?: "",
                    imagePath = memory.imageUri ?: memory.bookmarkImageUrl,
                    type = if (memory.audioFilePath != null) "AUDIO" else if (memory.imageUri != null) "Camera" else "MEMORY",
                    dueTime = pulseMemory.taskDueTime,
                    formattedDateTime = formattedDate
                )
            }

            val json = gson.toJson(widgetItems)

            // Fix 1: Define the manager that was missing
            val manager = GlanceAppWidgetManager(context)
            
            // Get IDs for both widgets
            val glanceIdsPulse = manager.getGlanceIds(MemoryPulseWidget::class.java)
            val glanceIdsCaptureBar = manager.getGlanceIds(CaptureBarWidget::class.java)

            // Logic for updating preferences
            val updatePulse: suspend (Preferences) -> Preferences = { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.widgetDataKey] = json
                    this[WidgetKeys.lastUpdateKey] = System.currentTimeMillis().toString()
                    this[WidgetKeys.themeColorKey] = themeColor
                    this[WidgetKeys.themeModeKey] = themeMode
                    this[WidgetKeys.amoledModeKey] = amoledMode
                    this[WidgetKeys.dynamicThemingKey] = dynamicTheming
                }
            }

            // Capture bar only needs theme state — no items.
            val updateCaptureBar: suspend (Preferences) -> Preferences = { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.themeColorKey] = themeColor
                    this[WidgetKeys.themeModeKey] = themeMode
                    this[WidgetKeys.amoledModeKey] = amoledMode
                    this[WidgetKeys.dynamicThemingKey] = dynamicTheming
                }
            }

            glanceIdsPulse.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId, updatePulse)
            }
            glanceIdsCaptureBar.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId, updateCaptureBar)
            }

            MemoryPulseWidget().updateAll(context)
            CaptureBarWidget().updateAll(context)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
