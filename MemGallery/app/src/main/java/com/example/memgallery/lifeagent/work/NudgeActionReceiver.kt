package com.example.memgallery.lifeagent.work

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Done and Snooze buttons on a nudge. Both are local: the task row in MemGallery is marked done or pushed an hour
 * down, and nothing is written back to Gmail, Calendar, ClickUp or Slack.
 */
@AndroidEntryPoint
class NudgeActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: LifeAgentRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getStringExtra(NudgeWorker.EXTRA_ITEM_ID) ?: return
        val notifId = intent.getIntExtra(NudgeWorker.EXTRA_NOTIFICATION_ID, id.hashCode())
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    NudgeWorker.ACTION_DONE -> repository.markDone(id)
                    NudgeWorker.ACTION_SNOOZE -> repository.snooze(id, System.currentTimeMillis() + 60L * 60 * 1000)
                }
                NotificationManagerCompat.from(app).cancel(notifId)
            } catch (e: Exception) {
                // Nothing to retry: the row may already be gone.
            } finally {
                pending.finish()
            }
        }
    }
}
