package com.example.memgallery.ui.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.memgallery.MainActivity
import com.example.memgallery.R

class CaptureBarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val themeColorInt = currentState(WidgetKeys.themeColorKey) ?: -1
            val themeMode = currentState(WidgetKeys.themeModeKey) ?: "SYSTEM"
            val amoledMode = currentState(WidgetKeys.amoledModeKey) ?: false
            val dynamicTheming = currentState(WidgetKeys.dynamicThemingKey) ?: true

            val ctx = LocalContext.current
            val systemDark = (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

            val palette = resolvePalette(
                context = ctx,
                themeMode = themeMode,
                amoledMode = amoledMode,
                customColor = themeColorInt,
                dynamicTheming = dynamicTheming,
                isSystemDark = systemDark
            )

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .captureBarBackgroundCornerRadius()
                        .background(palette.background)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CaptureTile(
                            label = "Voice",
                            iconRes = R.drawable.ic_mic,
                            shortcut = "record_audio",
                            palette = palette,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        CaptureTile(
                            label = "Camera",
                            iconRes = R.drawable.ic_camera,
                            shortcut = "camera_capture",
                            palette = palette,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        CaptureTile(
                            label = "Note",
                            iconRes = R.drawable.ic_note,
                            shortcut = "text_note",
                            palette = palette,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureTile(
    label: String,
    iconRes: Int,
    shortcut: String,
    palette: WidgetPalette,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .cornerRadius(18.dp)
            .background(ColorProvider(palette.accent.copy(alpha = 0.10f)))
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(
                        ActionParameters.Key<String>("shortcut_action") to shortcut
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.accent))
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(palette.onSurface),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun GlanceModifier.captureBarBackgroundCornerRadius(): GlanceModifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        this.cornerRadius(20.dp)
    }
}

@Suppress("unused")
private val CaptureBarRefs: Color = Color.Transparent
