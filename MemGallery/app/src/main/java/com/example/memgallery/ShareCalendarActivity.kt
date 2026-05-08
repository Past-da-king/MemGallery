package com.example.memgallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.memgallery.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareCalendarActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val enabled = settingsRepository.showInShareSheetFlow.first()
            if (!enabled) {
                finish()
                return@launch
            }

            if (intent?.action == Intent.ACTION_SEND) {
                handleCalendarShare()
            } else {
                finish()
            }
        }
    }

    private fun handleCalendarShare() {
        val eventTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val eventDescription = intent.getStringExtra(Intent.EXTRA_TEXT)

        val userText = listOfNotNull(eventTitle, eventDescription).joinToString("\n\n")

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain" // Treat it as a text input for PostCaptureScreen
            putExtra(Intent.EXTRA_TEXT, userText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        startActivity(mainIntent)
        finish()
    }
}

