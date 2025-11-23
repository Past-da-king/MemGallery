package com.example.memgallery.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val AUTO_INDEX_SCREENSHOTS = booleanPreferencesKey("auto_index_screenshots")
        val API_KEY = stringPreferencesKey("api_key")
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

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.API_KEY]
        }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.API_KEY] = apiKey
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { settings ->
            settings.remove(PreferencesKeys.API_KEY)
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
}