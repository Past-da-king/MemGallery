package com.example.memgallery.ui.components

import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.image.network.NetworkSchemeHandler
import java.io.File

@Composable
fun MarkdownText(
    markdown: String, 
    modifier: Modifier = Modifier, 
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
    val context = LocalContext.current
    val onSurfaceColor = style.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } 
        ?: MaterialTheme.colorScheme.onSurface
    
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create { plugin ->
                // Basic network support (http/https)
                plugin.addSchemeHandler(NetworkSchemeHandler.create())
                
                // File support (file:///), including assets
                plugin.addSchemeHandler(FileSchemeHandler.createWithAssets(context))
                
                // Custom handler for deeper control if needed, 
                // but FileSchemeHandler should handle standard file paths already.
                // We add a fallback content handler for robustness.
                plugin.addSchemeHandler(object : io.noties.markwon.image.SchemeHandler() {
                    override fun handle(raw: String, uri: Uri): io.noties.markwon.image.ImageItem {
                        return try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                io.noties.markwon.image.ImageItem.withDecodingNeeded(
                                    "content",
                                    java.io.BufferedInputStream(inputStream)
                                )
                            } else {
                                io.noties.markwon.image.ImageItem.withDecodingNeeded(
                                    "content",
                                    java.io.ByteArrayInputStream(ByteArray(0))
                                )
                            }
                        } catch (e: Exception) {
                            io.noties.markwon.image.ImageItem.withDecodingNeeded(
                                "content",
                                java.io.ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }

                    override fun supportedSchemes(): MutableCollection<String> {
                        return mutableListOf("content")
                    }
                })
            })
            // Use syntax highlighting if needed, but not required for these fixes
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(onSurfaceColor.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
                setLineSpacing(0f, 1.2f)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        }
    )
}
