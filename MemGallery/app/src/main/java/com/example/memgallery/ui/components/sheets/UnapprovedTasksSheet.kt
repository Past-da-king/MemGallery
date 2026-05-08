package com.example.memgallery.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memgallery.data.local.entity.TaskEntity

@Composable
fun UnapprovedTasksSheet(
    tasks: List<TaskEntity>,
    onApprove: (List<TaskEntity>) -> Unit,
    onDelete: (List<TaskEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTasks by remember { mutableStateOf(emptySet<Int>()) }
    val allSelected = selectedTasks.size == tasks.size && tasks.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Review New Tasks",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI has detected ${tasks.size} new tasks. Review and approve them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Bulk Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (allSelected) {
                        selectedTasks = emptySet()
                    } else {
                        selectedTasks = tasks.map { it.id }.toSet()
                    }
                }
            ) {
                Text(if (allSelected) "Deselect All" else "Select All")
            }
            
            Row {
                IconButton(
                    onClick = { 
                        val toDelete = tasks.filter { selectedTasks.contains(it.id) }
                        if (toDelete.isNotEmpty()) onDelete(toDelete)
                    },
                    enabled = selectedTasks.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(
                    onClick = {
                        val toApprove = tasks.filter { selectedTasks.contains(it.id) }
                        if (toApprove.isNotEmpty()) onApprove(toApprove)
                    },
                    enabled = selectedTasks.isNotEmpty()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Approve Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(tasks) { task ->
                val isSelected = selectedTasks.contains(task.id)
                UnapprovedTaskItem(
                    task = task,
                    isSelected = isSelected,
                    onToggleSelect = {
                        selectedTasks = if (isSelected) {
                            selectedTasks - task.id
                        } else {
                            selectedTasks + task.id
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Footer Actions (Approve All / Delete All)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { onDelete(tasks) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reject All")
            }
            Button(
                onClick = { onApprove(tasks) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Approve All")
            }
        }
    }
}

@Composable
fun UnapprovedTaskItem(
    task: TaskEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onToggleSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
