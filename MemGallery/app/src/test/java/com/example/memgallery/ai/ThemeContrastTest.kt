package com.example.memgallery.ai

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.memgallery.ui.theme.MemTokens
import com.example.memgallery.ui.theme.contrastRatio
import com.example.memgallery.ui.theme.customColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every pairing the app draws text on must reach 4.5:1, for the designed palette and for any custom seed. */
class ThemeContrastTest {
    private val seeds = listOf(0xFF800000, 0xFF000000, 0xFFFFFFFF, 0xFFFFEB3B, 0xFF1B5E20, 0xFFE91E63, 0xFF0D47A1).map { Color(it.toInt()) }

    private val dark = darkColorScheme(
        surface = MemTokens.Dark.surface, onSurface = MemTokens.Dark.onSurface,
        inverseSurface = MemTokens.Dark.inverseSurface, inverseOnSurface = MemTokens.Dark.inverseOnSurface
    )
    private val light = lightColorScheme(
        surface = MemTokens.Light.surface, onSurface = MemTokens.Light.onSurface,
        inverseSurface = MemTokens.Light.inverseSurface, inverseOnSurface = MemTokens.Light.inverseOnSurface
    )

    @Test fun designedPalettePairsReachBodyContrast() {
        fun check(name: String, fg: Color, bg: Color) = assertTrue("$name ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= 4.5f)
        with(MemTokens.Dark) {
            check("dark onSurface/surface", onSurface, surface)
            check("dark onSurface/surfaceHigh", onSurface, surfaceHigh)
            check("dark onSurfaceVariant/surfaceHigh", onSurfaceVariant, surfaceHigh)
            check("dark onPrimary/primary", onPrimary, primary)
            check("dark onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer)
            check("dark onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer)
            check("dark onErrorContainer/errorContainer", onErrorContainer, errorContainer)
            check("dark onError/error", onError, error)
        }
        with(MemTokens.Light) {
            check("light onSurface/surface", onSurface, surface)
            check("light onSurfaceVariant/surfaceHigh", onSurfaceVariant, surfaceHigh)
            check("light onPrimary/primary", onPrimary, primary)
            check("light onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer)
            check("light onErrorContainer/errorContainer", onErrorContainer, errorContainer)
        }
    }

    @Test fun customSeedsAreReadableInBothThemes() {
        for (seed in seeds) for ((name, base) in listOf("dark" to dark, "light" to light)) {
            val s = customColorScheme(base, seed, dark = name == "dark")
            fun check(pair: String, fg: Color, bg: Color) = assertTrue("$name seed=${seed.value} $pair ${contrastRatio(fg, bg)}", contrastRatio(fg, bg) >= 4.5f)
            check("onPrimary/primary", s.onPrimary, s.primary)
            check("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            check("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            assertTrue("$name seed=${seed.value} primary on surface ${contrastRatio(s.primary, s.surface)}", contrastRatio(s.primary, s.surface) >= 3f)
        }
    }
}
