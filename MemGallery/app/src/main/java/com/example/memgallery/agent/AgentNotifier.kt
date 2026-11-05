package com.example.memgallery.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.agent.model.AgentAsk
import com.example.memgallery.agent.model.AskKind
import com.example.memgallery.agent.model.NotifyPriority
import com.example.memgallery.agent.model.NotifyRequest
import com.example.memgallery.receiver.AgentActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Everything that puts an agent in front of the operator.
 *
 * Two channels, deliberately separate from MemGallery's own reminders: he can mute
 * chatty agents at 2am without also muting the alarm that tells him about tomorrow's
 * meeting. That separation is the difference between agent notifications being useful
 * and being turned off entirely.
 */
@Singleton
class AgentNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AgentBridgeSettings
) {
    companion object {
        const val CHANNEL_ALERTS = "agent_alerts"
        const val CHANNEL_ASKS = "agent_asks"
        const val CHANNEL_BRIDGE = "agent_bridge_status"

        const val EXTRA_KIND = "agent_kind"
        const val EXTRA_ASK_ID = "agent_ask_id"
        const val EXTRA_ACTION_ID = "agent_action_id"
        const val EXTRA_NOTIFICATION_ID = "agent_notification_id"
        const val EXTRA_CLIENT = "agent_client"
        const val KIND_ASK_ANSWER = "ask_answer"
        const val KIND_NOTIFY_ACTION = "notify_action"
        const val REMOTE_INPUT_KEY = "agent_reply"

        /** Foreground-service notification for the bridge itself. Fixed id: only ever one. */
        const val BRIDGE_NOTIFICATION_ID = 47_001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_agent_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_channel_agent_alerts_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASKS,
                context.getString(R.string.notif_channel_agent_asks_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_agent_asks_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BRIDGE,
                context.getString(R.string.notif_channel_agent_bridge_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = context.getString(R.string.notif_channel_agent_bridge_desc) }
        )
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Quiet hours drop the *sound*, never the notification. An agent that had something
     * worth saying at 3am still said it; he just finds it in the morning instead of
     * being woken. Urgent overrides — that's what urgent is for.
     */
    private suspend fun effectivePriority(requested: NotifyPriority): NotifyPriority {
        if (requested == NotifyPriority.URGENT) return requested
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (settings.isQuietNow(hour)) NotifyPriority.LOW else requested
    }

    private fun androidPriority(priority: NotifyPriority): Int = when (priority) {
        NotifyPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotifyPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        NotifyPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        NotifyPriority.URGENT -> NotificationCompat.PRIORITY_MAX
    }

    private fun contentIntent(notificationId: Int, deepLink: String?): PendingIntent {
        val intent = if (!deepLink.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(context.packageName)
        } else {
            Intent(context, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun actionIntent(
        notificationId: Int,
        kind: String,
        actionId: String,
        askId: String? = null,
        clientName: String? = null,
        withRemoteInput: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, AgentActionReceiver::class.java).apply {
            action = "${context.packageName}.AGENT_ACTION.$notificationId.$actionId"
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ACTION_ID, actionId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            askId?.let { putExtra(EXTRA_ASK_ID, it) }
            clientName?.let { putExtra(EXTRA_CLIENT, it) }
        }
        // RemoteInput needs a mutable PendingIntent so the system can fill in the reply.
        val flags = if (withRemoteInput) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, notificationId * 31 + actionId.hashCode(), intent, flags)
    }

    /** Fire-and-forget agent notification. Returns the id it was posted under. */
    suspend fun notify(request: NotifyRequest, clientName: String): Int {
        val id = request.tag?.let { stableId(it) } ?: nextId()
        if (!canPost()) return id

        val priority = effectivePriority(request.priority)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(request.title)
            .setContentText(request.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.body))
            .setSubText(clientName)
            .setPriority(androidPriority(priority))
            .setContentIntent(contentIntent(id, request.deepLink))
            .setAutoCancel(true)
            .setOnlyAlertOnce(priority == NotifyPriority.LOW)

        request.actions.take(3).forEach { action ->
            builder.addAction(
                0,
                action.label,
                actionIntent(id, KIND_NOTIFY_ACTION, action.id, clientName = clientName)
            )
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
        return id
    }

    /**
     * Posts a question. `confirm` and `choice` become buttons in the shade; `text` gets a
     * RemoteInput field, so the operator can answer without unlocking the phone — which is
     * the entire point of the feature.
     */
    fun postAsk(ask: AgentAsk, notificationId: Int) {
        if (!canPost()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ASKS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ask.question)
            .setContentText(ask.context ?: context.getString(R.string.agent_ask_default_body, ask.askedBy))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(listOfNotNull(ask.context, context.getString(R.string.agent_ask_asked_by, ask.askedBy)).joinToString("\n\n"))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(contentIntent(notificationId, null))
            .setAutoCancel(false)
            .setOngoing(false)

        when (ask.kind) {
            AskKind.CONFIRM -> {
                builder.addAction(0, context.getString(R.string.agent_ask_yes), answerIntent(notificationId, ask.id, "yes"))
                builder.addAction(0, context.getString(R.string.agent_ask_no), answerIntent(notificationId, ask.id, "no"))
            }
            AskKind.CHOICE -> {
                ask.options.take(3).forEach { option ->
                    builder.addAction(0, option, answerIntent(notificationId, ask.id, option))
                }
            }
            AskKind.TEXT -> {
                val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
                    .setLabel(context.getString(R.string.agent_ask_reply_hint))
                    .build()
                val action = NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.agent_ask_reply),
                    actionIntent(notificationId, KIND_ASK_ANSWER, "reply", ask.id, withRemoteInput = true)
                )
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .build()
                builder.addAction(action)
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun answerIntent(notificationId: Int, askId: String, answer: String): PendingIntent =
        actionIntent(notificationId, KIND_ASK_ANSWER, answer, askId)

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /** The persistent, minimum-importance notification the foreground service requires. */
    fun bridgeNotification(status: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_BRIDGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.agent_bridge_running))
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent(BRIDGE_NOTIFICATION_ID, null))
            .build()

    /** Ids live above the memory-id space so agent posts can never collide with memory posts. */
    private fun nextId(): Int = 100_000 + (System.currentTimeMillis() % 800_000).toInt()

    private fun stableId(tag: String): Int = 100_000 + abs(tag.hashCode() % 800_000)
}
