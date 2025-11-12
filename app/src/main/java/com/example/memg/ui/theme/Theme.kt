package com.example.memg.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppDarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = DarkBlue,
    primaryContainer = LightBlue,
    onPrimaryContainer = OffWhite,
    secondary = Teal,
    onSecondary = DarkBlue,
    secondaryContainer = Slate,
    onSecondaryContainer = DarkBlue,
    tertiary = Teal,
    onTertiary = DarkBlue,
    tertiaryContainer = LightBlue,
    onTertiaryContainer = OffWhite,
    error = ErrorRed,
    onError = OffWhite,
    background = DarkBlue,
    onBackground = OffWhite,
    surface = MediumBlue,
    onSurface = LightGrey,
    surfaceVariant = LightBlue,
    onSurfaceVariant = OffWhite,
    outline = Slate
)

@Composable
fun MemgTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography = Typography,
        content = content
    )
}