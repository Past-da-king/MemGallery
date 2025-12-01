package com.example.memgallery.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.memgallery.data.local.entity.TaskEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    taskToEdit: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate, String?, String, Boolean, String?) -> Unit
) {
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var type by remember { mutableStateOf(taskToEdit?.type ?: "TODO") }
    var date by remember { 
        mutableStateOf(
            taskToEdit?.dueDate?.let { 
                try { LocalDate.parse(it) } catch (e: Exception) { LocalDate.now() }
            } ?: LocalDate.now()
        ) 
    }
    var time by remember { mutableStateOf(taskToEdit?.dueTime ?: "") }
    var isRecurring by remember { mutableStateOf(taskToEdit?.isRecurring ?: false) }
    var recurrenceRule by remember { mutableStateOf(taskToEdit?.recurrenceRule ?: "WEEKLY") }
    
    // Custom in-layout dialog states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val timePickerState = rememberTimePickerState(
        initialHour = time.takeIf { it.contains(":") }?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = time.takeIf { it.contains(":") }?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
    )

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. Type Selector (Compact Chips)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("EVENT", "Event", Icons.Default.Event),
                    Triple("TODO", "Task", Icons.Default.Check),
                    Triple("REMINDER", "Reminder", Icons.Default.AccessTime)
                ).forEach { (key, label, icon) ->
                    FilterChip(
                        selected = type == key,
                        onClick = { type = key },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Title Input (Minimalist)
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { 
                    Text(
                        "What needs to be done?", 
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ) 
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Description Input (Minimalist)
            TextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { 
                    Text(
                        "Add details...", 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ) 
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Action Row (Date, Time, Repeat, Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Date Chip
                SuggestionChip(
                    onClick = { showDatePicker = true },
                    label = { Text(date.format(DateTimeFormatter.ofPattern("MMM d"))) },
                    icon = { 
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = null
                )
                
                // Time Chip
                SuggestionChip(
                    onClick = { showTimePicker = true },
                    label = { Text(time.ifBlank { "Time" }) },
                    icon = { 
                        Icon(
                            Icons.Default.AccessTime, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = if(time.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if(time.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = null
                )

                // Repeat Icon
                IconButton(
                    onClick = { isRecurring = !isRecurring },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if(isRecurring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = "Repeat")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = { onSave(title, description, date, time, type, isRecurring, recurrenceRule) },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text(if (taskToEdit != null) "Update" else "Save")
                }
            }
        }

        // Custom In-Layout Date Picker Overlay
        if (showDatePicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Column {
                    DatePicker(
                        state = datePickerState,
                        modifier = Modifier.wrapContentHeight()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // Custom In-Layout Time Picker Overlay
        if (showTimePicker) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Select Time",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            time = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
