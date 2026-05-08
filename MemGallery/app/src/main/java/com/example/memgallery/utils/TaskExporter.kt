package com.example.memgallery.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.memgallery.data.local.entity.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for exporting tasks to external task management apps.
 */
@Singleton
class TaskExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Exports a task to the configured external task manager.
     * @param task The task to export.
     * @param targetManager The target app ("TICKTICK", "GOOGLE_TASKS", "TODOIST", "SHARE", "NONE").
     * @return true if the intent was launched, false otherwise.
     */
    fun exportTask(task: TaskEntity, targetManager: String): Boolean {
        if (targetManager == "NONE") return false

        val intent = when (targetManager) {
            "TICKTICK" -> createTickTickIntent(task)
            "GOOGLE_TASKS" -> createGoogleTasksIntent(task)
            "TODOIST" -> createTodoistIntent(task)
            "SHARE" -> createShareIntent(task)
            else -> return false
        }

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // App not installed or intent failed, fallback to generic share
            if (targetManager != "SHARE") {
                return exportTask(task, "SHARE")
            }
            false
        }
    }

    private fun createTickTickIntent(task: TaskEntity): Intent {
        // TickTick supports a specific intent action
        // Alternatively, use their deep link: ticktick://x.any.do/addTask
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.ticktick.task")
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, buildTaskText(task))
        }
        return intent
    }

    private fun createGoogleTasksIntent(task: TaskEntity): Intent {
        // Google Tasks doesn't have a public intent API, so we use a generic share
        // targeted at the package if installed.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.google.android.apps.tasks")
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, buildTaskText(task))
        }
        return intent
    }

    private fun createTodoistIntent(task: TaskEntity): Intent {
        // Todoist supports a deep link for adding tasks
        // Format: todoist://addtask?content=...&date=...
        val content = Uri.encode(task.title)
        val date = task.dueDate?.let { Uri.encode(it) } ?: ""
        val uri = Uri.parse("todoist://addtask?content=$content&date=$date")
        
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.todoist")
        }
    }

    private fun createShareIntent(task: TaskEntity): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, buildTaskText(task))
        }
    }

    private fun buildTaskText(task: TaskEntity): String {
        val sb = StringBuilder()
        sb.append(task.title)
        if (task.description.isNotBlank() && task.description != task.title) {
            sb.append("\n\n")
            sb.append(task.description)
        }
        if (!task.dueDate.isNullOrBlank()) {
            sb.append("\n\nDue: ")
            sb.append(task.dueDate)
            if (!task.dueTime.isNullOrBlank()) {
                sb.append(" at ")
                sb.append(task.dueTime)
            }
        }
        return sb.toString()
    }
}
