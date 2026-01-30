package com.example.memgallery.ui.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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
            androidx.compose.ui.unit.DpSize(50.dp, 50.dp),
            androidx.compose.ui.unit.DpSize(100.dp, 100.dp),
            androidx.compose.ui.unit.DpSize(250.dp, 100.dp),
            androidx.compose.ui.unit.DpSize(250.dp, 250.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            val json = currentState(WidgetKeys.widgetDataKey) ?: "[]"
            val themeColorInt = currentState(WidgetKeys.themeColorKey) ?: -1
            
            val gson = Gson()
            val type = object : TypeToken<List<WidgetMemoryItem>>() {}.type
            val items: List<WidgetMemoryItem> = try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }

            // Sync with User-Selected Theme Color
            val pulseColor = if (themeColorInt != -1) Color(themeColorInt) else Color(0xFF6A11CB)
            
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (items.isEmpty()) {
                        EmptyPulseState(pulseColor)
                    } else {
                        // THE TIMELINE PULSE LAYOUT - 10X Different
                        Box(modifier = GlanceModifier.fillMaxSize()) {
                            // The Pulse Line (Vertical) - Left side
                            Box(
                                modifier = GlanceModifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .padding(start = 12.dp)
                                    .background(ColorProvider(pulseColor.copy(alpha = 0.3f)))
                                    .cornerRadius(2.dp)
                            ) { }

                            // Scrollable Timeline Content
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(items) { item ->
                                    TimelinePulseItem(item, pulseColor)
                                }
                                item { 
                                    // Bottom Spacer for clean finish
                                    Spacer(modifier = GlanceModifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePulseItem(item: WidgetMemoryItem, pulseColor: Color) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(androidx.glance.action.ActionParameters.Key<String>("navigate_to") to "detail/${item.id}")
            )),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Node Section
        Box(
            modifier = GlanceModifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pulse Node (Circle)
            Box(
                modifier = GlanceModifier
                    .size(12.dp)
                    .background(ColorProvider(pulseColor))
                    .cornerRadius(6.dp)
            ) { }
            
            // Subtle Outer Glow for the node
            Box(
                modifier = GlanceModifier
                    .size(20.dp)
                    .background(ColorProvider(pulseColor.copy(alpha = 0.15f)))
                    .cornerRadius(10.dp)
            ) { }
        }

        // Content Section
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = item.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                maxLines = 1
            )
            
            if (item.formattedDateTime != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = item.formattedDateTime!!,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Image/Icon Thumbnail Section - Floating style
        Box(
            modifier = GlanceModifier
                .size(56.dp)
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!item.imagePath.isNullOrEmpty()) {
                Image(
                    provider = getImageProvider(item),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize()
                )
            } else {
                // Large subtle icon for audio/text
                val iconRes = when (item.type) {
                    "AUDIO" -> R.drawable.ic_mic
                    "Camera" -> R.drawable.ic_camera
                    else -> R.drawable.ic_shortcut_note
                }
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(28.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer)
                )
            }
        }
    }
}

@Composable
private fun EmptyPulseState(pulseColor: Color) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(64.dp)
                .background(ColorProvider(pulseColor.copy(alpha = 0.1f)))
                .cornerRadius(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.rounded_image_24),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
                colorFilter = ColorFilter.tint(ColorProvider(pulseColor))
            )
        }
        Spacer(modifier = GlanceModifier.height(16.dp))
        Text(
            text = "No Pulse Detected",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
        Text(
            text = "Your memories are safe and sound.",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
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
