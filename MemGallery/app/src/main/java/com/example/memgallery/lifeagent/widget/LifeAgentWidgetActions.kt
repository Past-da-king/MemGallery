package com.example.memgallery.lifeagent.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.example.memgallery.MainActivity
import com.example.memgallery.R
import com.example.memgallery.lifeagent.ui.LifeChatBridge
import com.example.memgallery.lifeagent.ui.LifeRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Widget taps talk to the app's own database only: nothing here writes to a connected app. */
object LifeWidgetKeys {
    val taskId = ActionParameters.Key<String>("life_task_id")
    val proposalId = ActionParameters.Key<String>("life_proposal_id")
    val snooze = ActionParameters.Key<String>("life_snooze")
    val route = ActionParameters.Key<String>("life_route")
    val itemId = ActionParameters.Key<String>("life_item_id")
    val itemTitle = ActionParameters.Key<String>("life_item_title")
    val snapshot = "life_agent_snapshot"
}

object LifeAgentWidgetRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Re-reads the database and redraws every placed widget. Never touches the network. */
    fun enqueue(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { LifeAgentWidget().updateAll(app) } }
    }
}

private fun LifeAgentWidgetEntryPoint.repo() = repository()

/** Tap: mark the local task done. The tick is local only; the source app is never written to. */
class LifeWidgetDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LifeWidgetKeys.taskId] ?: return
        runCatching { LifeAgentWidgets.entry(context).repo().markDone(id) }
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** Tap: snooze one hour or until 08:00 tomorrow, in the user's own zone. */
class LifeWidgetSnoozeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LifeWidgetKeys.taskId] ?: return
        val now = System.currentTimeMillis()
        val until = if (parameters[LifeWidgetKeys.snooze] == "tomorrow") {
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
                .plusDays(1).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            now + 60L * 60 * 1000
        }
        runCatching { LifeAgentWidgets.entry(context).repo().snooze(id, until) }
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** Tap: keep the proposed memory (it becomes a memory, and a local task when it has a date). */
class LifeWidgetKeepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LifeWidgetKeys.proposalId] ?: return
        runCatching { LifeAgentWidgets.entry(context).repo().keep(id) }
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** Tap: drop the proposed memory. The agent learns from it. */
class LifeWidgetDropAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LifeWidgetKeys.proposalId] ?: return
        runCatching { LifeAgentWidgets.entry(context).repo().drop(id) }
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** Tap: redraw now. */
class LifeWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** Tap "Ask": opens the app's chat with the item as the question, the same way the screens do. */
class LifeWidgetAskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val title = parameters[LifeWidgetKeys.itemTitle].orEmpty()
        val question = if (title.isBlank()) {
            context.getString(R.string.la_widget_ask_plain)
        } else {
            context.getString(R.string.la_widget_ask_item, title)
        }
        LifeChatBridge.ask(question)
        openApp(context, LifeRoutes.Report)
    }
}

/** Tap a row: open the app on the screen that owns it (daily report or review deck). */
class LifeWidgetOpenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val route = parameters[LifeWidgetKeys.route] ?: LifeRoutes.Report
        openApp(context, route)
    }
}

private fun openApp(context: Context, route: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_NAVIGATE_TO, route)
    }
    runCatching { context.startActivity(intent) }
}
