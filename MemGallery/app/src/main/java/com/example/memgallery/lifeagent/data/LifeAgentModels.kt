package com.example.memgallery.lifeagent.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** The four apps v1 can read, through the user's own Composio account. READ ONLY. */
enum class LifeApp(val id: String, val label: String) {
    GMAIL("gmail", "Gmail"),
    GCAL("gcal", "Google Calendar"),
    CLICKUP("clickup", "ClickUp"),
    SLACK("slack", "Slack");

    companion object {
        fun from(id: String?): LifeApp? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** What a proposed memory is about. [key] is what Room stores. */
enum class LifeKind(val key: String, val label: String) {
    PERSON("person", "Person"),
    COMMITMENT("commitment", "Commitment"),
    DEADLINE("deadline", "Deadline"),
    DECISION("decision", "Decision"),
    MONEY("money", "Money"),
    EVENT("event", "Event"),
    DOCUMENT("document", "Document"),
    SECURITY("security", "Security"),
    AWAITING("awaiting", "Awaiting"),
    OTHER("other", "Other");

    companion object {
        fun from(key: String?): LifeKind = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OTHER
    }
}

enum class ProposalStatus(val key: String) {
    PROPOSED("proposed"), KEPT("kept"), DROPPED("dropped");

    companion object {
        fun from(key: String?): ProposalStatus = entries.firstOrNull { it.key == key } ?: PROPOSED
    }
}

data class LifeConnection(
    val id: String,
    val label: String,
    val connected: Boolean,
    val accountLabel: String?,
    val lastPulledAt: Long,
    val lastError: String?,
) {
    val app: LifeApp? get() = LifeApp.from(id)
}

data class LifeProposal(
    val id: String,
    val kind: LifeKind,
    val title: String,
    val detail: String,
    val app: String,
    val sourceId: String,
    val sourceTitle: String,
    val sourceUrl: String?,
    val personName: String?,
    val dueAt: Long?,
    val amountCents: Long?,
    val currency: String?,
    val confidence: Float,
    val status: ProposalStatus,
    /** One line: why this was picked ("names a person, a document and a date"). */
    val reason: String,
    val matchedTaskId: String?,
    val collectedAt: Long,
    val decidedAt: Long?,
)

data class LifePerson(
    val id: String,
    val name: String,
    val role: String?,
    val email: String?,
    val phone: String?,
    val lastSeenAt: Long,
    val openCommitments: Int,
    val lastMemory: String?,
)

data class PersonDetail(
    val person: LifePerson?,
    /** Kept commitments, deadlines and money the user owes this person. */
    val youOwe: List<LifeProposal>,
    /** Kept items where the person owes the user (kind awaiting). */
    val owedToYou: List<LifeProposal>,
    /** Every kept memory that names this person, newest first. */
    val recent: List<LifeProposal>,
)

data class LifeTask(
    val id: String,
    /** clickup, or memory for a task made from a kept proposal. */
    val source: String,
    val listName: String?,
    val title: String,
    val dueAt: Long?,
    val status: String,
    val doneLocal: Boolean,
    val snoozedUntil: Long?,
    val url: String?,
)

data class LifeMeeting(
    val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val location: String?,
    val joinUrl: String?,
)

data class AppRunStat(
    val app: String,
    val found: Int,
    val dropped: Int,
    val proposed: Int,
    val state: String,
    val note: String?,
)

data class LifeRun(
    val id: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val apps: List<AppRunStat>,
    /** Why items were dropped, e.g. "promotion" to 6. */
    val noiseReasons: Map<String, Int>,
    val noiseDropped: Int,
    val proposalsCreated: Int,
)

/** One row of the report. [isTask] false means it is a kept memory. */
data class ReportItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val dueAt: Long?,
    val isTask: Boolean,
    val source: String?,
    val url: String?,
)

data class DailyReport(
    val date: Long,
    val collectedAt: Long?,
    val dueToday: List<ReportItem>,
    val overdue: List<ReportItem>,
    val aware: List<ReportItem>,
    val meetings: List<LifeMeeting>,
    val open: List<ReportItem>,
    val toReview: Int,
)

enum class AppState { WAITING, RUNNING, DONE, FAILED, SKIPPED }

data class AppProgress(
    val app: String,
    val label: String,
    val found: Int,
    val dropped: Int,
    val state: AppState,
    val note: String?,
)

data class CollectionProgress(
    val running: Boolean,
    val startedAt: Long?,
    val apps: List<AppProgress>,
) {
    companion object { val IDLE = CollectionProgress(false, null, emptyList()) }
}

data class CollectionResult(
    val runId: String,
    /** done, failed (nothing could be pulled), or skipped (no key or nothing connected). */
    val status: String,
    val proposalsCreated: Int,
    val noiseDropped: Int,
    val apps: List<AppRunStat>,
    val error: String?,
)

/** What the review screen shows after the deck is empty. */
data class LearnedSummary(val dropped: Int, val kept: Int, val byReason: Map<String, Int>)

internal fun runStatsToJson(apps: List<AppRunStat>, noise: Map<String, Int>): String {
    val o = JsonObject()
    val a = JsonArray()
    apps.forEach {
        val x = JsonObject()
        x.addProperty("app", it.app); x.addProperty("found", it.found); x.addProperty("dropped", it.dropped)
        x.addProperty("proposed", it.proposed); x.addProperty("state", it.state); x.addProperty("note", it.note)
        a.add(x)
    }
    o.add("apps", a)
    val n = JsonObject()
    noise.forEach { (k, v) -> n.addProperty(k, v) }
    o.add("noise", n)
    return o.toString()
}

internal fun runStatsFromJson(json: String): Pair<List<AppRunStat>, Map<String, Int>> = try {
    val o = JsonParser.parseString(json).asJsonObject
    val apps = (o.getAsJsonArray("apps") ?: JsonArray()).map {
        val x = it.asJsonObject
        AppRunStat(
            x.get("app")?.asString ?: "", x.get("found")?.asInt ?: 0, x.get("dropped")?.asInt ?: 0,
            x.get("proposed")?.asInt ?: 0, x.get("state")?.asString ?: "",
            x.get("note")?.takeIf { n -> !n.isJsonNull }?.asString,
        )
    }
    val noise = o.getAsJsonObject("noise")?.entrySet()?.associate { e -> e.key to e.value.asInt } ?: emptyMap()
    apps to noise
} catch (e: Exception) {
    emptyList<AppRunStat>() to emptyMap()
}
