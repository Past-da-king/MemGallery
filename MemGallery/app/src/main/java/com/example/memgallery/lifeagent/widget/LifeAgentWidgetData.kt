package com.example.memgallery.lifeagent.widget

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import com.example.memgallery.lifeagent.data.DailyReport
import com.example.memgallery.lifeagent.data.LifeAgentRepository
import com.example.memgallery.lifeagent.data.LifeApp
import com.example.memgallery.lifeagent.data.LifeProposal
import com.example.memgallery.lifeagent.data.ReportItem
import com.example.memgallery.data.repository.SettingsRepository
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the widget has to ask Hilt for, since a Glance widget is not an injected component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LifeAgentWidgetEntryPoint {
    fun repository(): LifeAgentRepository
    fun settings(): SettingsRepository
}

object LifeAgentWidgets {
    fun entry(context: Context): LifeAgentWidgetEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, LifeAgentWidgetEntryPoint::class.java)

    /** Re-draws every placed widget from the database. Cheap: no network, no collection. */
    fun refresh(context: Context) {
        LifeAgentWidgetRefresh.enqueue(context)
    }
}

/** One row the widget draws. [kind] is overdue, due, meeting, open or proposal. */
data class LifeWidgetItem(
    val id: String = "",
    val title: String = "",
    val whenLabel: String = "",
    val kind: String = "open",
    /** video, calendar, mail, clock, money, flag, document, person, security */
    val icon: String = "clock",
    val isTask: Boolean = false,
    val source: String? = null,
) {
    val isOverdue: Boolean get() = kind == "overdue"
}

/**
 * Everything one widget draw needs, as one JSON blob in the widget's own state. Built by [LifeAgentWidgetData.build],
 * which is pure so the layout choices can be unit tested without a database.
 */
data class LifeWidgetSnapshot(
    val empty: Boolean = true,
    val dateLabel: String = "",
    val dueToday: Int = 0,
    val overdue: Int = 0,
    val aware: Int = 0,
    val toReview: Int = 0,
    val nextMeeting: LifeWidgetItem? = null,
    val overdueItems: List<LifeWidgetItem> = emptyList(),
    val dueItems: List<LifeWidgetItem> = emptyList(),
    val meetingItems: List<LifeWidgetItem> = emptyList(),
    val openItems: List<LifeWidgetItem> = emptyList(),
    val proposal: LifeWidgetItem? = null,
    val proposalSource: String? = null,
    val themeMode: String = "SYSTEM",
    val amoled: Boolean = false,
    val dynamic: Boolean = true,
    val accent: Int = -1,
) {
    fun toJson(): String = Gson().toJson(this)

    /** Rows in the order the larger layouts show them: late first, then due, meetings, open. */
    val rows: List<LifeWidgetItem>
        get() = (overdueItems + dueItems + meetingItems + openItems)

    companion object {
        val Start = LifeWidgetSnapshot()

        fun fromJson(json: String?): LifeWidgetSnapshot {
            if (json.isNullOrBlank()) return Start
            val parsed = runCatching { Gson().fromJson(json, LifeWidgetSnapshot::class.java) }.getOrNull() ?: return Start
            // Gson does not run the constructor, so a snapshot written by an older build can hold nulls.
            return parsed.copy(
                overdueItems = parsed.overdueItems ?: emptyList(),
                dueItems = parsed.dueItems ?: emptyList(),
                meetingItems = parsed.meetingItems ?: emptyList(),
                openItems = parsed.openItems ?: emptyList(),
            )
        }
    }
}

/** Loads and shapes the snapshot. [build] is pure and unit tested. */
object LifeAgentWidgetData {

    suspend fun load(context: Context, now: Long = System.currentTimeMillis()): LifeWidgetSnapshot {
        val entry = LifeAgentWidgets.entry(context)
        val repo = entry.repository()
        val settings = entry.settings()
        val zone = ZoneId.systemDefault()
        val report = runCatching { repo.dailyReport().first() }.getOrNull()
        val proposals = runCatching { repo.proposals.first() }.getOrDefault(emptyList())
        val themeMode = runCatching { settings.appThemeModeFlow.first() }.getOrDefault("SYSTEM")
        val amoled = runCatching { settings.amoledModeEnabledFlow.first() }.getOrDefault(false)
        val dynamic = runCatching { settings.dynamicThemingEnabledFlow.first() }.getOrDefault(true)
        val accent = runCatching { settings.selectedColorFlow.first() }.getOrDefault(-1)
        return build(report, proposals, now, zone, themeMode, amoled, dynamic, accent)
    }

    fun build(
        report: DailyReport?,
        proposals: List<LifeProposal>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        themeMode: String = "SYSTEM",
        amoled: Boolean = false,
        dynamic: Boolean = true,
        accent: Int = -1,
        maxRows: Int = 6,
    ): LifeWidgetSnapshot {
        val dateLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
            .format(Instant.ofEpochMilli(now).atZone(zone))
        val proposal = proposals.firstOrNull()?.let { p ->
            LifeWidgetItem(
                id = p.id,
                title = p.title,
                whenLabel = whenLabel(p.dueAt, now, zone),
                kind = "proposal",
                icon = iconFor(p.kind.key),
                isTask = false,
                source = LifeApp.from(p.app)?.label ?: p.app,
            )
        }
        val source = proposals.firstOrNull()?.let { p -> LifeApp.from(p.app)?.label ?: p.app }
        if (report == null) {
            return LifeWidgetSnapshot(
                empty = true, dateLabel = dateLabel, proposal = proposal, proposalSource = source,
                themeMode = themeMode, amoled = amoled, dynamic = dynamic, accent = accent,
            )
        }
        val overdueItems = report.overdue.take(maxRows).map { it.toWidget(now, zone, "overdue") }
        val dueItems = report.dueToday.take(maxRows).map { it.toWidget(now, zone, "due") }
        val meetingItems = report.meetings
            .filter { it.endAt >= now }
            .take(maxRows)
            .map {
                LifeWidgetItem(
                    id = it.id, title = it.title, whenLabel = whenLabel(it.startAt, now, zone),
                    kind = "meeting", icon = "video", isTask = false, source = it.joinUrl,
                )
            }
        val openItems = report.open.take(maxRows).map { it.toWidget(now, zone, "open") }
        return LifeWidgetSnapshot(
            empty = overdueItems.isEmpty() && dueItems.isEmpty() && meetingItems.isEmpty() && openItems.isEmpty() && proposal == null,
            dateLabel = dateLabel,
            dueToday = report.dueToday.size,
            overdue = report.overdue.size,
            aware = report.aware.size,
            toReview = report.toReview,
            nextMeeting = meetingItems.firstOrNull(),
            overdueItems = overdueItems,
            dueItems = dueItems,
            meetingItems = meetingItems,
            openItems = openItems,
            proposal = proposal,
            proposalSource = source,
            themeMode = themeMode, amoled = amoled, dynamic = dynamic, accent = accent,
        )
    }

    private fun ReportItem.toWidget(now: Long, zone: ZoneId, kind: String) = LifeWidgetItem(
        id = id, title = title, whenLabel = whenLabel(dueAt, now, zone), kind = kind,
        icon = if (isTask) "clock" else "flag", isTask = isTask, source = source,
    )

    private fun iconFor(kind: String) = when (kind) {
        "money" -> "money"
        "meeting", "event" -> "calendar"
        "document" -> "document"
        "person" -> "person"
        "security" -> "security"
        "awaiting" -> "mail"
        else -> "flag"
    }

    /** "16:00 tomorrow", "2 days late", "Fri 13 Nov". Empty when there is no date. */
    fun whenLabel(at: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (at == null) return ""
        val whenAt = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = whenAt.toLocalDate()
        val time = whenAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        val daysLate = ChronoUnit.DAYS.between(day, today)
        return when {
            at < now -> when {
                daysLate <= 0L -> "was $time"
                daysLate == 1L -> "1 day late"
                else -> "$daysLate days late"
            }
            day == today -> "$time today"
            day == today.plusDays(1) -> "$time tomorrow"
            else -> whenAt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
    }
}
