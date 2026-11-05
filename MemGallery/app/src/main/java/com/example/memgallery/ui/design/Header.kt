package com.example.memgallery.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Pulls a child up by [amount] over the previous sibling (the search pill sits on the seam of the header). */
fun Modifier.overlapUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val a = amount.roundToPx()
    layout(p.width, p.height - a) { p.place(0, -a) }
}

/**
 * The "blob header": lavender surface with oversized cookie and clover blobs, rounded bottom corners.
 * Content is laid over the blobs, below the status bar.
 */
@Composable
fun BlobHeader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp))
            .background(scheme.primaryContainer)
    ) {
        // The blobs fill the header but never decide its size
        Box(Modifier.matchParentSize()) {
            // Cookie, top right, tilted
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = (-60).dp)
                    .size(260.dp)
                    .rotate(14f)
                    .clip(MemShape.cookie)
                    .background(scheme.primary.copy(alpha = 0.16f))
            )
            // Clover, bottom left
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-70).dp, y = 82.dp)
                    .size(170.dp)
                    .clip(MemShape.clover)
                    .background(scheme.tertiary.copy(alpha = 0.14f))
            )
        }
        Column(Modifier.appStatusBarsPadding()) { content() }
    }
}

/** Eyebrow, huge emphasized title and the round tonal action buttons on the right. */
@Composable
fun HeaderTitleRow(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = 8.dp, top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Row { actions() }
    }
}

/** Round tonal icon button (48dp target, 40dp disc). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
        )
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** The one cookie-shaped button in the header (settings, add). */
@Composable
fun CookieIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(MemShape.cookie)
                .background(MaterialTheme.colorScheme.primary)
        )
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Back arrow that sits over the header. */
@Composable
fun HeaderBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

enum class StatTone { Quiet, Tertiary, Primary }

/** One of the three stat tiles under the title. */
@Composable
fun StatTile(
    value: String,
    label: String,
    tone: StatTone,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (tone) {
        StatTone.Quiet -> scheme.onPrimaryContainer.copy(alpha = 0.10f)
        StatTone.Tertiary -> scheme.tertiaryContainer
        StatTone.Primary -> scheme.primary
    }
    val fg = when (tone) {
        StatTone.Quiet -> scheme.onPrimaryContainer
        StatTone.Tertiary -> scheme.onTertiaryContainer
        StatTone.Primary -> scheme.onPrimary
    }
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = fg)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
    }
}

@Composable
fun StatTileRow(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tones = listOf(StatTone.Quiet, StatTone.Tertiary, StatTone.Primary)
        items.forEachIndexed { i, (value, label) ->
            StatTile(value, label, tones[i % 3], Modifier.weight(1f))
        }
    }
}

/**
 * Dotted activity heatmap: the last rows x columns days, oldest top-left, newest bottom-right.
 * Real data: a map of day -> memory count.
 */
@Composable
fun ActivityDots(
    data: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    rows: Int = 6
) {
    val color = MaterialTheme.colorScheme.primary
    val cell = 10.dp
    val gap = 3.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(cell * rows + gap * (rows - 1))
    ) {
        val c = cell.toPx()
        val g = gap.toPx()
        val cols = ((size.width + g) / (c + g)).toInt().coerceAtLeast(1)
        val total = cols * rows
        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val index = col * rows + row
                val date = today.minusDays((total - 1 - index).toLong())
                val n = data[date] ?: 0
                val alpha = when {
                    n >= 4 -> 0.95f
                    n >= 2 -> 0.6f
                    n >= 1 -> 0.3f
                    else -> 0.12f
                }
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(col * (c + g), row * (c + g)),
                    size = Size(c, c),
                    cornerRadius = CornerRadius(3.5.dp.toPx())
                )
            }
        }
    }
}

/** Floating white search pill that sits on the seam below the header. */
@Composable
fun SearchPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(6.dp, RoundedCornerShape(28.dp), clip = false),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            Modifier.height(56.dp).padding(start = 18.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(
                placeholder,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
