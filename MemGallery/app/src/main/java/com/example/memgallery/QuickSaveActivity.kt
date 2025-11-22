package com.example.memgallery

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
import javax.inject.Inject

@AndroidEntryPoint
class QuickSaveActivity : ComponentActivity() {

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
                handleQuickSave()
            } else {
                finish()
            }
        }
    }

    private fun handleQuickSave() {
        lifecycleScope.launch {
            try {
                val imageUri = if (intent.type?.startsWith("image/") == true) {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
                } else null

                val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                // Extract URL from text if present
                val urlRegex = "(https?://\\S+)".toRegex()
                val urlMatch = text?.let { urlRegex.find(it) }

                val bookmarkUrl: String?
                val userText: String?

                if (urlMatch != null) {
                    // URL found - extract it and get remaining text
                    bookmarkUrl = urlMatch.value
                    val remainingText = text.replace(bookmarkUrl, "").trim()
                    userText = if (remainingText.isBlank()) null else remainingText
                } else {
                    // No URL - treat as regular text
                    bookmarkUrl = null
                    userText = text
                }

                // Save to database
                memoryRepository.savePendingMemory(
                    imageUri = imageUri,
                    audioUri = null,
                    userText = userText,
                    bookmarkUrl = bookmarkUrl
                )

                runOnUiThread {
                    Toast.makeText(this@QuickSaveActivity, "Saved to MemGallery!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@QuickSaveActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
