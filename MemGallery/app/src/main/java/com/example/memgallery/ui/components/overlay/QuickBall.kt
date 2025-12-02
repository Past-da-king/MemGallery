package com.example.memgallery.ui.components.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun QuickBall(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Expanded Menu Items
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Radial Menu Items
                QuickBallMenuItem(
                    icon = Icons.Default.Notes,
                    angle = 180f,
                    onClick = { onNavigate("QUICK_TEXT"); onExpandChange(false) }
                )
                QuickBallMenuItem(
                    icon = Icons.Default.CameraAlt,
                    angle = 225f,
                    onClick = { onNavigate("CAMERA"); onExpandChange(false) }
                )
                QuickBallMenuItem(
                    icon = Icons.Default.Mic,
                    angle = 270f,
                    onClick = { onNavigate("QUICK_AUDIO"); onExpandChange(false) }
                )
                QuickBallMenuItem(
                    icon = Icons.Default.Link,
                    angle = 315f,
                    onClick = { onNavigate("ADD_URL"); onExpandChange(false) }
                )
            }
        }

        // Main Ball
        IconButton(
            onClick = { onExpandChange(!isExpanded) },
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    )
                },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Quick Ball",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
fun QuickBallMenuItem(
    icon: ImageVector,
    angle: Float,
    radius: Float = 160f, // Distance from center
    onClick: () -> Unit
) {
    // Calculate position based on angle
    val rad = Math.toRadians(angle.toDouble())
    val x = (radius * cos(rad)).toFloat()
    val y = (radius * sin(rad)).toFloat()

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(icon, contentDescription = null)
    }
}
