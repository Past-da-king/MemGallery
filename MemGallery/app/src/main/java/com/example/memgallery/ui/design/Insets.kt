package com.example.memgallery.ui.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Fixed system bar sizes for places that have no real window (screenshot tests, previews).
 * In the app these are null and the real window insets are used.
 */
data class FixedInsets(val top: Dp, val bottom: Dp)

val LocalFixedInsets = compositionLocalOf<FixedInsets?> { null }

/** Status bar padding that honours [LocalFixedInsets]. */
@Composable
fun Modifier.appStatusBarsPadding(): Modifier {
    val fixed = LocalFixedInsets.current
    return if (fixed != null) padding(top = fixed.top) else windowInsetsPadding(WindowInsets.statusBars)
}

/** Navigation bar padding that honours [LocalFixedInsets]. */
@Composable
fun Modifier.appNavigationBarsPadding(): Modifier {
    val fixed = LocalFixedInsets.current
    return if (fixed != null) padding(bottom = fixed.bottom) else windowInsetsPadding(WindowInsets.navigationBars)
}

/** Lets a full-width item (header, filters, carousel) bleed through the horizontal content padding of its list. */
fun Modifier.fullBleed(padding: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = padding.roundToPx() * 2
        val widened = constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra)
        val p = measurable.measure(widened)
        layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
    }
)
