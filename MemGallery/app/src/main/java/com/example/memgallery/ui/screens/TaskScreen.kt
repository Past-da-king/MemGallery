package com.example.memgallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.ui.components.sheets.AddTaskSheet
import com.example.memgallery.ui.components.FullMonthCalendar
import com.example.memgallery.ui.viewmodels.TaskViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

import com.example.memgallery.ui.components.sheets.UnapprovedTasksSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel = hiltViewModel(),
    openAddSheet: Boolean = false,
    onNavigateToMemory: (Int) -> Unit = {}
) {
    val tasks by viewModel.tasksForDisplay.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val unapprovedTasks by viewModel.unapprovedTasks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    
    // Filter by type
    val events = tasks.filter { it.type == "EVENT" }
    val todos = tasks.filter { it.type == "TODO" || it.type == "REMINDER" }

    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var isAddSheetOpen by remember { mutableStateOf(false) }
    var isCalendarExpanded by remember { mutableStateOf(false) }
    
    // Unapproved Tasks Sheet State
    var showUnapprovedSheet by remember { mutableStateOf(false) }
    val unapprovedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(unapprovedTasks) {
        if (unapprovedTasks.isNotEmpty()) {
            showUnapprovedSheet = true
        }
    }

    LaunchedEffect(openAddSheet) {
        if (openAddSheet) {
            isAddSheetOpen = true
        }
    }
    
    // Expansion states for lists
    var isEventsExpanded by remember { mutableStateOf(false) }
    var isTodosExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetailSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTask) {
        if (selectedTask != null) {
            showDetailSheet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Premium Header with Calendar Dropdown
            TaskHeader(
                selectedDate = selectedDate,
                isCalendarExpanded = isCalendarExpanded,
                onToggleCalendar = { isCalendarExpanded = !isCalendarExpanded }
            )

            // Expandable Month Calendar
            AnimatedVisibility(
                visible = isCalendarExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FullMonthCalendar(
                    selectedDate = selectedDate ?: LocalDate.now(),
                    activeDates = activeTasks.mapNotNull { task ->
                        task.dueDate?.let { dateString ->
                            try { LocalDate.parse(dateString) } catch (e: Exception) { null }
                        }
                    }.distinct(),
                    onDateSelected = { 
                        viewModel.selectDate(it)
                        isCalendarExpanded = false
                    },
                    onDismiss = { isCalendarExpanded = false }
                )
            }

            // Calendar Strip (Horizontal)
            if (!isCalendarExpanded) {
                CalendarStrip(
                    selectedDate = selectedDate ?: LocalDate.now(),
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Events Section
                if (events.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Events", 
                            icon = Icons.Default.Event,
                            isExpanded = isEventsExpanded,
                            canExpand = events.size > 3,
                            onToggleExpand = { isEventsExpanded = !isEventsExpanded }
                        )
                    }
                    val visibleEvents = if (isEventsExpanded) events else events.take(3)
                    items(visibleEvents) { event ->
                        EventCard(
                            event = event,
                            onClick = { selectedTask = event }
                        )
                    }
                }

                // Tasks Section
                if (todos.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "To-Do", 
                            icon = Icons.Default.Check,
                            isExpanded = isTodosExpanded,
                            canExpand = todos.size > 3,
                            onToggleExpand = { isTodosExpanded = !isTodosExpanded }
                        )
                    }
                    val visibleTodos = if (isTodosExpanded) todos else todos.take(3)
                    items(visibleTodos) { task ->
                        TodoCard(
                            task = task,
                            onClick = { selectedTask = task },
                            onToggle = { viewModel.toggleTaskCompletion(task) }
                        )
                    }
                }
                
                if (events.isEmpty() && todos.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
            }
        }

        // FAB for Adding
        FloatingActionButton(
            onClick = { isAddSheetOpen = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Task")
        }

        // Detail Bottom Sheet
        if (showDetailSheet && selectedTask != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showDetailSheet = false 
                    selectedTask = null
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                TaskDetailContent(
                    task = selectedTask!!,
                    onEdit = { 
                        showDetailSheet = false
                        // Slight delay to allow sheet to close
                        isAddSheetOpen = true 
                    },
                    onViewMemory = {
                        selectedTask?.memoryId?.let { onNavigateToMemory(it) }
                    }
                )
            }
        }

        // Add/Edit Bottom Sheet
        if (isAddSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { 
                    isAddSheetOpen = false 
                    selectedTask = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                AddTaskSheet(
                    taskToEdit = selectedTask,
                    onDismiss = { 
                        isAddSheetOpen = false 
                        selectedTask = null
                    },
                    onSave = { title, description, date, time, type, isRecurring, rule ->
                        if (selectedTask != null) {
                            viewModel.updateTask(
                                selectedTask!!.copy(
                                    title = title,
                                    description = description,
                                    dueDate = date.toString(),
                                    dueTime = time,
                                    type = type,
                                    isRecurring = isRecurring,
                                    recurrenceRule = rule
                                )
                            )
                        } else {
                            viewModel.addTask(title, description, date, time, type, isRecurring, rule)
                        }
                        isAddSheetOpen = false
                        selectedTask = null
                    }
                )
            }
        }
        
        // Unapproved Tasks Sheet
        if (showUnapprovedSheet && unapprovedTasks.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = { showUnapprovedSheet = false },
                sheetState = unapprovedSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                UnapprovedTasksSheet(
                    tasks = unapprovedTasks,
                    onApprove = { tasksToApprove ->
                        viewModel.approveTasks(tasksToApprove)
                        if (unapprovedTasks.size == tasksToApprove.size) {
                            showUnapprovedSheet = false
                        }
                    },
                    onDelete = { tasksToDelete ->
                        viewModel.deleteTasks(tasksToDelete)
                        if (unapprovedTasks.size == tasksToDelete.size) {
                            showUnapprovedSheet = false
                        }
                    },
                    onDismiss = { showUnapprovedSheet = false }
                )
            }
        }
    }
}

@Composable
fun TaskHeader(
    selectedDate: LocalDate?,
    isCalendarExpanded: Boolean,
    onToggleCalendar: () -> Unit
) {
    val title = when (selectedDate) {
        LocalDate.now() -> "My Day"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        null -> "Upcoming"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM d"))
    }

    val subtitle = selectedDate?.format(DateTimeFormatter.ofPattern("EEEE, yyyy")) ?: "All upcoming items"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleCalendar() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary // Dynamic Theme Color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (isCalendarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = "Toggle Calendar",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CalendarStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val days = (0..6).map { today.plusDays(it.toLong()) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(days) { date ->
            val isSelected = date == selectedDate
            val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String, 
    icon: ImageVector,
    isExpanded: Boolean,
    canExpand: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canExpand) { onToggleExpand() }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (canExpand) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isExpanded) "Show Less" else "See All",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun EventCard(event: TaskEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        // Time Column
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .width(60.dp)
                .padding(top = 8.dp)
        ) {
            Text(
                text = event.dueTime ?: "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Event Content
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (event.description.isNotBlank() && event.description != event.title) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun TodoCard(task: TaskEntity, onClick: () -> Unit, onToggle: () -> Unit) {
    val isCompleted = task.isCompleted
    val alpha by animateFloatAsState(targetValue = if (isCompleted) 0.5f else 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .clickable { onClick() } // Open details on card click
            .padding(16.dp)
    ) {
        // Checkbox Area
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(2.dp, if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { onToggle() } // Toggle only on checkbox click
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                color = MaterialTheme.colorScheme.onSurface
            )
            task.dueTime?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun TaskDetailContent(task: TaskEntity, onEdit: () -> Unit, onViewMemory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (task.type == "EVENT") Icons.Default.Event else Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (task.type == "EVENT") "Event Details" else "Task Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        if (task.memoryId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onViewMemory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Source Memory")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (task.dueTime != null || task.dueDate != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = listOfNotNull(task.dueDate, task.dueTime).joinToString(" ").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (task.isRecurring) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Repeats: ${task.recurrenceRule ?: "Custom"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = task.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Capture a memory to generate tasks!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
