package com.example.memgallery.ui.components.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QuickBall(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (isExpanded) {
            QuickBallMenu(
                onNavigate = onNavigate,
                onClose = { onExpandChange(false) }
            )
        }

        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null 
                ) { 
                    onExpandChange(!isExpanded)
                },
            color = Color.Black.copy(alpha = 0.7f),
            shape = CircleShape,
            tonalElevation = 6.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Quick Ball",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
fun QuickBallMenu(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    val items = listOf(
        QuickBallItemSpec(Icons.Rounded.Notes, "QUICK_TEXT", 180f),
        QuickBallItemSpec(Icons.Rounded.CameraAlt, "CAMERA", 225f),
        QuickBallItemSpec(Icons.Rounded.Mic, "QUICK_AUDIO", 270f),
        QuickBallItemSpec(Icons.Rounded.Link, "ADD_URL", 315f)
    )

    Box(contentAlignment = Alignment.Center) {
        items.forEachIndexed { index, item ->
            StaggeredMenuItem(
                index = index,
                totalItems = items.size,
                item = item,
                onNavigate = {
                    onNavigate(item.action)
                    onClose()
                }
            )
        }
    }
}

data class QuickBallItemSpec(
    val icon: ImageVector,
    val action: String,
    val angle: Float
)

@Composable
fun StaggeredMenuItem(
    index: Int,
    totalItems: Int,
    item: QuickBallItemSpec,
    onNavigate: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 30L) 
        visible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "alpha"
    )

    if (visible || scale > 0f) {
        // Use LocalDensity to convert dp radius to pixels
        // This ensures the menu size is consistent across devices
        val radiusDp = 120.dp
        val radiusPx = with(LocalDensity.current) { radiusDp.toPx() }

        MenuItem(
            icon = item.icon,
            angle = item.angle,
            scale = scale,
            alpha = alpha,
            radius = radiusPx, // Pass pixel value
            onClick = onNavigate
        )
    }
}

@Composable
fun MenuItem(
    icon: ImageVector,
    angle: Float,
    scale: Float,
    alpha: Float,
    radius: Float,
    onClick: () -> Unit
) {
    val rad = Math.toRadians(angle.toDouble())
    val x = (radius * cos(rad)).toFloat()
    val y = (radius * sin(rad)).toFloat()

    Surface(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .scale(scale)
            .size(50.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        shape = CircleShape,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
