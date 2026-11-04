package com.example.memgallery.agent.push

import android.util.Log
import com.example.memgallery.agent.AgentBridgeSettings
import com.example.memgallery.agent.sync.HubSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "PushWakeManager"

/**
 * Tier 2 of reaching the phone: the doorbell.
 *
 * When the phone isn't on Tailscale an agent can't open a connection to it, so it writes
 * the command to the hub and rings this bell instead. The phone wakes, dials *out* to the
 * hub, drains the queue and posts results back — which works on cellular, hotel wifi, and
 * anything else that allows outbound HTTPS.
 *
 * The bell is public ntfy. The payload is **an opaque wake-ping and nothing else** — no
 * titles, no bodies, no ids. A third-party broker learns only that something happened,
 * never what. Self-hosting the broker was considered and rejected: a broker on the
 * operator's own machine is unreachable in exactly the situation this tier exists for.
 *
 * Two ways in, in order of preference:
 *  1. **UnifiedPush** — if an ntfy distributor app is installed it wakes the app for us,
 *     at zero battery cost. Registration is handled by [UnifiedPushReceiver].
 *  2. **This websocket** — held open by the bridge foreground service. The service is
 *     already running to keep the HTTP server alive, so the socket is nearly free.
 *
 * Underneath both sits the WorkManager poll, which never fails and never hurries.
 */
@Singleton
class PushWakeManager @Inject constructor(
    private val settings: AgentBridgeSettings,
    private val hubSync: HubSync
) {
    private var socket: WebSocket? = null
    private var job: Job? = null
    private var attempt = 0

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // ntfy sends a keepalive roughly every 45s; 75s tolerates one miss without
            // tearing down a healthy connection.
            .readTimeout(75, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!settings.pushEnabled.first()) {
                Log.d(TAG, "Push wake is off; relying on the poll floor.")
                return@launch
            }
            val topic = settings.pushTopic.first()
            if (topic.isNullOrBlank()) {
                Log.w(TAG, "Push enabled but no topic configured.")
                return@launch
            }
            val server = settings.pushServer.first().trimEnd('/')
            connectLoop(server, topic, scope)
        }
    }

    private suspend fun connectLoop(server: String, topic: String, scope: CoroutineScope) {
        while (scope.isActiveSafe()) {
            connect(server, topic)
            // Exponential backoff capped at five minutes. A tight reconnect loop against a
            // public broker gets the topic rate-limited, which breaks the tier entirely.
            val backoff = min(300_000.0, 2.0.pow(attempt.coerceAtMost(8)) * 1000).toLong()
            delay(backoff)
            attempt++
        }
    }

    private fun CoroutineScope.isActiveSafe(): Boolean = coroutineContext[Job]?.isActive != false

    private fun connect(server: String, topic: String) {
        val url = "${server.replace("https://", "wss://").replace("http://", "ws://")}/$topic/ws"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Wake channel open")
                attempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Contents are deliberately ignored. The ping means "there is something
                // waiting at the hub", and the hub is the only thing we trust to say what.
                Log.d(TAG, "Wake ping received")
                hubSync.requestDrain("push")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Wake channel failed: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Wake channel closed: $code $reason")
            }
        })
    }

    fun stop() {
        runCatching { socket?.close(1000, "stopping") }
        socket = null
        job?.cancel()
        job = null
    }
}
