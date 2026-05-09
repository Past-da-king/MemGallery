package com.example.memgallery.ui.components.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.components.MemoryCard

@Composable
fun AddMemoriesToCollectionSheet(
    memories: List<MemoryEntity>,
    onAdd: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.sheet_add_memories_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(
                onClick = { onAdd(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(stringResource(R.string.sheet_add_memories_button, selectedIds.size))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (memories.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sheet_add_memories_empty))
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories) { memory ->
                    val isSelected = selectedIds.contains(memory.id)
                    MemoryCard(
                        memory = memory,
                        isSelected = isSelected,
                        onClick = {
                            selectedIds = if (isSelected) {
                                selectedIds - memory.id
                            } else {
                                selectedIds + memory.id
                            }
                        },
                        onLongClick = {} // No long click action needed here
                    )
                }
            }
        }
    }
}
