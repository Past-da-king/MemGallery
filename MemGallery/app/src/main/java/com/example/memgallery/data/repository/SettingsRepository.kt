package com.example.memgallery.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
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
}