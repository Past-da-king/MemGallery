package com.example.memgallery.ui.components.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.memgallery.R
import com.example.memgallery.data.local.entity.CollectionEntity
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.design.SheetTitle
import com.example.memgallery.ui.design.memoryPainter
import com.example.memgallery.ui.viewmodels.CollectionInfo

/** "Add to Collection": pick one collection, then confirm. Content of a MemSheet. */
@Composable
fun SelectCollectionSheet(
    collections: List<CollectionEntity>,
    onSelect: (Int) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    selectedCount: Int = 0,
    collectionInfo: Map<Int, CollectionInfo> = emptyMap(),
    initialSelection: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf(initialSelection) }
    val pickedName = collections.firstOrNull { it.id == picked }?.name

    SheetTitle(
        stringResource(R.string.collections_add_to_title),
        if (selectedCount > 0) stringResource(R.string.collections_add_to_supporting, selectedCount) else null
    )
    LazyColumn(
        Modifier.heightIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(collections, key = { it.id }) { c ->
            val selected = c.id == picked
            val info = collectionInfo[c.id] ?: CollectionInfo()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh)
                    .clickable { picked = c.id }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cover = info.covers.firstOrNull()
                if (cover != null) {
                    Image(
                        memoryPainter(cover), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(MemShape.softBurst)
                    )
                } else {
                    Box(Modifier.size(56.dp).clip(MemShape.softBurst).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.collections_n_memories, info.count, info.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(scheme.primary) else Modifier.border(2.dp, scheme.outline, CircleShape)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Filled.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        item {
            val dash = scheme.outline
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = dash,
                            cornerRadius = CornerRadius(28.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        )
                    }
                    .clickable(onClick = onCreateNew)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = scheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.collections_create_new), style = MaterialTheme.typography.titleMedium, color = scheme.tertiary)
            }
        }
    }
    Button(
        onClick = { picked?.let(onSelect) },
        enabled = picked != null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).height(56.dp)
    ) {
        Text(
            if (pickedName != null && selectedCount > 0) stringResource(R.string.collections_add_n_to, selectedCount, pickedName)
            else stringResource(R.string.collections_add_to_title),
            style = MaterialTheme.typography.titleMedium
        )
    }
}
