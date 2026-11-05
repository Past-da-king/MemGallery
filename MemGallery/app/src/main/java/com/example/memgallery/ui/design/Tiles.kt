package com.example.memgallery.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.ui.theme.memColors

enum class Tone { Primary, Secondary, Tertiary, Success, Sky, Amber }

/** Container and content colours for a tone, from the one token source. */
@Composable
fun Tone.colors(): Pair<Color, Color> {
    val s = MaterialTheme.colorScheme
    val m = MaterialTheme.memColors
    return when (this) {
        Tone.Primary -> s.primaryContainer to s.onPrimaryContainer
        Tone.Secondary -> s.secondaryContainer to s.onSecondaryContainer
        Tone.Tertiary -> s.tertiaryContainer to s.onTertiaryContainer
        Tone.Success -> m.successContainer to m.onSuccessContainer
        Tone.Sky -> m.skyContainer to m.onSkyContainer
        Tone.Amber -> m.amberContainer to m.onAmberContainer
    }
}

private val noteTones = listOf(Tone.Primary, Tone.Tertiary, Tone.Secondary, Tone.Amber)

enum class MemoryKind { Image, Audio, Link, Note }

fun MemoryEntity.kind(): MemoryKind = when {
    bookmarkUrl != null -> MemoryKind.Link
    imageUri != null -> MemoryKind.Image
    audioFilePath != null -> MemoryKind.Audio
    else -> MemoryKind.Note
}

fun MemoryEntity.displayTitle(fallback: String): String =
    aiTitle?.takeIf { it.isNotBlank() } ?: bookmarkTitle?.takeIf { it.isNotBlank() } ?: fallback

fun MemoryEntity.displayBody(): String? =
    aiSummary?.takeIf { it.isNotBlank() } ?: userText?.takeIf { it.isNotBlank() } ?: bookmarkDescription?.takeIf { it.isNotBlank() }

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

/** One memory in the grid: image, note, voice or link tile. Selection shows a check badge and ring. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryTile(
    memory: MemoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    untitledLabel: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val ring = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(ring)
    ) {
        when (memory.kind()) {
            MemoryKind.Image -> ImageTile(memory)
            MemoryKind.Audio -> AudioTile(memory, untitledLabel)
            MemoryKind.Link -> LinkTile(memory, untitledLabel)
            MemoryKind.Note -> NoteTile(memory, untitledLabel)
        }
        if (memory.status == "PENDING" || memory.status == "PROCESSING") {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x8C141028))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Rounded.HourglassEmpty, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ImageTile(memory: MemoryEntity) {
    Image(
        painter = memoryPainter(memory.imageUri),
        contentDescription = memory.aiTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 300.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
private fun TileLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoteTile(memory: MemoryEntity, untitled: String) {
    val (bg, fg) = noteTones[memory.id.mod(noteTones.size)].colors()
    Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
        TileLabel(Icons.Rounded.StickyNote2, "Note", fg)
        Spacer(Modifier.height(8.dp))
        Text(memory.displayTitle(untitled), style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        memory.displayBody()?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AudioTile(memory: MemoryEntity, untitled: String) {
    val (bg, fg) = Tone.Sky.colors()
    Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
        TileLabel(Icons.Rounded.Mic, "Voice", fg)
        Spacer(Modifier.height(12.dp))
        Waveform(seed = memory.id, color = fg, modifier = Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(memory.displayTitle(untitled), style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LinkTile(memory: MemoryEntity, untitled: String) {
    val (bg, fg) = Tone.Success.colors()
    Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
        TileLabel(Icons.Rounded.Link, hostOf(memory.bookmarkUrl.orEmpty()), fg)
        Spacer(Modifier.height(8.dp))
        Text(memory.displayTitle(untitled), style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
        memory.bookmarkDescription?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Decorative voice waveform, stable per memory. */
@Composable
fun Waveform(seed: Int, color: Color, modifier: Modifier = Modifier, bars: Int = 18) {
    Canvas(modifier) {
        val rnd = java.util.Random(seed.toLong() * 31 + 5)
        val w = 3.dp.toPx()
        val gap = (size.width - bars * w) / (bars - 1).coerceAtLeast(1)
        for (i in 0 until bars) {
            val v = ((Math.sin(i / 3.0) * 0.35 + 0.55) * (0.35 + rnd.nextFloat() * 0.65)).toFloat()
            val h = size.height * v
            drawRoundRect(
                color,
                topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f)
            )
        }
    }
}

/** Section title row: emphasized title on the left, an action or count on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        trailing()
    }
}

/** Small caps overline used between grouped lists (EVENTS, TO-DO, AI PROVIDER). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(horizontal = 24.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun GapW(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun FillBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier.fillMaxSize()) { content() }
