package com.example.memgallery.lifeagent.ui

/**
 * Plain UI state for the Life Agent screens. Everything here is already formatted (dates, counts, labels),
 * so the composables stay dumb and the screenshot tests can feed them fixtures with no repository and no Hilt.
 * The ViewModels in LifeAgentViewModels.kt map the repository's models onto these.
 */

/** The four read-only apps of v1. [id] is the app id the repository uses. */
enum class LifeAppId(val id: String) {
    GMAIL("gmail"), GCAL("gcal"), CLICKUP("clickup"), SLACK("slack");

    companion object {
        fun from(id: String?): LifeAppId? = entries.firstOrNull { it.id == id }
    }
}

// ---- Connections ----

/** One row of the Connections list: any Composio toolkit, connected or not. */
data class ToolkitRowUi(
    val slug: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val categories: List<String> = emptyList(),
    val connected: Boolean = false,
    val lastPulled: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    /** Gmail, Calendar, ClickUp and Slack get their own hand-written parser; the rest use the generic read path. */
    val known: Boolean = false,
)

data class ConnectionsUiState(
    val keySet: Boolean = false,
    /** Only the shape of the key (dots plus the last four), never the key itself. */
    val maskedKey: String = "",
    /** "Project key" or "Personal key", once Composio has said which. */
    val keyKind: String? = null,
    val projectName: String? = null,
    val checking: Boolean = false,
    /** The real answer from Composio, including the status and its own message when it refused the key. */
    val keyCheckOk: Boolean? = null,
    val keyCheckMessage: String? = null,
    val projects: List<ProjectRowUi> = emptyList(),
    val showProjectSheet: Boolean = false,
    val query: String = "",
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val connected: List<ToolkitRowUi> = emptyList(),
    val others: List<ToolkitRowUi> = emptyList(),
    val loading: Boolean = false,
    val catalogError: String? = null,
    /** "Finish connecting Gmail in your browser", from a connect link. */
    val connectMessage: String? = null,
    val connectUrl: String? = null,
    val keyError: String? = null,
    val keySaving: Boolean = false,
    val collectionEnabled: Boolean = false,
    val hour: Int = 17,
    val minute: Int = 0,
)

data class ProjectRowUi(val id: String, val name: String, val orgId: String?)

// ---- Collecting ----

enum class StepState { Done, Running, Queued, Skipped, Failed }

data class CollectAppUi(
    val app: LifeAppId,
    val state: StepState,
    val found: Int = 0,
    val dropped: Int = 0,
    /** One line per dropped item, already worded ("Takealot Black Friday: a promotion"). */
    val reasons: List<String> = emptyList(),
    /** What is being read right now, or the error when [state] is Failed. */
    val note: String? = null,
    val readSoFar: Int = 0,
    val readTotal: Int = 0,
)

data class CollectingUiState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val step: Int = 0,
    val steps: Int = 0,
    val startedLabel: String = "",
    val found: Int = 0,
    val dropped: Int = 0,
    val apps: List<CollectAppUi> = emptyList(),
    val proposalsCreated: Int = 0,
)

// ---- Review ----

enum class DateTone { Today, Overdue, Upcoming }

data class ReviewCardUi(
    val id: String,
    val app: LifeAppId?,
    /** Source app name when it is not one of the four (shown as plain text). */
    val appLabel: String,
    val title: String,
    val personName: String? = null,
    val personRole: String? = null,
    val why: String,
    val dateLabel: String? = null,
    val dateTag: String? = null,
    val dateTone: DateTone = DateTone.Upcoming,
    val matchedTask: String? = null,
    val sourceTitle: String = "",
    val sourceSub: String = "",
    val sourceUrl: String? = null,
    val suggestKeep: Boolean = true,
)

data class LearnedCountUi(val kind: String, val label: String, val count: Int)

data class ReviewUiState(
    /** Cards still waiting, top card first. */
    val remaining: List<ReviewCardUi> = emptyList(),
    /** Cards decided in this session, including the ones in [remaining] before the user reached them. */
    val decided: Int = 0,
    val total: Int = 0,
    val keptSoFar: Int = 0,
    val collectedLabel: String = "",
    val canUndo: Boolean = false,
    val learned: List<LearnedCountUi> = emptyList(),
    val nextCollectionLabel: String = "",
    val droppedTotal: Int = 0,
)

// ---- People ----

enum class CommitmentKind { YouOwe, OwesYou, Meeting, Waiting }

data class PersonRowUi(
    val id: String,
    val name: String,
    val role: String,
    val lastLabel: String,
    val commitmentKind: CommitmentKind? = null,
    val commitmentText: String? = null,
    val hasOpen: Boolean = false,
    val sortKey: Long = 0L,
)

enum class PeopleSort { Recent, AtoZ, Open }

data class PeopleUiState(
    val people: List<PersonRowUi> = emptyList(),
    val query: String = "",
    val sort: PeopleSort = PeopleSort.Recent,
) {
    /** The list as shown: filtered by the query, ordered by [sort]. */
    val visible: List<PersonRowUi>
        get() {
            val q = query.trim().lowercase()
            val filtered = if (q.isEmpty()) people else people.filter {
                it.name.lowercase().contains(q) || it.role.lowercase().contains(q)
            }
            return when (sort) {
                PeopleSort.Recent -> filtered.sortedByDescending { it.sortKey }
                PeopleSort.AtoZ -> filtered.sortedBy { it.name.lowercase() }
                PeopleSort.Open -> filtered.filter { it.hasOpen }.sortedByDescending { it.sortKey }
            }
        }
}

data class CommitmentUi(
    val key: String,
    val kind: CommitmentKind,
    val title: String,
    val app: LifeAppId?,
    val dateLabel: String? = null,
    /** The task id when the row can be ticked or snoozed. */
    val taskId: String? = null,
    val done: Boolean = false,
)

data class PersonMemoryUi(val key: String, val title: String, val app: LifeAppId?, val appLabel: String, val dateLabel: String)

data class PersonUiState(
    val found: Boolean = false,
    val name: String = "",
    val role: String = "",
    val lastLabel: String = "",
    val commitments: List<CommitmentUi> = emptyList(),
    val memories: List<PersonMemoryUi> = emptyList(),
    val email: String? = null,
    val phone: String? = null,
) {
    val youOwe: Int get() = commitments.count { it.kind == CommitmentKind.YouOwe && !it.done }
    val waiting: Int get() = commitments.count { (it.kind == CommitmentKind.Waiting || it.kind == CommitmentKind.OwesYou) && !it.done }
}

// ---- Daily report ----

enum class AwareKind { Deadline, Money, Meeting, Security, Person, Awaiting, Other }

data class AwareRowUi(
    val key: String,
    val kind: AwareKind,
    val title: String,
    val app: LifeAppId?,
    val appLabel: String,
    val sub: String,
    val url: String? = null,
    val proposalId: String? = null,
)

data class ReportTaskUi(
    val taskId: String,
    val title: String,
    val app: LifeAppId?,
    val whenLabel: String,
    val done: Boolean = false,
    val overdue: Boolean = false,
)

data class ReportMeetingUi(
    val key: String,
    val timeLabel: String,
    val title: String,
    val app: LifeAppId?,
    val sub: String,
    val ended: Boolean,
    val joinUrl: String? = null,
)

data class ReportOpenUi(val taskId: String, val title: String)

data class DailyReportUiState(
    val dateLabel: String = "",
    val collectedLabel: String? = null,
    val aware: List<AwareRowUi> = emptyList(),
    val dueToday: List<ReportTaskUi> = emptyList(),
    val overdue: List<ReportTaskUi> = emptyList(),
    val meetings: List<ReportMeetingUi> = emptyList(),
    val open: List<ReportOpenUi> = emptyList(),
    val toReview: Int = 0,
    val anyConnected: Boolean = false,
) {
    val isEmpty: Boolean get() = aware.isEmpty() && dueToday.isEmpty() && overdue.isEmpty() && meetings.isEmpty() && open.isEmpty()
}

// ---- Chat sources ----

data class LifeSourceUi(
    val number: Int,
    val title: String,
    val app: LifeAppId?,
    val appLabel: String,
    val who: String,
    val url: String? = null,
)
