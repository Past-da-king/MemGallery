package com.example.memgallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.memgallery.R

/** Google Sans Flex, bundled as a variable font resource (no network fonts). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.google_sans_flex,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("ROND", 100f))
        )
    }
)

private fun t(
    size: Int, line: Int, weight: Int, tracking: Float = 0f
) = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

/** v4 type scale. Display and headline use the emphasized (heavier, tighter) styles. */
val Typography = Typography(
    displayLarge = t(57, 64, 700, -0.5f),
    displayMedium = t(45, 50, 700, -1f),
    displaySmall = t(36, 42, 700, -0.5f),
    headlineLarge = t(32, 38, 700, -0.25f),
    headlineMedium = t(28, 34, 700, -0.2f),
    headlineSmall = t(24, 30, 700, 0f),
    titleLarge = t(22, 28, 700, 0f),
    titleMedium = t(16, 22, 700, 0.1f),
    titleSmall = t(14, 20, 600, 0.1f),
    bodyLarge = t(16, 24, 400, 0.3f),
    bodyMedium = t(14, 20, 400, 0.2f),
    bodySmall = t(12, 16, 400, 0.3f),
    labelLarge = t(14, 20, 600, 0.1f),
    labelMedium = t(12, 16, 600, 0.5f),
    labelSmall = t(11, 16, 600, 0.6f),
)
