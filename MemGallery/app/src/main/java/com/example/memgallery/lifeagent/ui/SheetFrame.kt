package com.example.memgallery.lifeagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.memgallery.ui.design.MemSheet
import com.example.memgallery.ui.design.appNavigationBarsPadding

/** Test hook: when true, sheets are drawn in place (scrim plus a bottom panel) so a screenshot can include them. */
val LocalSheetInPlace = compositionLocalOf { false }

/** The one slide-up container of the Life Agent screens: the app's MemSheet on a device, an in-place panel in screenshot tests. */
@Composable
fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (LocalSheetInPlace.current) {
        val scheme = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.42f))) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(scheme.surfaceContainerLow)
                    .appNavigationBarsPadding()
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).width(32.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(scheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                content()
            }
        }
    } else {
        MemSheet(onDismiss = onDismiss, content = content)
    }
}
