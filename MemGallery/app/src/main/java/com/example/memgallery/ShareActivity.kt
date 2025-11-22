package com.example.memgallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.memgallery.data.repository.MemoryRepository
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.memgallery.ui.theme.MemGalleryTheme
import javax.inject.Inject

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject
    lateinit var memoryRepository: MemoryRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if share handling is enabled
        lifecycleScope.launch {
            val enabled = settingsRepository.showInShareSheetFlow.first()
            if (!enabled && intent?.action == Intent.ACTION_SEND) {
                finish()
                return@launch
            }

            if (intent?.action == Intent.ACTION_SEND) {
                setContent {
                    MemGalleryTheme {
                        ShareDialog(
                            onQuickSave = { handleShare(quickSave = true) },
                            onEdit = { handleShare(quickSave = false) },
                            onDismiss = { finish() }
                        )
                    }
                }
            } else {
                finish()
            }
        }
    }

    @Composable
    fun ShareDialog(
        onQuickSave: () -> Unit,
        onEdit: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Save to MemGallery") },
            text = { Text("Choose how you want to save this memory.") },
            confirmButton = {
                Button(onClick = onQuickSave) {
                    Text("Quick Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
            }
        )
    }

    private fun handleShare(quickSave: Boolean) {
        lifecycleScope.launch {
            try {
                val imageUri = if (intent.type?.startsWith("image/") == true) {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
                } else null

                val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                if (quickSave) {
                    // Extract URL from text if present
                    val urlRegex = "(https?://\\S+)".toRegex()
                    val bookmarkUrl = text?.let { urlRegex.find(it)?.value }

                    // Save to database
                    memoryRepository.savePendingMemory(
                        imageUri = imageUri,
                        audioUri = null,
                        userText = text,
                        bookmarkUrl = bookmarkUrl
                    )

                    runOnUiThread {
                        Toast.makeText(this@ShareActivity, "Saved to MemGallery!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    // Forward to MainActivity for editing
                    val mainIntent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        type = intent.type
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (imageUri != null) {
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUri))
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(mainIntent)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ShareActivity, "Failed to process", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
