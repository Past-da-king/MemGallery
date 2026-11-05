package com.example.memgallery.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.rememberAsyncImagePainter

/**
 * How a memory image model (content URI, file path or URL) becomes a Painter.
 * The app uses Coil; screenshot tests provide generated artwork so they need no files or network.
 */
val LocalMemoryPainter = staticCompositionLocalOf<@Composable (Any?) -> Painter> {
    { model -> rememberAsyncImagePainter(model = model) }
}

@Composable
fun memoryPainter(model: Any?): Painter = LocalMemoryPainter.current(model)
