package com.example.memgallery.lifeagent.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.data.repository.SettingsRepository
import com.example.memgallery.lifeagent.data.LifeAgentDao
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import com.example.memgallery.lifeagent.data.LifeTaskEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One notification the planner wants posted. [key] is what makes it once-only. */
data class Nudge(val key: String, val taskId: String, val title: String, val dueAt: Long, val overdue: Boolean)

/** Pure: which open items need a nudge now, minus those already nudged. Unit tested. */
object NudgePlanner {
    const val WINDOW_MS = 24L * 60 * 60 * 1000

    fun plan(tasks: List<LifeTaskEntity>, now: Long, alreadyNudged: Set<String>): List<Nudge> =
        tasks.asSequence()
            .filter { !LifeAgentRepository.isClosed(it.status) && !it.doneLocal && it.dueAt != null }
            .filter { (it.snoozedUntil ?: 0L) <= now }
            .mapNotNull { t ->
                val due = t.dueAt!!
                val overdue = due < now
                if (!overdue && due - now > WINDOW_MS) return@mapNotNull null
                // Snoozing and letting the snooze pass earns one more nudge: the snooze time is part of the key.
                val key = "${t.id}|${if (overdue) "overdue" else "due"}|$due|${t.snoozedUntil ?: 0L}"
                if (key in alreadyNudged) null else Nudge(key, t.id, t.title, due, overdue)
            }
            .sortedBy { it.dueAt }
            .toList()
}

/**
 * Hourly check (and after every collection): one notification per item that is due within 24 hours or overdue,
 * never twice for the same state. Respects the app's notification switch and the POST_NOTIFICATIONS permission.
 * Tapping opens MainActivity with [ACTION_OPEN_ITEM] and [EXTRA_ITEM_ID] for the UI to route to the item.
 */
@HiltWorker
class NudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: LifeAgentDao,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.lifeAgentEnabledFlow.first() || !settings.notificationsEnabledFlow.first()) return Result.success()
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val now = System.currentTimeMillis()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().mapNotNull { e ->
            e.substringBeforeLast('@', "").takeIf { it.isNotEmpty() }?.let { it to e.substringAfterLast('@').toLongOrNull() }
        }.filter { (_, at) -> at != null && now - at < 14L * 24 * 3600 * 1000 }.toMap()

        val nudges = NudgePlanner.plan(dao.getTasks(), now, sent.keys)
        if (nudges.isEmpty()) return Result.success()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm, ctx)
        val updated = sent.entries.map { "${it.key}@${it.value}" }.toMutableSet()
        for (n in nudges.take(MAX_PER_RUN)) {
            nm.notify(n.key.hashCode(), build(ctx, n))
            updated += "${n.key}@$now"
        }
        prefs.edit().putStringSet(KEY_SENT, updated).apply()
        return Result.success()
    }

    private fun build(ctx: Context, n: Nudge) = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(ctx.applicationInfo.icon)
        .setContentTitle(ctx.getString(if (n.overdue) R.string.la_nudge_title_overdue else R.string.la_nudge_title_due))
        .setContentText(ctx.getString(if (n.overdue) R.string.la_nudge_text_overdue else R.string.la_nudge_text_due, n.title, whenText(n.dueAt)))
        .setStyle(NotificationCompat.BigTextStyle())
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .addAction(
            0,
            ctx.getString(R.string.la_nudge_action_done),
            PendingIntent.getBroadcast(
                ctx, n.key.hashCode() * 31 + 1,
                Intent(ctx, NudgeActionReceiver::class.java).apply {
                    action = ACTION_DONE
                    putExtra(EXTRA_ITEM_ID, n.taskId)
                    putExtra(EXTRA_NOTIFICATION_ID, n.key.hashCode())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            ctx.getString(R.string.la_nudge_action_snooze),
            PendingIntent.getBroadcast(
                ctx, n.key.hashCode() * 31 + 2,
                Intent(ctx, NudgeActionReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_ITEM_ID, n.taskId)
                    putExtra(EXTRA_NOTIFICATION_ID, n.key.hashCode())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setContentIntent(
            PendingIntent.getActivity(
                ctx, n.key.hashCode(),
                Intent(ctx, MainActivity::class.java).apply {
                    action = ACTION_OPEN_ITEM
                    putExtra(EXTRA_ITEM_ID, n.taskId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun whenText(at: Long): String =
        DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

    private fun ensureChannel(nm: NotificationManager, ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.la_nudge_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.la_nudge_channel_desc) },
        )
    }

    companion object {
        const val CHANNEL = "life_agent_nudges"
        const val ACTION_OPEN_ITEM = "com.example.memgallery.lifeagent.OPEN_ITEM"
        const val EXTRA_ITEM_ID = "life_agent_item_id"
        const val EXTRA_NOTIFICATION_ID = "life_agent_notification_id"
        const val ACTION_DONE = "com.example.memgallery.lifeagent.NUDGE_DONE"
        const val ACTION_SNOOZE = "com.example.memgallery.lifeagent.NUDGE_SNOOZE"
        private const val PREFS = "life_agent_nudges"
        private const val KEY_SENT = "sent"
        private const val MAX_PER_RUN = 5
    }
}
