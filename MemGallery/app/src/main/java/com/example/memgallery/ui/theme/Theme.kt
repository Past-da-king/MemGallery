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
import androidx.compose.material3.LocalContentColor
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
        customColor != -1 -> customColorScheme(if (useDarkTheme) DarkColorScheme else LightColorScheme, Color(customColor), useDarkTheme)
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

    CompositionLocalProvider(
        LocalMemColors provides if (useDarkTheme) DarkMemColors else LightMemColors,
        // Text and icons that name no colour must follow the theme. Without this a plain Text on a themed
        // background falls back to black, which is invisible on the dark scheme.
        LocalContentColor provides finalColorScheme.onBackground
    ) {
        MaterialExpressiveTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MemShapes,
            typography = Typography,
            content = content
        )
    }
}

/** WCAG contrast ratio between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance(); val l2 = b.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

/** Black or white text, whichever reads better on [bg]. */
private fun readableOn(bg: Color): Color =
    if (contrastRatio(bg, Color.White) >= contrastRatio(bg, Color.Black)) Color.White else Color.Black

/** Push [c] toward white (dark theme) or black (light theme) until it has at least [min] contrast against [against]. */
private fun ensureContrast(c: Color, against: Color, min: Float, towardsWhite: Boolean): Color {
    var out = c
    var t = 0f
    while (contrastRatio(out, against) < min && t < 1f) {
        t += 0.05f
        out = androidx.compose.ui.graphics.lerp(c, if (towardsWhite) Color.White else Color.Black, t)
    }
    return out.copy(alpha = 1f)
}

/**
 * A palette built from the user's seed colour that stays readable: opaque containers mixed into the surface,
 * and every "on" colour chosen by contrast (4.5:1), whatever the seed is (maroon, black, yellow).
 */
fun customColorScheme(base: ColorScheme, seed: Color, dark: Boolean): ColorScheme {
    val surface = base.surface
    val primary = ensureContrast(seed.copy(alpha = 1f), surface, 3f, towardsWhite = dark)
    val container = androidx.compose.ui.graphics.lerp(surface, primary, if (dark) 0.38f else 0.20f)
    val secondaryContainer = androidx.compose.ui.graphics.lerp(surface, primary, if (dark) 0.28f else 0.14f)
    return base.copy(
        primary = primary,
        onPrimary = readableOn(primary),
        primaryContainer = container,
        onPrimaryContainer = readableOn(container),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOn(secondaryContainer),
        surfaceTint = primary,
        inversePrimary = ensureContrast(seed.copy(alpha = 1f), base.inverseSurface, 3f, towardsWhite = !dark),
    )
}
