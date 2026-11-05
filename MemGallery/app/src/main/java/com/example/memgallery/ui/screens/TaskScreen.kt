package com.example.memgallery.ui.screens

import com.example.memgallery.ui.design.appStatusBarsPadding
import com.example.memgallery.ui.design.appNavigationBarsPadding
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.memgallery.R
import com.example.memgallery.data.local.dao.TaskSourceMemory
import com.example.memgallery.data.local.entity.TaskEntity
import com.example.memgallery.ui.components.FullMonthCalendar
import com.example.memgallery.ui.components.sheets.AddTaskSheet
import com.example.memgallery.ui.components.sheets.UnapprovedTasksSheet
import com.example.memgallery.ui.design.BlobHeader
import com.example.memgallery.ui.design.CookieIconButton
import com.example.memgallery.ui.design.EmptyState
import com.example.memgallery.ui.design.HeaderTitleRow
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.Overline
import com.example.memgallery.ui.design.RoundIconButton
import com.example.memgallery.ui.design.StatTileRow
import com.example.memgallery.ui.design.Tone
import com.example.memgallery.ui.design.colors
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.design.overlapUp
import com.example.memgallery.ui.viewmodels.TaskViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Tasks page draws. Real data in the app, sample data in screenshot tests. */
data class TaskUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val selectedDate: LocalDate? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val unapprovedCount: Int = 0,
    val sources: Map<Int, TaskSourceMemory> = emptyMap(),
)

class TaskActions(
    val onSelectDate: (LocalDate?) -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onOpenTask: (TaskEntity) -> Unit = {},
    val onToggle: (TaskEntity) -> Unit = {},
    val onCapture: () -> Unit = {},
)

@Composable
fun TaskScreen(
    viewModel: TaskViewModel = hiltViewModel(),
    openAddSheet: Boolean = false,
    addTaskTrigger: Int = 0,
    bottomPadding: Dp = 112.dp,
    onCapture: () -> Unit = {},
    onNavigateToMemory: (Int) -> Unit = {}
) {
    val tasks by viewModel.tasksForDisplay.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val unapprovedTasks by viewModel.unapprovedTasks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val sources by viewModel.taskSources.collectAsState()

    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var isAddSheetOpen by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showUnapprovedSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openAddSheet) { if (openAddSheet) isAddSheetOpen = true }
    LaunchedEffect(addTaskTrigger) { if (addTaskTrigger > 0) { selectedTask = null; isAddSheetOpen = true } }
    LaunchedEffect(selectedTask) { if (selectedTask != null && !isAddSheetOpen) showDetailSheet = true }

    val state = TaskUiState(tasks, selectedDate, LocalDateTime.now(), unapprovedTasks.size, sources)
    val actions = TaskActions(
        onSelectDate = viewModel::selectDate,
        onOpenCalendar = { showCalendar = true },
        onAdd = { selectedTask = null; isAddSheetOpen = true },
        onReview = { showUnapprovedSheet = true },
        onOpenTask = { selectedTask = it },
        onToggle = viewModel::toggleTaskCompletion,
        onCapture = onCapture,
    )

    TasksContent(state, actions, bottomPadding)

    if (showCalendar) {
        MemSheet(onDismiss = { showCalendar = false }) {
            FullMonthCalendar(
                selectedDate = selectedDate ?: LocalDate.now(),
                activeDates = activeTasks.mapNotNull { t ->
                    t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                }.distinct(),
                onDateSelected = {
                    viewModel.selectDate(it)
                    showCalendar = false
                },
                onDismiss = { showCalendar = false }
            )
        }
    }

    if (showDetailSheet && selectedTask != null) {
        MemSheet(onDismiss = { showDetailSheet = false; selectedTask = null }) {
            TaskDetailContent(
                task = selectedTask!!,
                onEdit = {
                    showDetailSheet = false
                    isAddSheetOpen = true
                },
                onViewMemory = { selectedTask?.memoryId?.let { onNavigateToMemory(it) } }
            )
        }
    }

    if (isAddSheetOpen) {
        MemSheet(onDismiss = { isAddSheetOpen = false; selectedTask = null }) {
            AddTaskSheet(
                taskToEdit = selectedTask,
                onDismiss = { isAddSheetOpen = false; selectedTask = null },
                onSave = { title, description, date, time, type, isRecurring, rule ->
                    val editing = selectedTask
                    if (editing != null) {
                        viewModel.updateTask(
                            editing.copy(
                                title = title, description = description, dueDate = date.toString(), dueTime = time,
                                type = type, isRecurring = isRecurring, recurrenceRule = rule
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

    if (showUnapprovedSheet && unapprovedTasks.isNotEmpty()) {
        MemSheet(onDismiss = { showUnapprovedSheet = false }) {
            UnapprovedTasksSheet(
                tasks = unapprovedTasks,
                onApprove = { toApprove ->
                    viewModel.approveTasks(toApprove)
                    if (unapprovedTasks.size == toApprove.size) showUnapprovedSheet = false
                },
                onDelete = { toDelete ->
                    viewModel.deleteTasks(toDelete)
                    if (unapprovedTasks.size == toDelete.size) showUnapprovedSheet = false
                },
                onDismiss = { showUnapprovedSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TasksContent(
    state: TaskUiState,
    actions: TaskActions,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val today = state.now.toLocalDate()
    val events = state.tasks.filter { it.type == "EVENT" }
    val todos = state.tasks.filter { it.type == "TODO" || it.type == "REMINDER" }
    val done = todos.count { it.isCompleted }
    val open = todos.size - done
    val empty = events.isEmpty() && todos.isEmpty()

    val title = when (state.selectedDate) {
        today -> stringResource(R.string.tasks_header_my_day)
        today.plusDays(1) -> stringResource(R.string.tasks_header_tomorrow)
        null -> stringResource(R.string.tasks_header_upcoming)
        else -> state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()))
    }
    val eyebrow = state.selectedDate?.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
        ?: stringResource(R.string.tasks_all_upcoming_eyebrow)

    LazyColumn(
        modifier.fillMaxSize().background(scheme.surface),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item {
            BlobHeader {
                HeaderTitleRow(eyebrow = eyebrow, title = title) {
                    RoundIconButton(Icons.Rounded.CalendarMonth, stringResource(R.string.tasks_cd_open_calendar), actions.onOpenCalendar)
                    CookieIconButton(Icons.Rounded.Add, stringResource(R.string.tasks_cd_add_task), actions.onAdd)
                }
                Spacer(Modifier.height(12.dp))
                StatTileRow(
                    listOf(
                        events.size.toString() to stringResource(R.string.tasks_stat_events),
                        open.toString() to stringResource(R.string.tasks_stat_todo),
                        done.toString() to stringResource(R.string.tasks_stat_done),
                    )
                )
                Spacer(Modifier.height(if (empty) 24.dp else 28.dp))
            }
            if (!empty || state.selectedDate != null) {
                DaySegments(state.selectedDate, today, actions.onSelectDate, Modifier.overlapUp(20.dp))
                Spacer(Modifier.height(8.dp))
            }
        }

        if (state.unapprovedCount > 0) {
            item {
                ReviewBanner(state.unapprovedCount, actions.onReview)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (empty) {
            item {
                EmptyState(
                    icon = Icons.Rounded.TaskAlt,
                    title = stringResource(R.string.tasks_empty_title),
                    body = stringResource(R.string.tasks_empty_body),
                    modifier = Modifier.padding(top = 24.dp),
                    primaryLabel = stringResource(R.string.tasks_capture_memory),
                    primaryIcon = Icons.Rounded.PhotoCamera,
                    onPrimary = actions.onCapture,
                    secondaryLabel = stringResource(R.string.tasks_add_yourself),
                    onSecondary = actions.onAdd
                )
            }
        } else {
            if (events.isNotEmpty()) {
                item { Overline(stringResource(R.string.tasks_section_events)); Spacer(Modifier.height(8.dp)) }
                items(events.size) { i ->
                    EventRow(events[i], i, state, actions.onOpenTask)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
            if (todos.isNotEmpty()) {
                item { Overline(stringResource(R.string.tasks_section_todos)); Spacer(Modifier.height(8.dp)) }
                items(todos.size) { i ->
                    TodoRow(
                        task = todos[i], state = state,
                        shape = RoundedCornerShape(
                            topStart = if (i == 0) 28.dp else 6.dp, topEnd = if (i == 0) 28.dp else 6.dp,
                            bottomStart = if (i == todos.lastIndex) 28.dp else 6.dp, bottomEnd = if (i == todos.lastIndex) 28.dp else 6.dp
                        ),
                        onClick = { actions.onOpenTask(todos[i]) },
                        onToggle = { actions.onToggle(todos[i]) },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/** My Day, Tomorrow, Upcoming: a connected single-select button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DaySegments(selected: LocalDate?, today: LocalDate, onSelect: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        today to stringResource(R.string.tasks_header_my_day),
        today.plusDays(1) to stringResource(R.string.tasks_header_tomorrow),
        null to stringResource(R.string.tasks_header_upcoming),
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { i, (date, label) ->
            val checked = selected == date
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(date) },
                modifier = Modifier.weight(1f),
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    checkedContainerColor = scheme.secondary,
                    checkedContentColor = scheme.onSecondary,
                )
            ) {
                if (checked) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onReview: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.tertiaryContainer)
            .clickable(onClick = onReview)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(MemShape.softBurst).background(scheme.onTertiaryContainer.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.onTertiaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (count == 1) stringResource(R.string.tasks_review_title_one) else stringResource(R.string.tasks_review_title, count),
                style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer
            )
            Text(stringResource(R.string.tasks_review_sub), style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer.copy(alpha = 0.8f))
        }
        Text(stringResource(R.string.tasks_review_action), style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
    }
}

private fun durationUntil(task: TaskEntity, now: LocalDateTime): String? {
    val date = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = task.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val d = Duration.between(now, LocalDateTime.of(date, time))
    if (d.isNegative || d.isZero || d.toDays() > 0) return null
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h > 0 && m > 0) "${h}h ${m}m" else if (h > 0) "${h}h" else "${m}m"
}

@Composable
private fun EventRow(event: TaskEntity, index: Int, state: TaskUiState, onClick: (TaskEntity) -> Unit) {
    val (bg, fg) = (if (index % 2 == 0) Tone.Primary else Tone.Success).colors()
    val source = event.memoryId?.let { state.sources[it] }
    val until = durationUntil(event, state.now)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
            Text(event.dueTime.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (until != null) Text(until, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(bg)
                .clickable { onClick(event) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (event.description.isNotBlank() && event.description != event.title) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, tint = fg, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(event.description, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val image = source?.imageUri
            if (image != null) {
                Spacer(Modifier.width(12.dp))
                Image(memoryPainter(image), null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            }
        }
    }
}

@Composable
private fun TodoRow(
    task: TaskEntity,
    state: TaskUiState,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val checked = task.isCompleted
    val today = state.now.toLocalDate()
    val source = task.memoryId?.let { state.sources[it]?.aiTitle }
    val trailing = when {
        checked -> stringResource(R.string.tasks_done_at, java.time.Instant.ofEpochMilli(task.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
        task.isRecurring -> stringResource(R.string.tasks_repeats_x, task.recurrenceRule ?: stringResource(R.string.tasks_repeats_custom))
        task.dueDate == today.toString() -> stringResource(R.string.tasks_due_today)
        task.dueDate != null -> stringResource(R.string.tasks_due_on, task.dueDate)
        else -> null
    }
    val subtitle = listOfNotNull(source?.let { stringResource(R.string.tasks_from_memory, it) }, trailing).joinToString(" · ")
    val boxShape by animateFloatAsState(if (checked) 50f else 28f, label = "check-morph")
    val fill by animateColorAsState(if (checked) scheme.primary else androidx.compose.ui.graphics.Color.Transparent, label = "check-fill")
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(boxShape.toInt()))
                .background(fill)
                .border(2.dp, if (checked) scheme.primary else scheme.outline, RoundedCornerShape(boxShape.toInt()))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(if (checked) 0.7f else 1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = scheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Content of the task detail sheet. */
@Composable
fun TaskDetailContent(task: TaskEntity, onEdit: () -> Unit, onViewMemory: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (task.type == "EVENT") Icons.Filled.Event else Icons.Filled.Check, null, tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (task.type == "EVENT") stringResource(R.string.tasks_detail_event) else stringResource(R.string.tasks_detail_task),
                    style = MaterialTheme.typography.labelLarge, color = scheme.primary
                )
            }
            RoundIconButtonOnSurface(onEdit)
        }
        Spacer(Modifier.height(12.dp))
        Text(task.title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
        if (task.memoryId != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onViewMemory, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tasks_view_source_memory))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (task.dueTime != null || task.dueDate != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccessTime, null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(listOfNotNull(task.dueDate, task.dueTime).joinToString(" ").trim(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (task.isRecurring) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tasks_repeats_label, task.recurrenceRule ?: stringResource(R.string.tasks_repeats_custom)),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(task.description, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

@Composable
private fun RoundIconButtonOnSurface(onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onEdit),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.secondaryContainer))
        Icon(Icons.Filled.Edit, stringResource(R.string.common_edit), tint = scheme.onSecondaryContainer)
    }
}
