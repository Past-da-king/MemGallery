package com.example.memgallery

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.example.memgallery.service.OverlayService

class ShortcutOverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if we have overlay permission
        if (Settings.canDrawOverlays(this)) {
            // Permission granted - start the overlay service
            startService(Intent(this, OverlayService::class.java))
        } else {
            // Permission denied - fallback to opening main app
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("shortcut_action", "add_memory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(mainIntent)
        }
        
        // Finish immediately without animation
        finish()
        overridePendingTransition(0, 0)
    }
}
