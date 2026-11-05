package com.example.memgallery.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.agentDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_bridge")

/**
 * Everything the operator can turn on, off or dial about the Agent Bridge.
 *
 * Kept in its own DataStore file rather than bolted onto [com.example.memgallery.data.repository.SettingsRepository]:
 * the bridge is an opt-in subsystem, and a corrupt preferences file here should never be
 * able to take the rest of the app's settings with it.
 */
@Singleton
class AgentBridgeSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("bridge_enabled")
        val PORT = intPreferencesKey("bridge_port")
        val ALLOW_LAN = booleanPreferencesKey("bridge_allow_lan")
        val AI_PROCESSING = booleanPreferencesKey("bridge_ai_processing")
        val QUIET_HOURS = booleanPreferencesKey("bridge_quiet_hours")
        val QUIET_START = intPreferencesKey("bridge_quiet_start")
        val QUIET_END = intPreferencesKey("bridge_quiet_end")
        val DEVICE_NAME = stringPreferencesKey("bridge_device_name")
        val DEVICE_ID = stringPreferencesKey("bridge_device_id")
        val PUSH_ENABLED = booleanPreferencesKey("bridge_push_enabled")
        val PUSH_TOPIC = stringPreferencesKey("bridge_push_topic")
        val PUSH_SERVER = stringPreferencesKey("bridge_push_server")
        val HUB_URL = stringPreferencesKey("bridge_hub_url")
        val HUB_TOKEN = stringPreferencesKey("bridge_hub_token")
        val MIRROR_ENABLED = booleanPreferencesKey("bridge_mirror_enabled")
    }

    companion object {
        const val DEFAULT_PORT = 8790
        const val DEFAULT_PUSH_SERVER = "https://ntfy.sh"
    }

    val enabled: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.ENABLED] ?: false }
    val port: Flow<Int> = context.agentDataStore.data.map { it[Keys.PORT] ?: DEFAULT_PORT }

    /**
     * Off by default. When off, the server accepts only loopback and the Tailscale CGNAT
     * range 100.64.0.0/10 — the two places the operator actually controls. Turning this on
     * opens it to RFC1918, i.e. to every device on whatever café wifi he's joined.
     */
    val allowLan: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.ALLOW_LAN] ?: false }

    /** On by default: his keys, his agents. One tap stops agents spending AI quota. */
    val aiProcessingAllowed: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.AI_PROCESSING] ?: true }

    val quietHoursEnabled: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.QUIET_HOURS] ?: false }
    val quietStartHour: Flow<Int> = context.agentDataStore.data.map { it[Keys.QUIET_START] ?: 22 }
    val quietEndHour: Flow<Int> = context.agentDataStore.data.map { it[Keys.QUIET_END] ?: 7 }

    val deviceName: Flow<String> = context.agentDataStore.data.map {
        it[Keys.DEVICE_NAME] ?: android.os.Build.MODEL ?: "MemGallery"
    }

    val pushEnabled: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.PUSH_ENABLED] ?: false }
    val pushTopic: Flow<String?> = context.agentDataStore.data.map { it[Keys.PUSH_TOPIC] }
    val pushServer: Flow<String> = context.agentDataStore.data.map { it[Keys.PUSH_SERVER] ?: DEFAULT_PUSH_SERVER }

    val hubUrl: Flow<String?> = context.agentDataStore.data.map { it[Keys.HUB_URL]?.takeIf { u -> u.isNotBlank() } }
    val hubToken: Flow<String?> = context.agentDataStore.data.map { it[Keys.HUB_TOKEN]?.takeIf { t -> t.isNotBlank() } }
    val mirrorEnabled: Flow<Boolean> = context.agentDataStore.data.map { it[Keys.MIRROR_ENABLED] ?: false }

    suspend fun setEnabled(value: Boolean) = edit { it[Keys.ENABLED] = value }
    suspend fun setPort(value: Int) = edit { it[Keys.PORT] = value.coerceIn(1024, 65535) }
    suspend fun setAllowLan(value: Boolean) = edit { it[Keys.ALLOW_LAN] = value }
    suspend fun setAiProcessingAllowed(value: Boolean) = edit { it[Keys.AI_PROCESSING] = value }
    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) = edit {
        it[Keys.QUIET_HOURS] = enabled
        it[Keys.QUIET_START] = startHour.coerceIn(0, 23)
        it[Keys.QUIET_END] = endHour.coerceIn(0, 23)
    }
    suspend fun setDeviceName(value: String) = edit { it[Keys.DEVICE_NAME] = value }
    suspend fun setPush(enabled: Boolean, topic: String?, server: String = DEFAULT_PUSH_SERVER) = edit {
        it[Keys.PUSH_ENABLED] = enabled
        if (topic != null) it[Keys.PUSH_TOPIC] = topic
        it[Keys.PUSH_SERVER] = server
    }
    suspend fun setHub(url: String?, token: String?, mirror: Boolean) = edit {
        it[Keys.HUB_URL] = url.orEmpty()
        it[Keys.HUB_TOKEN] = token.orEmpty()
        it[Keys.MIRROR_ENABLED] = mirror
    }

    /** Stable per-install id, minted on first read. Identifies this device to the hub. */
    suspend fun deviceId(): String {
        val existing = context.agentDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        edit { it[Keys.DEVICE_ID] = fresh }
        return fresh
    }

    /** Handles the wrap-around case (22:00 → 07:00) that a naive range check gets wrong. */
    suspend fun isQuietNow(hourOfDay: Int): Boolean {
        if (!quietHoursEnabled.first()) return false
        val start = quietStartHour.first()
        val end = quietEndHour.first()
        return if (start <= end) hourOfDay in start until end else hourOfDay >= start || hourOfDay < end
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.agentDataStore.edit(block)
    }
}
