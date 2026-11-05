package com.example.memgallery.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.agent.AgentApiImpl
import com.example.memgallery.agent.AgentBridgeSettings
import com.example.memgallery.agent.AgentMappers
import com.example.memgallery.agent.model.AgentAsk
import com.example.memgallery.agent.model.AgentClient
import com.example.memgallery.agent.model.AgentScope
import com.example.memgallery.agent.model.CreateClient
import com.example.memgallery.agent.sync.MirrorSyncWorker
import com.example.memgallery.data.local.dao.AgentAskDao
import com.example.memgallery.data.local.dao.AgentAuditDao
import com.example.memgallery.data.local.dao.AgentClientDao
import com.example.memgallery.data.local.entity.AgentAuditEntity
import com.example.memgallery.service.AgentBridgeService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

data class AgentAccessState(
    val enabled: Boolean = false,
    val port: Int = AgentBridgeSettings.DEFAULT_PORT,
    val allowLan: Boolean = false,
    val aiProcessingAllowed: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    val clients: List<AgentClient> = emptyList(),
    val pendingAsks: List<AgentAsk> = emptyList(),
    val recentActivity: List<AgentAuditEntity> = emptyList(),
    val hubUrl: String = "",
    val hubToken: String = "",
    val mirrorEnabled: Boolean = false,
    val pushEnabled: Boolean = false,
    val pushTopic: String = "",
    val reachableAddress: String? = null,
    val error: String? = null,
    /** Non-null only immediately after minting. Shown once, then dropped — it is not recoverable. */
    val freshToken: String? = null
)

@HiltViewModel
class AgentAccessViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AgentApiImpl,
    private val settings: AgentBridgeSettings,
    private val clientDao: AgentClientDao,
    private val askDao: AgentAskDao,
    private val auditDao: AgentAuditDao
) : ViewModel() {

    private val _state = MutableStateFlow(AgentAccessState())
    val state: StateFlow<AgentAccessState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                enabled = settings.enabled.first(),
                port = settings.port.first(),
                allowLan = settings.allowLan.first(),
                aiProcessingAllowed = settings.aiProcessingAllowed.first(),
                quietHoursEnabled = settings.quietHoursEnabled.first(),
                quietStart = settings.quietStartHour.first(),
                quietEnd = settings.quietEndHour.first(),
                clients = clientDao.getAll().map { AgentMappers.toAgentClient(it) },
                pendingAsks = askDao.observePending().first().map { AgentMappers.toAgentAsk(it) },
                recentActivity = auditDao.observeRecent(20).first(),
                hubUrl = settings.hubUrl.first().orEmpty(),
                hubToken = settings.hubToken.first().orEmpty(),
                mirrorEnabled = settings.mirrorEnabled.first(),
                pushEnabled = settings.pushEnabled.first(),
                pushTopic = settings.pushTopic.first().orEmpty(),
                reachableAddress = bestAddress()
            )
        }
    }

    /**
     * Prefers the Tailscale address when there is one — that's the address the operator's
     * agents will actually be able to reach from elsewhere. A 192.168.x that only works on
     * the current wifi is a footgun to print in a pairing command.
     */
    private fun bestAddress(): String? = try {
        val addresses = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress ?: "" }
            .filter { it.isNotBlank() }
        addresses.firstOrNull { it.startsWith("100.") } ?: addresses.firstOrNull()
    } catch (e: Exception) {
        null
    }

    fun toggleBridge(enable: Boolean) {
        viewModelScope.launch {
            if (enable && !api.hasAnyClient()) {
                _state.value = _state.value.copy(
                    error = "Create a token first — the bridge will not bind without one."
                )
                return@launch
            }
            settings.setEnabled(enable)
            if (enable) AgentBridgeService.start(context) else AgentBridgeService.stop(context)
            _state.value = _state.value.copy(enabled = enable, error = null)
            refresh()
        }
    }

    fun createToken(name: String, scopes: Set<AgentScope>, dailyAiQuota: Int = 50) {
        viewModelScope.launch {
            val issued = api.issueToken(CreateClient(name = name, scopes = scopes, dailyAiQuota = dailyAiQuota))
            _state.value = _state.value.copy(freshToken = issued.token)
            refresh()
        }
    }

    fun dismissFreshToken() {
        _state.value = _state.value.copy(freshToken = null)
    }

    fun revoke(clientId: String) {
        viewModelScope.launch {
            clientDao.delete(clientId)
            refresh()
        }
    }

    fun setPort(port: Int) = viewModelScope.launch { settings.setPort(port); restartIfRunning() }
    fun setAllowLan(value: Boolean) = viewModelScope.launch { settings.setAllowLan(value); restartIfRunning() }
    fun setAiProcessing(value: Boolean) = viewModelScope.launch { settings.setAiProcessingAllowed(value); refresh() }

    fun setQuietHours(enabled: Boolean, start: Int, end: Int) = viewModelScope.launch {
        settings.setQuietHours(enabled, start, end)
        refresh()
    }

    fun setHub(url: String, token: String, mirror: Boolean) {
        viewModelScope.launch {
            settings.setHub(url.trim().trimEnd('/'), token.trim(), mirror)
            if (url.isNotBlank()) MirrorSyncWorker.schedule(context) else MirrorSyncWorker.cancel(context)
            refresh()
        }
    }

    fun setPush(enabled: Boolean, topic: String) {
        viewModelScope.launch {
            settings.setPush(enabled, topic.trim().ifBlank { null })
            restartIfRunning()
        }
    }

    fun answerAsk(id: String, answer: String) {
        viewModelScope.launch {
            runCatching { api.answerAsk(id, answer) }
            refresh()
        }
    }

    /** Settings that change how the socket binds only take effect on a rebind. */
    private suspend fun restartIfRunning() {
        if (settings.enabled.first()) {
            AgentBridgeService.stop(context)
            AgentBridgeService.start(context)
        }
        refresh()
    }

    fun pairingCommand(): String {
        val address = _state.value.reachableAddress ?: "<device-ip>"
        return "npx memgallery-mcp pair --url http://$address:${_state.value.port}"
    }
}
