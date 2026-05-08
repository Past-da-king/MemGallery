package com.example.memgallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class ProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract the selected text (could be EXTRA_PROCESS_TEXT or EXTRA_PROCESS_TEXT_READONLY)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getCharSequenceExtra("android.intent.extra.PROCESS_TEXT_READONLY")?.toString()

        if (!text.isNullOrBlank()) {
            // Open MainActivity with text using SEND action
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra("shared_text", text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(mainIntent)
        }

        finish()
    }
}
