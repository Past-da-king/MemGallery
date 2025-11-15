package com.example.memgallery.data.repository

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(apiKey: String) {
        with(sharedPreferences.edit()) {
            putString(API_KEY, apiKey)
            apply()
        }
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(API_KEY, null)
    }

    fun clearApiKey() {
        with(sharedPreferences.edit()) {
            remove(API_KEY)
            apply()
        }
    }

    companion object {
        private const val API_KEY = "gemini_api_key"
    }
}
