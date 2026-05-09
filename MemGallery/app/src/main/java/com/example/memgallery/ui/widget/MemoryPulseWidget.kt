package com.example.memgallery.ui.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class MemoryPulseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            androidx.compose.ui.unit.DpSize(180.dp, 110.dp),
            androidx.compose.ui.unit.DpSize(260.dp, 110.dp),
            androidx.compose.ui.unit.DpSize(260.dp, 200.dp),
            androidx.compose.ui.unit.DpSize(320.dp, 320.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            val json = currentState(WidgetKeys.widgetDataKey) ?: "[]"
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

            val gson = Gson()
            val type = object : TypeToken<List<WidgetMemoryItem>>() {}.type
            val items: List<WidgetMemoryItem> = try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackgroundCornerRadius()
                        .background(palette.background),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (items.isEmpty()) {
                        EmptyPulseState(palette)
                    } else {
                        TimelineBody(items = items, palette = palette, size = size)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineBody(
    items: List<WidgetMemoryItem>,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize
) {
    val isCompact = size.height < 160.dp
    val visible = if (isCompact) items.take(2) else items.take(6)

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(
            modifier = GlanceModifier
                .width(2.dp)
                .fillMaxHeight()
                .padding(start = 18.dp, top = 18.dp, bottom = 18.dp)
                .background(ColorProvider(palette.accent.copy(alpha = palette.railAlpha)))
        ) {}

        Column(modifier = GlanceModifier.fillMaxSize().padding(vertical = 6.dp)) {
            visible.forEachIndexed { idx, item ->
                TimelineRow(item, palette, isCompact)
                if (idx < visible.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    item: WidgetMemoryItem,
    palette: WidgetPalette,
    isCompact: Boolean
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 8.dp else 10.dp, horizontal = 10.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(
                        androidx.glance.action.ActionParameters
                            .Key<String>("navigate_to") to "detail/${item.id}"
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.width(28.dp).height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(18.dp)
                    .cornerRadius(9.dp)
                    .background(ColorProvider(palette.accent.copy(alpha = palette.haloAlpha)))
            ) {}
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(ColorProvider(palette.accent))
            ) {}
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = ColorProvider(palette.onSurface),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )

            if (item.summary.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = item.summary,
                    style = TextStyle(
                        color = ColorProvider(palette.onSurfaceMuted),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = if (isCompact) 1 else 2
                )
            }

            if (!item.formattedDateTime.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = item.formattedDateTime!!,
                    style = TextStyle(
                        color = ColorProvider(palette.accent.copy(alpha = 0.90f)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

        Thumbnail(item, palette, sizeDp = if (isCompact) 44.dp else 52.dp)
    }
}

@Composable
private fun Thumbnail(item: WidgetMemoryItem, palette: WidgetPalette, sizeDp: Dp) {
    Box(
        modifier = GlanceModifier
            .size(sizeDp)
            .cornerRadius(14.dp)
            .background(ColorProvider(palette.accent.copy(alpha = 0.10f))),
        contentAlignment = Alignment.Center
    ) {
        if (!item.imagePath.isNullOrEmpty()) {
            Image(
                provider = getImageProvider(item),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp)
            )
        } else {
            val iconRes = when (item.type) {
                "AUDIO" -> R.drawable.ic_mic
                "Camera" -> R.drawable.ic_camera
                else -> R.drawable.ic_shortcut_note
            }
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(sizeDp.value.toInt().div(2.5f).dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.accent.copy(alpha = 0.85f)))
            )
        }
    }
}

@Composable
private fun EmptyPulseState(palette: WidgetPalette) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(40.dp)
                .cornerRadius(20.dp)
                .background(ColorProvider(palette.accent.copy(alpha = 0.10f))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(ColorProvider(palette.accent))
            ) {}
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        Text(
            text = "No pulse yet",
            style = TextStyle(
                color = ColorProvider(palette.onSurface),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = "Captured memories will surface here.",
            style = TextStyle(
                color = ColorProvider(palette.onSurfaceMuted),
                fontSize = 11.sp
            ),
            maxLines = 2
        )
    }
}

private fun getImageProvider(item: WidgetMemoryItem): ImageProvider {
    return try {
        val file = File(Uri.parse(item.imagePath).path ?: "")
        if (file.exists()) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) ImageProvider(bitmap) else ImageProvider(R.drawable.rounded_image_24)
        } else {
            ImageProvider(R.drawable.rounded_image_24)
        }
    } catch (e: Exception) {
        ImageProvider(R.drawable.rounded_image_24)
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Composable
private fun GlanceModifier.appWidgetBackgroundCornerRadius(): GlanceModifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        this.cornerRadius(20.dp)
    }
}

@Composable
@Suppress("unused")
private fun GlanceModifier.appWidgetInnerCornerRadius(widgetPadding: Dp): GlanceModifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val resources = LocalContext.current.resources
    val px = resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    val widgetBackgroundRadiusDpValue = px / resources.displayMetrics.density
    if (widgetBackgroundRadiusDpValue < widgetPadding.value) return this
    return this.cornerRadius((widgetBackgroundRadiusDpValue - widgetPadding.value).dp)
}

