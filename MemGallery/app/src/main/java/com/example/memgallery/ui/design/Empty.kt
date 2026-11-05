package com.example.memgallery.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.memgallery.ui.theme.memColors

/** Empty-state artwork: a soft burst with a clover behind a primary cookie that carries the icon, plus small satellites. */
@Composable
fun EmptyArt(icon: ImageVector, modifier: Modifier = Modifier, scale: Float = 1f) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(260.dp * scale), contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp * scale).clip(MemShape.softBurst).background(scheme.primaryContainer))
        Box(Modifier.size(130.dp * scale).rotate(20f).clip(MemShape.clover).background(scheme.tertiaryContainer))
        Box(Modifier.size(78.dp * scale).clip(MemShape.cookie).background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onPrimary, modifier = Modifier.size(40.dp * scale))
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp * scale, y = 6.dp * scale).size(46.dp * scale).clip(MemShape.pentagon).background(MaterialTheme.memColors.successContainer))
        Box(Modifier.align(Alignment.BottomStart).offset(x = 10.dp * scale, y = (-26).dp * scale).size(32.dp * scale).clip(MemShape.gem).background(MaterialTheme.memColors.skyContainer))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-26).dp * scale, y = (-24).dp * scale).size(34.dp * scale).clip(MemShape.sunny).background(MaterialTheme.memColors.amberContainer))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    primaryIcon: ImageVector? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    artScale: Float = 1f
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyArt(icon, scale = artScale)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (primaryLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPrimary, modifier = Modifier.height(48.dp)) {
                if (primaryIcon != null) {
                    Icon(primaryIcon, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (secondaryLabel != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
