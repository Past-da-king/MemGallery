package com.example.memgallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundDark
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = BackgroundLight

    /* Other default colors to override
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MemGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    appThemeMode: String = "SYSTEM",
    amoledMode: Boolean = false,
    customColor: Int = -1,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (appThemeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customColor != -1 -> {
            val seedColor = androidx.compose.ui.graphics.Color(customColor)
            if (useDarkTheme) {
                darkColorScheme(
                    primary = seedColor,
                    onPrimary = androidx.compose.ui.graphics.Color.Black,
                    primaryContainer = seedColor.copy(alpha = 0.3f),
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.White, // High contrast for dark mode
                    secondary = seedColor,
                    onSecondary = androidx.compose.ui.graphics.Color.Black,
                    secondaryContainer = seedColor.copy(alpha = 0.3f),
                    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
                    tertiary = seedColor,
                    onTertiary = androidx.compose.ui.graphics.Color.Black,
                    tertiaryContainer = seedColor.copy(alpha = 0.3f),
                    onTertiaryContainer = androidx.compose.ui.graphics.Color.White
                )
            } else {
                lightColorScheme(
                    primary = seedColor,
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    primaryContainer = seedColor.copy(alpha = 0.2f), // Slightly more visible container
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black, // High contrast for light mode
                    secondary = seedColor,
                    onSecondary = androidx.compose.ui.graphics.Color.White,
                    secondaryContainer = seedColor.copy(alpha = 0.2f),
                    onSecondaryContainer = androidx.compose.ui.graphics.Color.Black,
                    tertiary = seedColor,
                    onTertiary = androidx.compose.ui.graphics.Color.White,
                    tertiaryContainer = seedColor.copy(alpha = 0.2f),
                    onTertiaryContainer = androidx.compose.ui.graphics.Color.Black
                )
            }
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalColorScheme = if (amoledMode && useDarkTheme) {
        colorScheme.copy(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black,
            surfaceContainer = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLow = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF121212) // Slightly lighter for cards
        )
    } else {
        colorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}
