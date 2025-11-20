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

            when (intent?.action) {
                Intent.ACTION_SEND -> handleShare()
                else -> finish()
            }
        }
    }

    private fun handleShare() {
        lifecycleScope.launch {
            try {
                val imageUri = if (intent.type?.startsWith("image/") == true) {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
                } else null

                val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                // Save to database (will auto-trigger processing via MemoryProcessingWorker)
                memoryRepository.savePendingMemory(
                    imageUri = imageUri,
                    audioUri = null,
                    userText = text
                )

                runOnUiThread {
                    Toast.makeText(this@ShareActivity, "Saved to MemGallery!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ShareActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
