package com.example.memgallery.ui.widget

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

// Mirrors com.example.memgallery.ui.theme.Color so widget reads as
// the same surface the rest of the app does.
internal val WidgetDefaultAccent = Color(0xFF8C25F4)        // theme.Primary
internal val WidgetBackgroundDark = Color(0xFF191022)        // theme.BackgroundDark
internal val WidgetBackgroundLight = Color(0xFFF7F5F8)       // theme.BackgroundLight
internal val WidgetAmoledBlack = Color(0xFF000000)

internal data class WidgetPalette(
    val background: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val railAlpha: Float,
    val haloAlpha: Float
)

internal fun resolvePalette(
    context: Context,
    themeMode: String,
    amoledMode: Boolean,
    customColor: Int,
    dynamicTheming: Boolean,
    isSystemDark: Boolean
): WidgetPalette {
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemDark
    }

    // Resolution mirrors ui/theme/Theme.kt:
    //   1. dynamicTheming wins on Android 12+ (Material You system colors)
    //   2. else customColor seed if set
    //   3. else app default Primary
    val useDynamic = dynamicTheming && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val accent: Color = when {
        useDynamic -> {
            val accentRes = if (isDark) android.R.color.system_accent1_200
                            else android.R.color.system_accent1_600
            Color(context.getColor(accentRes))
        }
        customColor != -1 -> Color(customColor)
        else -> WidgetDefaultAccent
    }

    return if (isDark) {
        val bg: Color = when {
            amoledMode -> WidgetAmoledBlack
            useDynamic -> Color(context.getColor(android.R.color.system_neutral1_900))
            else -> WidgetBackgroundDark
        }
        val onSurface: Color = when {
            useDynamic -> Color(context.getColor(android.R.color.system_neutral1_50))
            else -> Color(0xFFEDEDF2)
        }
        val onSurfaceMuted: Color = when {
            useDynamic -> Color(context.getColor(android.R.color.system_neutral2_200))
                .copy(alpha = 0.70f)
            else -> Color(0xA8B4B4BE)
        }
        WidgetPalette(
            background = bg,
            onSurface = onSurface,
            onSurfaceMuted = onSurfaceMuted,
            accent = accent,
            railAlpha = 0.12f,
            haloAlpha = 0.18f
        )
    } else {
        val bg: Color = when {
            useDynamic -> Color(context.getColor(android.R.color.system_neutral1_50))
            else -> WidgetBackgroundLight
        }
        val onSurface: Color = when {
            useDynamic -> Color(context.getColor(android.R.color.system_neutral1_900))
            else -> Color(0xFF1C1A1F)
        }
        val onSurfaceMuted: Color = when {
            useDynamic -> Color(context.getColor(android.R.color.system_neutral2_700))
                .copy(alpha = 0.75f)
            else -> Color(0xB05B5963)
        }
        WidgetPalette(
            background = bg,
            onSurface = onSurface,
            onSurfaceMuted = onSurfaceMuted,
            accent = accent,
            railAlpha = 0.18f,
            haloAlpha = 0.20f
        )
    }
}
