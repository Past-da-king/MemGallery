package com.example.memgallery.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.memgallery.data.local.dao.MemoryDao
import com.example.memgallery.ui.widget.MemoryPulseWidget
import com.example.memgallery.ui.widget.WidgetKeys
import com.example.memgallery.ui.widget.WidgetMemoryItem
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.glance.appwidget.updateAll

@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryDao: MemoryDao,
    private val gson: Gson
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().toString()
            val memories = memoryDao.getPulseMemories(today)

            val widgetItems = memories.map { pulseMemory ->
                val memory = pulseMemory.memory
                
                // Format Date/Time: "Fri, 12 Oct • 14:00"
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

            // Update state for all instances of MemoryPulseWidget
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(MemoryPulseWidget::class.java)
            
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetKeys.widgetDataKey] = json
                        this[WidgetKeys.lastUpdateKey] = System.currentTimeMillis().toString()
                    }
                }
            }
            
            // Trigger UI refresh
            MemoryPulseWidget().updateAll(context)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
