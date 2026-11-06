package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memgallery.ui.design.MemShape
import com.example.memgallery.ui.theme.memColors

/** Up to two initials of a name ("Nomsa Mahlangu" gives "NM"). */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/** A stable slot 0..6 for a name, so the same person always gets the same shape and tone. */
fun avatarSlot(name: String): Int = (name.trim().lowercase().fold(7) { a, c -> a * 31 + c.code }).mod(7)

/** Shaped initials avatar: the shape library and the tonal containers, never an image. */
@Composable
fun PersonAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 52.dp, fontSize: TextUnit = (size.value * 0.34f).sp) {
    val scheme = MaterialTheme.colorScheme
    val mem = MaterialTheme.memColors
    val slot = avatarSlot(name)
    val shape: Shape = when (slot) {
        0 -> MemShape.cookie
        1 -> MemShape.clover
        2 -> MemShape.softBurst
        3 -> MemShape.sunny
        4 -> MemShape.puffy
        5 -> MemShape.gem
        else -> MemShape.arch
    }
    val (bg, fg) = when (slot) {
        0 -> mem.amberContainer to mem.onAmberContainer
        1 -> mem.skyContainer to mem.onSkyContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        3 -> mem.successContainer to mem.onSuccessContainer
        4 -> scheme.primaryContainer to scheme.onPrimaryContainer
        5 -> mem.amberContainer to mem.onAmberContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Box(modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Text(initialsOf(name), style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize, fontWeight = FontWeight.ExtraBold), color = fg)
    }
}

@Suppress("unused")
private val keepColor: Color = Color.Unspecified
