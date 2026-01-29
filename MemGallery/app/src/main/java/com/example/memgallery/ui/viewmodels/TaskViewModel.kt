package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.utils.TaskExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.example.memgallery.ui.widget.WidgetRefreshManager

@HiltViewModel
class TaskViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val settingsRepository: SettingsRepository,
    private val taskExporter: TaskExporter
) : ViewModel() {

    private val _selectedDate = MutableStateFlow<LocalDate?>(null) // Default to Upcoming (All)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    // External task manager setting
    private val externalTaskManager: StateFlow<String> = settingsRepository.externalTaskManagerFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = "NONE")

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasksForDisplay: StateFlow<List<TaskEntity>> = _selectedDate.flatMapLatest { date ->
        if (date != null) {
            // If date selected, show tasks for that date
            taskDao.getTasksByDate(date.toString())
        } else {
            // If no date selected (or specific "Upcoming" mode), show all upcoming
            // For now, we default to Today, but this supports future "All Upcoming" view
            taskDao.getUpcomingTasksFromDate(LocalDate.now().toString())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Keep track of all active tasks for the calendar dots/indicators if needed later
    val activeTasks: StateFlow<List<TaskEntity>> = taskDao.getActiveTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unapprovedTasks: StateFlow<List<TaskEntity>> = taskDao.getUnapprovedTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
            WidgetRefreshManager.refreshWidget(context)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
            WidgetRefreshManager.refreshWidget(context)
        }
    }

    fun deleteTasks(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            taskDao.deleteTasksByIds(tasks.map { it.id })
            WidgetRefreshManager.refreshWidget(context)
        }
    }

    fun approveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task.copy(isApproved = true))
            // Export to external task manager if configured
            val manager = externalTaskManager.value
            if (manager != "NONE") {
                taskExporter.exportTask(task, manager)
            }
            WidgetRefreshManager.refreshWidget(context)
        }
    }

    fun approveTasks(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            taskDao.approveTasks(tasks.map { it.id })
            // Export each task to external manager if configured
            val manager = externalTaskManager.value
            if (manager != "NONE") {
                // Only export the first task to avoid opening multiple apps at once
                // User can manually approve remaining tasks one by one if needed
                tasks.firstOrNull()?.let { taskExporter.exportTask(it, manager) }
            }
            WidgetRefreshManager.refreshWidget(context)
        }
    }


    fun addTask(
        title: String,
        description: String,
        date: LocalDate,
        time: String?,
        type: String,
        isRecurring: Boolean,
        recurrenceRule: String?
    ) {
        viewModelScope.launch {
            val newTask = TaskEntity(
                memoryId = null, // Manual entry
                title = title,
                description = description,
                dueDate = date.toString(),
                dueTime = time,
                type = type,
                isRecurring = isRecurring,
                recurrenceRule = recurrenceRule,
                status = "PENDING",
                isApproved = true // Manual tasks are always approved
            )
            taskDao.insertTask(newTask)
            WidgetRefreshManager.refreshWidget(context)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task)
            WidgetRefreshManager.refreshWidget(context)
        }
    }
}
