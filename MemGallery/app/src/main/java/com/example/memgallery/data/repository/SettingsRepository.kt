package com.example.memgallery.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "secure_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private object PreferencesKeys {
        val API_KEY_PLAINTEXT = stringPreferencesKey("api_key") // Old key for migration
        val AUTO_INDEX_SCREENSHOTS = booleanPreferencesKey("auto_index_screenshots")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_FILTER = stringPreferencesKey("notification_filter") // "ALL", "EVENTS", "TODOS"
        val SHOW_IN_SHARE_SHEET = booleanPreferencesKey("show_in_share_sheet")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val TASK_SCREEN_ENABLED = booleanPreferencesKey("task_screen_enabled")
        val DYNAMIC_THEMING_ENABLED = booleanPreferencesKey("dynamic_theming_enabled")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode") // "LIGHT", "DARK", "SYSTEM"
        val AMOLED_MODE_ENABLED = booleanPreferencesKey("amoled_mode_enabled")
        val SELECTED_COLOR = intPreferencesKey("selected_color")
        val SHOW_HIGHLIGHTS = booleanPreferencesKey("show_highlights")
        val USER_SYSTEM_PROMPT = stringPreferencesKey("user_system_prompt")
        val USER_CONTEXT_SUMMARY = stringPreferencesKey("user_context_summary")
        
        // Edge Gesture
        val EDGE_GESTURE_ENABLED = booleanPreferencesKey("edge_gesture_enabled")
        val EDGE_GESTURE_SIDE = stringPreferencesKey("edge_gesture_side") // "LEFT", "RIGHT"
        val EDGE_GESTURE_ACTION_SWIPE_UP = stringPreferencesKey("edge_gesture_action_swipe_up")
        val EDGE_GESTURE_ACTION_SWIPE_DOWN = stringPreferencesKey("edge_gesture_action_swipe_down")
        val EDGE_GESTURE_ACTION_DOUBLE_TAP = stringPreferencesKey("edge_gesture_action_double_tap")
        
        // Edge Gesture Appearance
        val EDGE_GESTURE_POSITION_Y = intPreferencesKey("edge_gesture_position_y") // 0-100 percentage
        val EDGE_GESTURE_HEIGHT_PERCENT = intPreferencesKey("edge_gesture_height_percent") // 10-100
        val EDGE_GESTURE_WIDTH = intPreferencesKey("edge_gesture_width") // dp
        val EDGE_GESTURE_DUAL_HANDLES = booleanPreferencesKey("edge_gesture_dual_handles")
        val EDGE_GESTURE_VISIBLE = booleanPreferencesKey("edge_gesture_visible")

        // Behavior
        val AUDIO_AUTO_START = booleanPreferencesKey("audio_auto_start")
        val POST_CAPTURE_BEHAVIOR = stringPreferencesKey("post_capture_behavior") // "BACKGROUND", "FOREGROUND"
        val AUTO_REMINDERS_ENABLED = booleanPreferencesKey("auto_reminders_enabled")
        val OVERLAY_STYLE = stringPreferencesKey("overlay_style") // "EDGE", "BALL"
    }

    init {
        // Run blocking is acceptable here as it's a one-time operation during app init
        // and needs to complete before the repository is used.
        runBlocking {
            migrateApiKeyIfNeeded()
        }
    }

    private suspend fun migrateApiKeyIfNeeded() {
        val oldApiKey = context.dataStore.data.map { it[PreferencesKeys.API_KEY_PLAINTEXT] }.first()
        if (!oldApiKey.isNullOrBlank()) {
            saveApiKey(oldApiKey) // Save to new encrypted prefs
            context.dataStore.edit { settings ->
                settings.remove(PreferencesKeys.API_KEY_PLAINTEXT) // Remove old plaintext key
            }
        }
    }
    
    val apiKeyFlow: Flow<String?> = flow {
        emit(encryptedPrefs.getString("api_key_secure", null))
    }

    suspend fun saveApiKey(apiKey: String) {
        with(encryptedPrefs.edit()) {
            putString("api_key_secure", apiKey)
            apply()
        }
    }

    suspend fun clearApiKey() {
        with(encryptedPrefs.edit()) {
            remove("api_key_secure")
            apply()
        }
    }

    val autoIndexScreenshotsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_INDEX_SCREENSHOTS] ?: false
        }

    suspend fun setAutoIndexScreenshots(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.AUTO_INDEX_SCREENSHOTS] = enabled
        }
    }

    // Notification Preferences
    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        }

    val notificationFilterFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_FILTER] ?: "ALL"
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationFilter(filter: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.NOTIFICATION_FILTER] = filter
        }
    }

    // Share Sheet Preferences
    val showInShareSheetFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_IN_SHARE_SHEET] ?: true
        }

    suspend fun setShowInShareSheet(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SHOW_IN_SHARE_SHEET] = enabled
        }
    }

    // Onboarding
    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    // Task Screen
    val taskScreenEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.TASK_SCREEN_ENABLED] ?: true
        }

    suspend fun setTaskScreenEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.TASK_SCREEN_ENABLED] = enabled
        }
    }
    
    // Highlights
    val showHighlightsFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_HIGHLIGHTS] ?: true
        }

    suspend fun setShowHighlights(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SHOW_HIGHLIGHTS] = enabled
        }
    }

    // Theme Preferences
    val dynamicThemingEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_THEMING_ENABLED] ?: true
        }

    suspend fun setDynamicThemingEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.DYNAMIC_THEMING_ENABLED] = enabled
        }
    }

    val appThemeModeFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.APP_THEME_MODE] ?: "SYSTEM"
        }

    suspend fun setAppThemeMode(mode: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.APP_THEME_MODE] = mode
        }
    }

    val amoledModeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AMOLED_MODE_ENABLED] ?: false
        }

    suspend fun setAmoledModeEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.AMOLED_MODE_ENABLED] = enabled
        }
    }

    val selectedColorFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_COLOR] ?: -1 // -1 indicates no custom color selected (use default)
        }

    suspend fun setSelectedColor(color: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SELECTED_COLOR] = color
        }
    }

    // User System Prompt
    val userSystemPromptFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_SYSTEM_PROMPT] ?: ""
        }

    suspend fun saveUserSystemPrompt(prompt: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.USER_SYSTEM_PROMPT] = prompt
        }
    }

    // User Context Summary
    val userContextSummaryFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_CONTEXT_SUMMARY] ?: ""
        }

    suspend fun saveUserContextSummary(summary: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.USER_CONTEXT_SUMMARY] = summary
        }
    }

    // Edge Gesture - Flows
    val edgeGestureEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EDGE_GESTURE_ENABLED] ?: false
        }

    val edgeGestureSideFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EDGE_GESTURE_SIDE] ?: "RIGHT"
        }

    val edgeGestureActionSwipeUpFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EDGE_GESTURE_ACTION_SWIPE_UP] ?: "NONE"
        }

    val edgeGestureActionSwipeDownFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EDGE_GESTURE_ACTION_SWIPE_DOWN] ?: "NONE"
        }

    val edgeGestureActionDoubleTapFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EDGE_GESTURE_ACTION_DOUBLE_TAP] ?: "NONE"
        }

    val edgeGesturePositionYFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EDGE_GESTURE_POSITION_Y] ?: 50 } // Center default

    val edgeGestureHeightPercentFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EDGE_GESTURE_HEIGHT_PERCENT] ?: 30 }

    val edgeGestureWidthFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EDGE_GESTURE_WIDTH] ?: 20 }

    val edgeGestureDualHandlesFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EDGE_GESTURE_DUAL_HANDLES] ?: false }

    val edgeGestureVisibleFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.EDGE_GESTURE_VISIBLE] ?: true }

    val audioAutoStartFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.AUDIO_AUTO_START] ?: true }

    val postCaptureBehaviorFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.POST_CAPTURE_BEHAVIOR] ?: "FOREGROUND" }

    // Edge Gesture - Setters
    suspend fun setEdgeGestureEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.EDGE_GESTURE_ENABLED] = enabled
        }
    }

    suspend fun setEdgeGestureSide(side: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.EDGE_GESTURE_SIDE] = side
        }
    }

    suspend fun setEdgeGestureActionSwipeUp(action: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.EDGE_GESTURE_ACTION_SWIPE_UP] = action
        }
    }

    suspend fun setEdgeGestureActionSwipeDown(action: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.EDGE_GESTURE_ACTION_SWIPE_DOWN] = action
        }
    }

    suspend fun setEdgeGestureActionDoubleTap(action: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.EDGE_GESTURE_ACTION_DOUBLE_TAP] = action
        }
    }

    suspend fun setEdgeGesturePositionY(percent: Int) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.EDGE_GESTURE_POSITION_Y] = percent }
    }

    suspend fun setEdgeGestureHeightPercent(percent: Int) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.EDGE_GESTURE_HEIGHT_PERCENT] = percent }
    }

    suspend fun setEdgeGestureWidth(widthDp: Int) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.EDGE_GESTURE_WIDTH] = widthDp }
    }

    suspend fun setEdgeGestureDualHandles(enabled: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.EDGE_GESTURE_DUAL_HANDLES] = enabled }
    }

    suspend fun setEdgeGestureVisible(visible: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.EDGE_GESTURE_VISIBLE] = visible }
    }

    suspend fun setAudioAutoStart(enabled: Boolean) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.AUDIO_AUTO_START] = enabled }
    }

    suspend fun setPostCaptureBehavior(behavior: String) {
        context.dataStore.edit { settings -> settings[PreferencesKeys.POST_CAPTURE_BEHAVIOR] = behavior }
    }

    val autoRemindersEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_REMINDERS_ENABLED] ?: true
        }

    suspend fun setAutoRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.AUTO_REMINDERS_ENABLED] = enabled
        }
    }

    val overlayStyleFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.OVERLAY_STYLE] ?: "EDGE"
        }

    suspend fun setOverlayStyle(style: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.OVERLAY_STYLE] = style
        }
    }
}
