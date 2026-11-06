package com.example.memgallery.lifeagent

import com.example.memgallery.lifeagent.ui.*

/**
 * Fictional fixture data for the Life Agent screens (design/life-agent-mocks/SAMPLE-DATA.md on the design branch).
 * Generated in code: no repository, no Hilt, nothing of anyone's real accounts. Today is Fri 6 Nov 2026, collected 17:04.
 */
object LifeFixtures {
    // ---- Connections ----
    fun connections(): ConnectionsUiState = ConnectionsUiState(
        keySet = true, maskedKey = "•".repeat(15) + "3f9a", collectionEnabled = true, hour = 17, minute = 0,
        rows = listOf(
            ConnectionRowUi(LifeAppId.GMAIL, true, "17:04"),
            ConnectionRowUi(LifeAppId.GCAL, true, "17:04"),
            ConnectionRowUi(LifeAppId.CLICKUP, true, "17:04"),
            ConnectionRowUi(LifeAppId.SLACK, false),
        ),
    )

    fun connectionsNoKey() = ConnectionsUiState()

    fun connectionsFailed() = connections().copy(
        rows = listOf(
            ConnectionRowUi(LifeAppId.GMAIL, false, error = "Composio could not reach Gmail. Connect it again."),
            ConnectionRowUi(LifeAppId.GCAL, true, "17:04"),
            ConnectionRowUi(LifeAppId.CLICKUP, true, "Thu 5 Nov", error = "Last pull failed: rate limited. Retrying tomorrow."),
            ConnectionRowUi(LifeAppId.SLACK, false),
        ),
    )

    // ---- Collecting ----
    fun collectingRunning() = CollectingUiState(
        running = true, step = 3, steps = 4, startedLabel = "17:00", found = 41, dropped = 14,
        apps = listOf(
            CollectAppUi(LifeAppId.GMAIL, StepState.Done, 34, 12, listOf("8 promotions, 3 receipts, 1 social")),
            CollectAppUi(LifeAppId.GCAL, StepState.Done, 4, 2, listOf("2 repeats, already known")),
            CollectAppUi(LifeAppId.CLICKUP, StepState.Running, found = 3, note = "Reading “Q4 expense sheet for Kagiso”", readSoFar = 12, readTotal = 18),
            CollectAppUi(LifeAppId.SLACK, StepState.Queued),
        ),
    )

    fun collectingFailedOne() = collectingRunning().copy(
        apps = collectingRunning().apps.map { if (it.app == LifeAppId.SLACK) it.copy(state = StepState.Failed, note = "Composio refused the read: reconnect Slack.") else it },
    )

    fun collectingFinished() = CollectingUiState(
        finished = true, step = 4, steps = 4, startedLabel = "17:00", found = 41, dropped = 14, proposalsCreated = 11,
        apps = collectingRunning().apps.map { if (it.state == StepState.Done) it else it.copy(state = StepState.Done, found = 3, dropped = 0, reasons = emptyList(), note = null) },
    )

    fun collectingIdle() = CollectingUiState(apps = listOf(CollectAppUi(LifeAppId.GMAIL, StepState.Queued)))
    fun collectingNoApps() = CollectingUiState()

    // ---- Review ----
    val jaco = ReviewCardUi(
        id = "p4", app = LifeAppId.SLACK, appLabel = "Slack", title = "Jaco Steyn promised the retouched Umoya set today.",
        personName = "Jaco Steyn", personRole = "Freelance photographer",
        why = "A commitment owed to you, with a date on it.",
        dateLabel = "Today, Fri 6 Nov · 17:00", dateTag = "Due today", dateTone = DateTone.Today,
        matchedTask = "Chase Jaco for the set", sourceTitle = "Slack · #studio", sourceSub = "Jaco Steyn, today",
        sourceUrl = "https://example.com/slack/studio", suggestKeep = true,
    )
    val takealot = ReviewCardUi(
        id = "p8", app = LifeAppId.GMAIL, appLabel = "Gmail", title = "Takealot Black Friday, up to 60% off.",
        why = "Looks like a promotion: a sender you have dropped before.", sourceTitle = "Takealot Black Friday",
        sourceSub = "Gmail · Takealot", suggestKeep = false,
    )
    val nomsa = ReviewCardUi(
        id = "p1", app = LifeAppId.GMAIL, appLabel = "Gmail", title = "Nomsa Mahlangu is waiting on the signed lease.",
        personName = "Nomsa Mahlangu", personRole = "Client, Umoya Lodge", why = "Names a person, a document and a date; you have not replied.",
        dateLabel = "Tomorrow, Sat 7 Nov · 16:00", dateTag = "Due tomorrow", dateTone = DateTone.Today, matchedTask = "Send the signed lease to Nomsa Mahlangu",
        sourceTitle = "Signed lease, before the weekend", sourceSub = "Gmail · Nomsa Mahlangu", suggestKeep = true,
    )
    val invoice = ReviewCardUi(
        id = "p2", app = LifeAppId.GMAIL, appLabel = "Gmail", title = "Anele Jacobs sent invoice INV-2043, R8,400.",
        personName = "Anele Jacobs", personRole = "Supplier, Kloof Print Works", why = "Money you owe with a date.",
        dateLabel = "Fri 13 Nov · 09:00", dateTag = "In 7 days", dateTone = DateTone.Upcoming, suggestKeep = true,
    )
    val signin = ReviewCardUi(
        id = "p5", app = LifeAppId.GMAIL, appLabel = "Gmail", title = "New sign-in to your Google account from Chrome on Windows, Durban.",
        why = "Security alert.", sourceTitle = "Security alert", sourceSub = "Gmail · Google", suggestKeep = true,
    )

    fun reviewDeck() = ReviewUiState(
        remaining = listOf(jaco, takealot, nomsa, invoice, signin), decided = 3, total = 8, keptSoFar = 3,
        collectedLabel = "17:04", canUndo = true,
    )

    fun reviewApproveAll() = ReviewUiState(
        remaining = listOf(jaco, signin, takealot, nomsa), decided = 3, total = 7, keptSoFar = 3, collectedLabel = "17:04",
    )

    fun reviewCaughtUp() = ReviewUiState(
        decided = 11, total = 11, keptSoFar = 7, collectedLabel = "17:04", droppedTotal = 12,
        learned = listOf(
            LearnedCountUi("newsletter", "Newsletters", 6), LearnedCountUi("promotion", "Promotions", 3),
            LearnedCountUi("receipt", "Receipts", 2), LearnedCountUi("social", "Social alerts", 1),
        ),
        nextCollectionLabel = "Tomorrow, Sat 7 Nov · 17:00",
    )

    fun reviewNothing() = ReviewUiState(nextCollectionLabel = "Daily collection is off")

    // ---- People ----
    fun people() = PeopleUiState(
        people = listOf(
            PersonRowUi("nomsa", "Nomsa Mahlangu", "Client, Umoya Lodge", "6 Nov", CommitmentKind.YouOwe, "signed lease, Sat 7 Nov 16:00", true, 7),
            PersonRowUi("kagiso", "Kagiso Motaung", "Accountant, Brightline Books", "6 Nov", CommitmentKind.YouOwe, "Q4 expense sheet, Mon 9 Nov", true, 6),
            PersonRowUi("jaco", "Jaco Steyn", "Freelance photographer", "6 Nov", CommitmentKind.OwesYou, "retouched Umoya set, today", true, 5),
            PersonRowUi("sizwe", "Sizwe Ndlovu", "Client, Ndlovu & Sons", "5 Nov", CommitmentKind.Meeting, "pitch Thu 12 Nov, 10:00 (moved)", true, 4),
            PersonRowUi("thandeka", "Thandeka Zulu", "Studio partner", "5 Nov", CommitmentKind.OwesYou, "final artwork, Tue 10 Nov", true, 3),
            PersonRowUi("anele", "Anele Jacobs", "Supplier, Kloof Print Works", "4 Nov", CommitmentKind.YouOwe, "R8,400 invoice INV-2043, Fri 13 Nov", true, 2),
            PersonRowUi("priya", "Priya Naidoo", "Landlord, Morningside", "26 Oct", CommitmentKind.Waiting, "reply on the deposit refund", true, 1),
        ),
    )

    // ---- Person ----
    fun person() = PersonUiState(
        found = true, name = "Nomsa Mahlangu", role = "Client, Umoya Lodge", lastLabel = "today, 6 Nov",
        commitments = listOf(
            CommitmentUi("c1", CommitmentKind.YouOwe, "Send the signed lease to Nomsa", LifeAppId.CLICKUP, "Sat 7 Nov, 16:00", taskId = "t1"),
            CommitmentUi("c2", CommitmentKind.Waiting, "Her reply about the deposit", LifeAppId.GMAIL, "no reply yet"),
        ),
        memories = listOf(
            PersonMemoryUi("m1", "Signed lease for Nomsa, due Sat 7 Nov 16:00", LifeAppId.GMAIL, "gmail", "6 Nov"),
            PersonMemoryUi("m2", "Jaco promised the retouched Umoya set today", LifeAppId.SLACK, "slack", "6 Nov"),
            PersonMemoryUi("m3", "Task added: send the signed lease to Nomsa", LifeAppId.CLICKUP, "clickup", "5 Nov"),
        ),
        email = "nomsa@umoyalodge.example", phone = "+27 31 555 0142",
    )

    fun personMissing() = PersonUiState(found = false)

    // ---- Daily report ----
    fun report() = DailyReportUiState(
        dateLabel = "Friday 6 November", collectedLabel = "17:04", anyConnected = true, toReview = 8,
        aware = listOf(
            AwareRowUi("a1", AwareKind.Deadline, "Nomsa's lease is due tomorrow", LifeAppId.GMAIL, "gmail", "Sat 7 Nov, 16:00", "https://example.com/m/1"),
            AwareRowUi("a2", AwareKind.Money, "INV-2043, R8,400, is due Fri 13 Nov", LifeAppId.GMAIL, "gmail", "Anele Jacobs", "https://example.com/m/2"),
            AwareRowUi("a3", AwareKind.Meeting, "Pitch moved to Thu 12 Nov, 10:00", LifeAppId.GCAL, "gcal", "Sizwe, was Wed"),
            AwareRowUi("a4", AwareKind.Security, "New Google sign-in from Durban", LifeAppId.GMAIL, "gmail", "Windows, Thu 21:12", "https://example.com/m/4"),
        ),
        dueToday = listOf(
            ReportTaskUi("t1", "Chase Jaco for the retouched set", LifeAppId.CLICKUP, "today 17:00"),
            ReportTaskUi("t2", "Prep the Q4 figures for Kagiso", LifeAppId.CLICKUP, "today, before 15:30"),
            ReportTaskUi("t3", "Standup notes for Thandeka", LifeAppId.CLICKUP, "today"),
        ),
        overdue = listOf(ReportTaskUi("t4", "Book the Durban workshop flights", LifeAppId.CLICKUP, "2 days late", overdue = true)),
        meetings = listOf(ReportMeetingUi("e1", "15:30", "Call with Kagiso about Q4", LifeAppId.GCAL, "Ended 16:00", ended = true)),
        open = listOf(
            ReportOpenUi("t5", "Q4 expense sheet"), ReportOpenUi("t6", "Approve INV-2043"), ReportOpenUi("t1", "Chase Jaco"),
            ReportOpenUi("t4", "Book the flights"), ReportOpenUi("t7", "Sign the lease"),
        ),
    )

    fun reportNoApps() = DailyReportUiState(dateLabel = "Friday 6 November")
    fun reportEmpty() = DailyReportUiState(dateLabel = "Friday 6 November", collectedLabel = "17:04", anyConnected = true)

    // ---- Chat ----
    val chatAnswer = "**Money.** INV-2043, R8,400 to Kloof Print Works, is due Fri 13 Nov. [2]\n\n**People.** Nomsa needs the lease by Sat 16:00. [1] Jaco's set is due today. [4]\n\n**Meetings.** The pitch moved to Thu 12 Nov, 10:00. [3]"
    val chatSources = listOf(
        LifeSourceUi(1, "Signed lease, before the weekend", LifeAppId.GMAIL, "gmail", "Nomsa Mahlangu", "https://example.com/m/1"),
        LifeSourceUi(2, "INV-2043 due 13 Nov", LifeAppId.GMAIL, "gmail", "Anele Jacobs", "https://example.com/m/2"),
        LifeSourceUi(3, "Ndlovu & Sons pitch, moved", LifeAppId.GCAL, "gcal", "Ndlovu & Sons"),
        LifeSourceUi(4, "#studio", LifeAppId.SLACK, "slack", "Jaco Steyn"),
    )
}
