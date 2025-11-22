package com.example.memgallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenInAppActivity : ComponentActivity() {

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
                forwardToMainActivity()
            } else {
                finish()
            }
        }
    }

    private fun forwardToMainActivity() {
        val imageUri = if (intent.type?.startsWith("image/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } else null

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)

        // Forward to MainActivity which will handle routing to PostCaptureScreen
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = intent.type
            if (text != null) {
                putExtra(Intent.EXTRA_TEXT, text)
            }
            if (imageUri != null) {
                putExtra(Intent.EXTRA_STREAM, imageUri)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        startActivity(mainIntent)
        finish()
    }
}
