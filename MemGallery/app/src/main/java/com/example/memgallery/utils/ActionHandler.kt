package com.example.memgallery.utils

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.AlarmClock
import android.widget.Toast
import com.example.memgallery.data.remote.dto.ActionDto
import java.util.Calendar
import java.util.TimeZone

object ActionHandler {

    fun handleAction(context: Context, action: ActionDto) {
        val intent = getActionIntent(action)
        if (intent != null) {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No app found to handle this action", Toast.LENGTH_SHORT).show()
            }
        } else {
             Toast.makeText(context, "Unknown action type", Toast.LENGTH_SHORT).show()
        }
    }

    fun getActionIntent(action: ActionDto): Intent? {
        return when (action.type) {
            "EVENT" -> createCalendarEventIntent(action)
            "TODO", "REMINDER" -> createNoteOrReminderIntent(action)
            else -> null
        }
    }

    private fun createCalendarEventIntent(action: ActionDto): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.description)
            
            if (action.date != null) {
                val startTime = parseDateTime(action.date, action.time)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                // Default to 1 hour duration if no end time logic (simplified)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTime + 3600000)
            }
        }
    }

    private fun createNoteOrReminderIntent(action: ActionDto): Intent {
        // Try to create a note/reminder. 
        // ACTION_SEND is a generic way to share text to note apps.
        // For specific reminders, AlarmClock.ACTION_SET_ALARM could be used if it's a time-based reminder,
        // but "Note" is safer for general TODOs.
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${action.description} ${action.date ?: ""} ${action.time ?: ""}")
            putExtra(Intent.EXTRA_TITLE, "New Action")
        }
        
        return Intent.createChooser(intent, "Save Action to...")
    }

    private fun parseDateTime(dateStr: String, timeStr: String?): Long {
        // Simple parser for YYYY-MM-DD and HH:MM
        // In a real app, use java.time or SimpleDateFormat with error handling
        try {
            if (dateStr.isBlank()) return System.currentTimeMillis()

            val parts = dateStr.split("-")
            if (parts.size != 3) return System.currentTimeMillis()

            val year = parts[0].toIntOrNull() ?: return System.currentTimeMillis()
            val month = (parts[1].toIntOrNull() ?: 1) - 1 // Calendar months are 0-indexed
            val day = parts[2].toIntOrNull() ?: return System.currentTimeMillis()

            val calendar = Calendar.getInstance()
            calendar.set(year, month, day)

            if (!timeStr.isNullOrBlank()) {
                val timeParts = timeStr.split(":")
                if (timeParts.size == 2) {
                    val hour = timeParts[0].toIntOrNull() ?: 9
                    val minute = timeParts[1].toIntOrNull() ?: 0
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                } else {
                     calendar.set(Calendar.HOUR_OF_DAY, 9)
                     calendar.set(Calendar.MINUTE, 0)
                }
            } else {
                // Default to start of day or current time? 
                // Let's leave it as 00:00 for all-day events if no time
                calendar.set(Calendar.HOUR_OF_DAY, 9) // Default to 9 AM
                calendar.set(Calendar.MINUTE, 0)
            }
            return calendar.timeInMillis
        } catch (e: Exception) {
            e.printStackTrace()
            return System.currentTimeMillis()
        }
    }
}
