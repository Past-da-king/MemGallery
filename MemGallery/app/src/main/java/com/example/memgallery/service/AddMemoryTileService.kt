package com.example.memgallery.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.example.memgallery.ShortcutOverlayActivity

class AddMemoryTileService : TileService() {

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        
        // Create an intent to launch our transparent overlay activity
        val intent = Intent(this, ShortcutOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // startActivityAndCollapse does two things:
        // 1. Launches the activity (which starts the overlay service)
        // 2. Collapses the Quick Settings panel/Notification shade so you see the overlay immediately
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (UpsideDownCake) and above
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
