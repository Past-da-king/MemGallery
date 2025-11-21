package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.local.dao.TaskDao
import com.example.memgallery.data.local.entity.TaskEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskDao: TaskDao
) : ViewModel() {

    private val _selectedDate = MutableStateFlow<LocalDate?>(null) // Default to Upcoming (All)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

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

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
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
                status = "PENDING"
            )
            taskDao.insertTask(newTask)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task)
        }
    }
}
