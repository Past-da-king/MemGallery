# Life Agent v1: build contract

Approved by Ayanda 6 Nov 2026 16:11. READ ONLY towards every external app. Design mocks (the visual target):
`design/life-agent-mocks/` on branch `design/life-agent-mocks`. This branch: `feat/life-agent`, off `master` (v0.4.0).

Server build rule: Gradle only on the impetix server, in `~/repos/MemGallery`, through `~/bin/build-slot`, and
`./gradlew --stop` at the end of every job. No Gradle on the PC.

## Where things live (new package, keep it out of the way of existing code)
`app/src/main/java/com/example/memgallery/lifeagent/`

```
lifeagent/
  data/        LifeAgentEntities.kt, LifeAgentDao.kt, LifeAgentRepository.kt, LifeAgentModels.kt
  composio/    ComposioClient.kt, ComposioModels.kt, ComposioCatalog.kt
  collect/     NoiseFilter.kt, ProposalBuilder.kt, CollectionRunner.kt
  work/        LifeAgentCollectionWorker.kt, LifeAgentSchedule.kt, NudgeWorker.kt
  ui/          ConnectionsScreen.kt, CollectingScreen.kt, ReviewScreen.kt, PeopleScreen.kt,
               PersonScreen.kt, DailyReportScreen.kt, LifeAgentViewModels.kt, LifeAgentSheet.kt
  widget/      LifeAgentWidget.kt, LifeAgentWidgetReceiver.kt, LifeAgentWidgetActions.kt
  di/          LifeAgentModule.kt
```

Existing files that MAY be edited (each has ONE owner):
- `data/local/database/AppDatabase.kt` (entities + version 18 -> 19 + `addMigrations`): data owner.
- `data/repository/SettingsRepository.kt` (new keys only, additive): data owner.
- `navigation/AppNavigation.kt` + the Settings > Advanced entry row: UI owner.
- `AndroidManifest.xml` (receivers only): widget owner.
- `ui/screens/ChatScreen.kt` (source rows only): UI owner.
- Strings: each owner adds its OWN resource file, never edits `strings.xml`:
  `res/values/strings_lifeagent_data.xml`, `strings_lifeagent_ui.xml`, `strings_lifeagent_widget.xml`.
- Dependency graph (Hilt): each owner adds its own `@Module` in its own file. Never edit another's module.

## Settings (DataStore, same storage as the existing Gemini key, never in code)
`composio_api_key` (String, empty = not set), `life_agent_enabled` (Boolean, default false),
`life_agent_hour` (Int, default 17), `life_agent_minute` (Int, default 0), `life_agent_last_run` (Long).
The key belongs to the user who installs the app. Nothing in the code may contain a key, a token or Ayanda's data.

## Apps (read only, Composio)
| app id | label | Composio read tools |
|---|---|---|
| gmail | Gmail | GMAIL_FETCH_EMAILS |
| gcal | Google Calendar | GOOGLECALENDAR_EVENTS_LIST |
| clickup | ClickUp | CLICKUP_GET_TASKS |
| slack | Slack | SLACK_FETCH_CONVERSATION_HISTORY (channels first: SLACK_LIST_ALL_CHANNELS) |

Composio v3 over OkHttp, `x-api-key` header from the settings key: list connected accounts
(`GET /api/v3/connected_accounts`) and execute a read tool (`POST /api/v3/tools/execute/{slug}` with
`connected_account_id`, `user_id`, `arguments`). Verify the shapes against Composio's docs and keep the mapping in
one file so a change is a one-line fix. Every call must be a read tool; no create, update, send or delete path may
exist in the code at all. Timeouts, retries with backoff, and a clear `lastError` per connection.

## Room (version 19, migration 18 -> 19 creates these; no destructive fallback)
- `life_connections`: id TEXT PK, label, connected INTEGER, accountLabel, lastPulledAt INTEGER, lastError
- `life_items`: id TEXT PK, app, sourceId, kind, title, snippet, author, url, occurredAt, ingestedAt,
  fingerprint TEXT UNIQUE (the dedupe key)
- `life_proposals`: id TEXT PK, kind, title, detail, app, sourceId, sourceTitle, sourceUrl, personName, dueAt,
  amountCents, currency, confidence REAL, status TEXT (proposed|kept|dropped), reason, fingerprint TEXT UNIQUE,
  matchedTaskId, collectedAt, decidedAt
- `life_people`: id TEXT PK (normalised name), name, role, email, phone, lastSeenAt
- `life_tasks`: id TEXT PK, source, listName, title, dueAt, status, doneLocal INTEGER, snoozedUntil INTEGER, url
- `life_meetings`: id TEXT PK, title, startAt, endAt, location, joinUrl
- `life_runs`: id TEXT PK, startedAt, finishedAt, status (running|done|failed), appStatsJson, noiseDropped,
  proposalsCreated
- `life_learning`: id INTEGER PK AUTOINCREMENT, app, senderKey, kind, decision (keep|drop), decidedAt

`kind` is one of: person, commitment, deadline, decision, money, event, document, security, awaiting, other.

## Repository (the only thing the UI and the widgets talk to)
`class LifeAgentRepository` with:
- `connections: Flow<List<LifeConnection>>`, `proposals: Flow<List<LifeProposal>>`,
  `proposedCount: Flow<Int>`, `people: Flow<List<LifePerson>>`, `person(name): Flow<PersonDetail>`,
  `tasks: Flow<List<LifeTask>>`, `meetings: Flow<List<LifeMeeting>>`, `latestRun: Flow<LifeRun?>`,
  `dailyReport(): Flow<DailyReport>` (today's due, overdue, aware, meetings, open)
- suspend: `connect(app)`, `disconnect(app)`, `runCollection(): CollectionResult` with a
  `collectionProgress: Flow<CollectionProgress>` (per app: app, found, dropped, state, note),
  `keep(id)`, `drop(id)`, `keepAll()`, `markDone(taskId)`, `markUndone(taskId)`, `snooze(taskId, until)`,
  `askContext(id): String` (the one-paragraph context a chat question is opened with).

## Collection (WorkManager, daily at the user's time, default 17:00)
1. For each connected app, pull items newer than `life_connections.lastPulledAt` (first run: the last 7 days).
2. `NoiseFilter` drops promotions, newsletters, social pings, receipts with nothing to do, duplicates and anything
   already known: every drop is counted and given a one-line reason.
3. `ProposalBuilder` turns what survives into proposals (person, commitment, deadline, decision, money, event,
   document, security, awaiting). It may use the existing per-job AI provider for the sentence and the kind, with a
   deterministic fallback when no provider is configured; it must never be required.
4. Dedupe on `fingerprint`: same source item, or the same person plus the same thing (a lease, an invoice number),
   never twice. `life_learning` biases what is dropped next time (a sender or kind dropped 3 times is proposed less).
5. Write the run row, per-app stats, update `lastPulledAt`, then let the repository refresh widgets.
Failure of one app never fails the run: it records `lastError` and carries on.

## Widgets (Glance, three sizes, existing `ui/widget` is the pattern to follow)
2x2 (count + top item), 4x2 (three to be aware of + next meeting), 4x4 (full report, scrollable list, keep or
drop card). Actions: tick done (local only), snooze 1h/tomorrow, keep/drop, refresh, Ask (opens chat with the item
as context). Every action writes through the repository and refreshes the widget.

## Tests and proof
- Unit tests with fakes/MockWebServer for every Composio read call, the noise filter, the proposal builder, dedupe,
  the collection run end to end, and the learning bias. No network, no real account.
- Roborazzi screenshot tests for every new screen, light and dark (the existing proof package is the pattern).
- Emulator run with a fake connection: the whole loop from connected -> collected -> review -> kept -> report ->
  widget -> chat, then screenshot each step.
- Never Ayanda's real mail, calendar, ClickUp or Slack. Fixtures only.

## Rules that are not negotiable
- Read only: no write to any external app, ever, in v1.
- No hardcoded key, token, email address, user id or workspace id. Nothing tied to Ayanda's accounts.
- The app must work with no Composio key at all (the screens explain what to do), and with any one app missing.
- Phone: every choice, form and confirmation is a slide-up sheet, never a centred dialog.
- Material 3 Expressive, the tokens and components already in `ui/theme` and `ui/design`; the mocks are the target.
