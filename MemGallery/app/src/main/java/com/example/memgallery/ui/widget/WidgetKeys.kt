package com.example.memgallery.ui.widget

import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetKeys {
    val widgetDataKey = stringPreferencesKey("widget_pulse_data")
    val lastUpdateKey = stringPreferencesKey("widget_last_update")
    val themeColorKey = androidx.datastore.preferences.core.intPreferencesKey("widget_theme_color")
    val themeModeKey = stringPreferencesKey("widget_theme_mode")
}

data class WidgetMemoryItem(
    val id: Int,
    val title: String,
    val summary: String,
    val imagePath: String?,
    val type: String, // "MEMORY", "AUDIO", "CHAT", "Camera"
    val dueTime: String?, // "14:00"
    val formattedDateTime: String? = null // e.g. "Mon, 12 Oct • 14:00"
)
