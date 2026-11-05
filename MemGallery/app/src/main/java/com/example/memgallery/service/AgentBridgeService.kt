package com.example.memgallery.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.memgallery.R
import com.example.memgallery.agent.AgentBridgeSettings
import com.example.memgallery.agent.AgentEventBus
import com.example.memgallery.agent.AgentNotifier
import com.example.memgallery.agent.push.PushWakeManager
import com.example.memgallery.agent.server.AgentServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AgentBridgeService"

/**
 * Keeps the Agent Bridge alive.
 *
 * A long-lived listening socket cannot survive on Android outside a foreground service —
 * the process gets frozen and the socket dies silently, which looks to an agent exactly
 * like the phone being off. Hence the permanent minimum-importance notification: it is
 * the price of the bridge being reachable at all.
 *
 * The same service also holds the ntfy subscription, which is why that tier costs almost
 * nothing extra: the process is already up and already exempt.
 */
@AndroidEntryPoint
class AgentBridgeService : Service() {

    @Inject lateinit var server: AgentServer
    @Inject lateinit var notifier: AgentNotifier
    @Inject lateinit var settings: AgentBridgeSettings
    @Inject lateinit var events: AgentEventBus
    @Inject lateinit var pushWake: PushWakeManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_START = "com.example.memgallery.agent.START"
        const val ACTION_STOP = "com.example.memgallery.agent.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AgentBridgeService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AgentBridgeService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        // Android 12+ kills a foreground service that doesn't post its notification fast,
        // so this goes up before anything that can block.
        promote(getString(R.string.agent_bridge_starting))

        scope.launch {
            try {
                val port = server.start()
                val name = settings.deviceName.first()
                promote(getString(R.string.agent_bridge_status, name, port.toString()))
                events.prune()
                pushWake.start(scope)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the bridge", e)
                promote(getString(R.string.agent_bridge_failed, e.message ?: "unknown error"))
            }
        }

        // START_STICKY: if Android reclaims the process, bring the bridge back rather than
        // leaving the operator's agents silently unable to reach him.
        return START_STICKY
    }

    private fun promote(status: String) {
        val notification = notifier.bridgeNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AgentNotifier.BRIDGE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AgentNotifier.BRIDGE_NOTIFICATION_ID, notification)
        }
    }

    private fun shutdown() {
        runCatching { server.stop() }
        runCatching { pushWake.stop() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { server.stop() }
        runCatching { pushWake.stop() }
        scope.cancel()
        super.onDestroy()
    }
}
