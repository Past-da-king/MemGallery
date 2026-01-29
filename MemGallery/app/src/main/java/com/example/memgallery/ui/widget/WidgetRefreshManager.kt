package com.example.memgallery.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetRefreshManager {
    fun refreshWidget(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MemoryPulseWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
