package com.example.memgallery.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MemGallery v4 colour tokens: the single source for every colour in the app.
 * Mirrors mocks-v4/src/tokens.css (seed: violet-indigo hue 268, tertiary rose).
 * Screens never hard-code colours; they read MaterialTheme.colorScheme or MaterialTheme.memColors.
 */
object MemTokens {
    object Light {
        val primary = Color(0xFF5B46CF)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFE5DFFF)
        val onPrimaryContainer = Color(0xFF1C0E6C)
        val secondary = Color(0xFF5D5B7D)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFE2DFF9)
        val onSecondaryContainer = Color(0xFF1A1936)
        val tertiary = Color(0xFFA03A60)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFFFD9E4)
        val onTertiaryContainer = Color(0xFF3D0020)
        val error = Color(0xFFBA1A1A)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFFFDAD6)
        val onErrorContainer = Color(0xFF410002)
        val success = Color(0xFF1F6B44)
        val successContainer = Color(0xFFC2F0D2)
        val onSuccessContainer = Color(0xFF00210F)
        val sky = Color(0xFF0B6A88)
        val skyContainer = Color(0xFFC4EAF9)
        val onSkyContainer = Color(0xFF001F2B)
        val amberContainer = Color(0xFFFFE2A8)
        val onAmberContainer = Color(0xFF2B1D00)
        val surface = Color(0xFFFCF8FF)
        val surfaceLowest = Color(0xFFFFFFFF)
        val surfaceLow = Color(0xFFF6F2FB)
        val surfaceContainer = Color(0xFFF0ECF6)
        val surfaceHigh = Color(0xFFEAE6F0)
        val surfaceHighest = Color(0xFFE4E0EA)
        val onSurface = Color(0xFF1B1B21)
        val onSurfaceVariant = Color(0xFF47464F)
        val outline = Color(0xFF78767F)
        val outlineVariant = Color(0xFFC9C5D0)
        val scrim = Color(0xFF141028)
        val inverseSurface = Color(0xFF303036)
        val inverseOnSurface = Color(0xFFF3EFF7)
    }

    object Dark {
        val primary = Color(0xFFC9C1FF)
        val onPrimary = Color(0xFF2C1A9A)
        val primaryContainer = Color(0xFF3F3596)
        val onPrimaryContainer = Color(0xFFE5DFFF)
        val secondary = Color(0xFFC7C3E2)
        val onSecondary = Color(0xFF2F2E4D)
        val secondaryContainer = Color(0xFF454464)
        val onSecondaryContainer = Color(0xFFE2DFF9)
        val tertiary = Color(0xFFFFB1C8)
        val onTertiary = Color(0xFF5E1135)
        val tertiaryContainer = Color(0xFF7F2650)
        val onTertiaryContainer = Color(0xFFFFD9E4)
        val error = Color(0xFFFFB4AB)
        val onError = Color(0xFF690005)
        val errorContainer = Color(0xFF93000A)
        val onErrorContainer = Color(0xFFFFDAD6)
        val success = Color(0xFF86D8A5)
        val successContainer = Color(0xFF0B5231)
        val onSuccessContainer = Color(0xFFC2F0D2)
        val sky = Color(0xFF7FD0EE)
        val skyContainer = Color(0xFF004D66)
        val onSkyContainer = Color(0xFFC4EAF9)
        val amberContainer = Color(0xFF5C4300)
        val onAmberContainer = Color(0xFFFFE2A8)
        val surface = Color(0xFF131318)
        val surfaceLowest = Color(0xFF0E0E13)
        val surfaceLow = Color(0xFF1B1B21)
        val surfaceContainer = Color(0xFF1F1F25)
        val surfaceHigh = Color(0xFF2A292F)
        val surfaceHighest = Color(0xFF35343A)
        val onSurface = Color(0xFFE5E1E9)
        val onSurfaceVariant = Color(0xFFC9C5D0)
        val outline = Color(0xFF928F99)
        val outlineVariant = Color(0xFF47464F)
        val scrim = Color(0xFF000000)
        val inverseSurface = Color(0xFFE5E1E9)
        val inverseOnSurface = Color(0xFF303036)
    }
}

/** Roles that Material's ColorScheme has no slot for: memory kinds and success. */
data class MemColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val sky: Color,
    val skyContainer: Color,
    val onSkyContainer: Color,
    val amberContainer: Color,
    val onAmberContainer: Color,
)

val LightMemColors = MemColors(
    success = MemTokens.Light.success,
    successContainer = MemTokens.Light.successContainer,
    onSuccessContainer = MemTokens.Light.onSuccessContainer,
    sky = MemTokens.Light.sky,
    skyContainer = MemTokens.Light.skyContainer,
    onSkyContainer = MemTokens.Light.onSkyContainer,
    amberContainer = MemTokens.Light.amberContainer,
    onAmberContainer = MemTokens.Light.onAmberContainer,
)

val DarkMemColors = MemColors(
    success = MemTokens.Dark.success,
    successContainer = MemTokens.Dark.successContainer,
    onSuccessContainer = MemTokens.Dark.onSuccessContainer,
    sky = MemTokens.Dark.sky,
    skyContainer = MemTokens.Dark.skyContainer,
    onSkyContainer = MemTokens.Dark.onSkyContainer,
    amberContainer = MemTokens.Dark.amberContainer,
    onAmberContainer = MemTokens.Dark.onAmberContainer,
)
