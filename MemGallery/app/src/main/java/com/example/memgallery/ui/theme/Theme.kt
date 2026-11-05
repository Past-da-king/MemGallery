package com.example.memgallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalMemColors = staticCompositionLocalOf { LightMemColors }

/** Success, sky and amber roles (memory kinds). */
val MaterialTheme.memColors: MemColors
    @Composable @ReadOnlyComposable get() = LocalMemColors.current

/** True when the active scheme is the dark one (read from the surface luminance, so it follows the app theme mode). */
val MaterialTheme.isDark: Boolean
    @Composable @ReadOnlyComposable get() = colorScheme.surface.luminance() < 0.5f

private val DarkColorScheme = darkColorScheme(
    primary = MemTokens.Dark.primary, onPrimary = MemTokens.Dark.onPrimary,
    primaryContainer = MemTokens.Dark.primaryContainer, onPrimaryContainer = MemTokens.Dark.onPrimaryContainer,
    secondary = MemTokens.Dark.secondary, onSecondary = MemTokens.Dark.onSecondary,
    secondaryContainer = MemTokens.Dark.secondaryContainer, onSecondaryContainer = MemTokens.Dark.onSecondaryContainer,
    tertiary = MemTokens.Dark.tertiary, onTertiary = MemTokens.Dark.onTertiary,
    tertiaryContainer = MemTokens.Dark.tertiaryContainer, onTertiaryContainer = MemTokens.Dark.onTertiaryContainer,
    error = MemTokens.Dark.error, onError = MemTokens.Dark.onError,
    errorContainer = MemTokens.Dark.errorContainer, onErrorContainer = MemTokens.Dark.onErrorContainer,
    background = MemTokens.Dark.surface, onBackground = MemTokens.Dark.onSurface,
    surface = MemTokens.Dark.surface, onSurface = MemTokens.Dark.onSurface,
    surfaceVariant = MemTokens.Dark.surfaceHighest, onSurfaceVariant = MemTokens.Dark.onSurfaceVariant,
    surfaceTint = MemTokens.Dark.primary,
    inverseSurface = MemTokens.Dark.inverseSurface, inverseOnSurface = MemTokens.Dark.inverseOnSurface,
    inversePrimary = MemTokens.Light.primary,
    outline = MemTokens.Dark.outline, outlineVariant = MemTokens.Dark.outlineVariant,
    scrim = MemTokens.Dark.scrim,
    surfaceDim = MemTokens.Dark.surface, surfaceBright = MemTokens.Dark.surfaceHighest,
    surfaceContainerLowest = MemTokens.Dark.surfaceLowest, surfaceContainerLow = MemTokens.Dark.surfaceLow,
    surfaceContainer = MemTokens.Dark.surfaceContainer, surfaceContainerHigh = MemTokens.Dark.surfaceHigh,
    surfaceContainerHighest = MemTokens.Dark.surfaceHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = MemTokens.Light.primary, onPrimary = MemTokens.Light.onPrimary,
    primaryContainer = MemTokens.Light.primaryContainer, onPrimaryContainer = MemTokens.Light.onPrimaryContainer,
    secondary = MemTokens.Light.secondary, onSecondary = MemTokens.Light.onSecondary,
    secondaryContainer = MemTokens.Light.secondaryContainer, onSecondaryContainer = MemTokens.Light.onSecondaryContainer,
    tertiary = MemTokens.Light.tertiary, onTertiary = MemTokens.Light.onTertiary,
    tertiaryContainer = MemTokens.Light.tertiaryContainer, onTertiaryContainer = MemTokens.Light.onTertiaryContainer,
    error = MemTokens.Light.error, onError = MemTokens.Light.onError,
    errorContainer = MemTokens.Light.errorContainer, onErrorContainer = MemTokens.Light.onErrorContainer,
    background = MemTokens.Light.surface, onBackground = MemTokens.Light.onSurface,
    surface = MemTokens.Light.surface, onSurface = MemTokens.Light.onSurface,
    surfaceVariant = MemTokens.Light.surfaceHighest, onSurfaceVariant = MemTokens.Light.onSurfaceVariant,
    surfaceTint = MemTokens.Light.primary,
    inverseSurface = MemTokens.Light.inverseSurface, inverseOnSurface = MemTokens.Light.inverseOnSurface,
    inversePrimary = MemTokens.Dark.primary,
    outline = MemTokens.Light.outline, outlineVariant = MemTokens.Light.outlineVariant,
    scrim = MemTokens.Light.scrim,
    surfaceDim = MemTokens.Light.surfaceHigh, surfaceBright = MemTokens.Light.surface,
    surfaceContainerLowest = MemTokens.Light.surfaceLowest, surfaceContainerLow = MemTokens.Light.surfaceLow,
    surfaceContainer = MemTokens.Light.surfaceContainer, surfaceContainerHigh = MemTokens.Light.surfaceHigh,
    surfaceContainerHighest = MemTokens.Light.surfaceHighest,
)

/** Shape scale from the v4 tokens: 4, 8, 12, 16, 20, 28, 32, 48, full. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val MemShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic colour is opt-in so the app keeps its own violet identity by default.
    dynamicColor: Boolean = false,
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

    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customColor != -1 -> {
            val seedColor = Color(customColor)
            if (useDarkTheme) {
                val effectiveColor = if (seedColor == Color.Black) Color.White else seedColor
                DarkColorScheme.copy(
                    primary = effectiveColor,
                    onPrimary = Color.Black,
                    primaryContainer = effectiveColor.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.White,
                    secondaryContainer = effectiveColor.copy(alpha = 0.3f),
                    onSecondaryContainer = Color.White,
                )
            } else {
                LightColorScheme.copy(
                    primary = seedColor,
                    onPrimary = Color.White,
                    primaryContainer = seedColor.copy(alpha = 0.2f),
                    onPrimaryContainer = Color.Black,
                    secondaryContainer = seedColor.copy(alpha = 0.2f),
                    onSecondaryContainer = Color.Black,
                )
            }
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalColorScheme = if (amoledMode && useDarkTheme) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerHigh = Color(0xFF121212)
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

    CompositionLocalProvider(LocalMemColors provides if (useDarkTheme) DarkMemColors else LightMemColors) {
        MaterialExpressiveTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MemShapes,
            typography = Typography,
            content = content
        )
    }
}
