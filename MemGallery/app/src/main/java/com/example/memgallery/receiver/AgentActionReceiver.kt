package com.example.memgallery.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.memgallery.agent.AgentApiImpl
import com.example.memgallery.agent.AgentEventBus
import com.example.memgallery.agent.AgentNotifier
import com.example.memgallery.agent.model.AgentEventType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AgentActionReceiver"

/**
 * Turns a tap in the notification shade into an answer the waiting agent receives.
 *
 * This is the closing half of the ask loop, and the reason `POST /v1/ask` is worth
 * anything: the operator answers from the lock screen, and the agent's long-poll returns
 * within half a second — no app launch, no unlock.
 */
@AndroidEntryPoint
class AgentActionReceiver : BroadcastReceiver() {

    @Inject lateinit var api: AgentApiImpl
    @Inject lateinit var events: AgentEventBus
    @Inject lateinit var notifier: AgentNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AgentNotifier.EXTRA_KIND) ?: return
        val actionId = intent.getStringExtra(AgentNotifier.EXTRA_ACTION_ID).orEmpty()
        val notificationId = intent.getIntExtra(AgentNotifier.EXTRA_NOTIFICATION_ID, -1)

        // The broadcast returns immediately; the DB write outlives it, so hold the wake
        // lock explicitly rather than racing the process being frozen.
        val pending = goAsync()

        scope.launch {
            try {
                when (kind) {
                    AgentNotifier.KIND_ASK_ANSWER -> {
                        val askId = intent.getStringExtra(AgentNotifier.EXTRA_ASK_ID)
                        if (askId != null) {
                            val typed = RemoteInput.getResultsFromIntent(intent)
                                ?.getCharSequence(AgentNotifier.REMOTE_INPUT_KEY)
                                ?.toString()
                                ?.trim()
                            val answer = typed?.takeIf { it.isNotEmpty() } ?: actionId
                            api.answerAsk(askId, answer)
                            Log.i(TAG, "Ask $askId answered: $answer")
                        }
                    }

                    AgentNotifier.KIND_NOTIFY_ACTION -> {
                        if (notificationId >= 0) notifier.cancel(notificationId)
                        events.emit(
                            AgentEventType.NOTIFICATION_ACTION,
                            notificationId.toString(),
                            mapOf(
                                "notification_id" to notificationId,
                                "action_id" to actionId,
                                "client" to intent.getStringExtra(AgentNotifier.EXTRA_CLIENT).orEmpty()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle agent notification action", e)
            } finally {
                pending.finish()
            }
        }
    }
}
