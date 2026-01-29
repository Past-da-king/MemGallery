package com.example.memgallery.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import androidx.compose.runtime.Composable
import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

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
            GlanceTheme {
                val size = LocalSize.current
                val json = currentState(WidgetKeys.widgetDataKey) ?: "[]"
                val gson = Gson()
                val type = object : TypeToken<List<WidgetMemoryItem>>() {}.type
                val items: List<WidgetMemoryItem> = try {
                    gson.fromJson(json, type)
                } catch (e: Exception) {
                    emptyList()
                }

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (items.isEmpty()) {
                        EmptyWidgetState()
                    } else {
                        when {
                            // Small Square (Icon Only)
                            size.width < 150.dp || size.height < 100.dp -> {
                                SmallWidgetContent(items.first())
                            }
                            // Wide Row (Single Item)
                            size.height < 200.dp -> {
                                MediumWidgetContent(items.first())
                            }
                            // List View
                            else -> {
                                ListWidgetContent(items)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWidgetState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
         Text(
            text = "No Pulse",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium)
        )
        Text(
            text = "Add tasks to see memories",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)
        )
    }
}

@Composable
private fun SmallWidgetContent(item: WidgetMemoryItem) {
    val imageProvider = getImageProvider(item)
    
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(androidx.glance.action.ActionParameters.Key<String>("navigate_to") to "detail/${item.id}")
            ))
            .cornerRadius(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = imageProvider,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize()
        )
        
        // Semi-transparent overlay at bottom if date exists
        if (item.formattedDateTime != null) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                 Box(
                     modifier = GlanceModifier
                         .fillMaxWidth()
                         .background(Color.Black.copy(alpha = 0.6f))
                         .padding(4.dp)
                 ) {
                     Text(
                         text = item.dueTime ?: "Event",
                         style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp),
                         modifier = GlanceModifier.fillMaxWidth()
                     )
                 }
            }
        }
    }
}

@Composable
private fun MediumWidgetContent(item: WidgetMemoryItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>(
                actionParametersOf(androidx.glance.action.ActionParameters.Key<String>("navigate_to") to "detail/${item.id}")
            ))
            .background(GlanceTheme.colors.background)
            .cornerRadius(16.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Image
         Box(
            modifier = GlanceModifier
                .size(64.dp)
                .cornerRadius(12.dp)
        ) {
             Image(
                provider = getImageProvider(item),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = GlanceModifier.width(16.dp))
        
        // Right Text
        Column(modifier = GlanceModifier.defaultWeight()) {
             Text(
                text = item.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            
            // Date Chip
            if (item.formattedDateTime != null) {
                DateChip(item.formattedDateTime)
            } else if (item.summary.isNotBlank()) {
                 Text(
                    text = item.summary,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant, 
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ListWidgetContent(items: List<WidgetMemoryItem>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items) { item ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable(actionStartActivity<MainActivity>(
                        actionParametersOf(androidx.glance.action.ActionParameters.Key<String>("navigate_to") to "detail/${item.id}")
                    ))
                    .background(GlanceTheme.colors.background)
                    .cornerRadius(16.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(12.dp)
                ) {
                     Image(
                        provider = getImageProvider(item),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = GlanceModifier.width(16.dp))
                
                Column(modifier = GlanceModifier.defaultWeight()) {
                     Text(
                        text = item.title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    
                    if (item.formattedDateTime != null) {
                        DateChip(item.formattedDateTime)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateChip(text: String) {
    Row(
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simple text for now, could add icon if needed
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

private fun getImageProvider(item: WidgetMemoryItem): ImageProvider {
    return if (!item.imagePath.isNullOrEmpty()) {
        try {
            val file = File(Uri.parse(item.imagePath).path ?: "")
            if (file.exists()) {
                 val options = BitmapFactory.Options().apply {
                     inJustDecodeBounds = true
                 }
                 BitmapFactory.decodeFile(file.absolutePath, options)
                 
                 val reqWidth = 400
                 val reqHeight = 400
                 options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                 
                 options.inJustDecodeBounds = false
                 val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                 
                 if (bitmap != null) {
                    ImageProvider(bitmap)
                 } else {
                    ImageProvider(R.drawable.rounded_image_24)
                 }
            } else {
                 ImageProvider(R.drawable.rounded_image_24)
            }
        } catch (e: Exception) {
             e.printStackTrace()
             ImageProvider(R.drawable.rounded_image_24)
        }
    } else {
        when (item.type) {
            "AUDIO" -> ImageProvider(R.drawable.ic_mic) 
            "Camera" -> ImageProvider(R.drawable.ic_camera)
            else -> ImageProvider(R.drawable.rounded_image_24)
        }
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
