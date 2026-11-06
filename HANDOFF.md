# HANDOFF: MemGallery Life Agent v1

Written 6 Nov 2026 ~16:35 SAST by the MemGallery project chat (the one that is being replaced by a server chat).
Branch: **feat/life-agent** (off master 46cba59, v0.4.0). Nothing merged to master, no PR.

## Where the build runs
The impetix server, repo `~/repos/MemGallery`, branch `feat/life-agent`. Editing, Gradle, tests and renders all
happen there: heavy jobs through `~/bin/build-slot`, and `./gradlew --stop` when a job ends. Gradle never runs on
the PC. Only the finished debug APK and the proof screenshots go back to `Downloads\MemGallery\life-agent-build\` on
the PC.

## Done before this handoff
1. **Design mocks, approved by Ayanda on 6 Nov 2026 16:11**, on branch `design/life-agent-mocks`, folder
   `design/life-agent-mocks/`: 15 screens light and dark (824x1830), an exact copy of the approved v4 kit in `_kit/`,
   `README.md` (the contract for the mocks), `SAMPLE-DATA.md` (one fictional dataset), `board-light.png` and
   `board-dark.png`. His viewing copies: `Downloads\MemGallery\life-agent-mocks\` on the PC.
2. **`docs/life-agent/CONTRACT.md`** on `feat/life-agent`, which is the build contract: new package
   `com.example.memgallery.lifeagent/` (data, composio, collect, work, ui, widget, di), the DataStore keys, Room
   version 19 tables and columns, the repository API the UI talks to, the four apps with their Composio read tool
   slugs, the collection rules (noise, proposals, fingerprint dedupe, learning bias), file ownership, and the
   non-negotiables (read only, no key or account in code, works with no key, slide-up sheets only).
3. **Data, Composio, collection and work layers: BUILT AND PUSHED** (helper A finished after the handoff was first
   written; this replaces what the earlier draft said). Commits on `feat/life-agent`: `74a4072` (models, entities,
   DAO, Room v19 migration, repository, settings keys, Composio client and catalog, Hilt module), `35ab1c9` (noise
   filter, proposal builder, collection runner, workers, schedule, nudges, controls, nudge strings), `57f37c3`
   (unit tests), `b36bd12` (`connect()` looks the app up in the user's own Composio project and stores its account
   id). `assembleDebug` builds and **82 unit tests pass** (CollectionRunner 12, ComposioCatalog 13, ComposioClient 10,
   LifeAgentRepository 13, Migration 2, NoiseFilter 9, NudgeAndSchedule 7, ProposalBuilder 16). `./gradlew --stop`
   was run. Nothing has been run against a real Composio account yet, so the response parsers are untested on live
   data.
   - Read-only is enforced in code: `execute()` refuses any slug outside `READ_TOOLS` before a request, with a test
     proving five write slugs send nothing.
   - Works with no key (the run is "skipped"), with no AI provider (rules only), and with any one app missing; one
     app failing never fails the run.
   - Departures to keep: `AppModule.kt` has one added line for `MIGRATION_18_19`; `life_connections` carries
     `accountId` and `userId` (Composio ids, not secrets); ClickUp uses `CLICKUP_GET_AUTHORIZED_TEAMS_WORKSPACES`
     plus `CLICKUP_GET_FILTERED_TEAM_TASKS`; the key is stored encrypted as `composio_api_key_secure`; key, switch
     and time live in a new `LifeAgentControls` (reschedules on every change); `keep()` with a date creates a local
     task `p:<id>`; the widget owner sets `LifeAgentControls`/repository `onDataChanged` to refresh Glance.
   - Still owed by whoever does the next part: the nudge notification has no snooze or done button because that needs
     a manifest receiver; it taps into `MainActivity` with action
     `com.example.memgallery.lifeagent.OPEN_ITEM`, extra `life_agent_item_id`, and navigation must route it. The
     screens must call `LifeAgentControls.ensureScheduled()` on app start. ClickUp, Slack and Calendar response
     shapes are guesses from the docs until one real read each is run.

4. **Helper B (screens and navigation) was still working when this thread stopped.** Check whether it pushed; if not,
   its work may sit uncommitted in a temp clone on the server. Its split is unchanged: the screens from the mocks,
   navigation and entry points, chat source rows, and Roborazzi screenshot tests.

## What is left (Ayanda's nine, read only)
1. Settings > Advanced > Connections: the user's own Composio key, Gmail, Google Calendar, ClickUp, Slack, read only.
2. Daily collection on WorkManager at the user's time (default 17:00): pull since the last run, drop noise, propose
   memories, dedupe, learn from keeps and drops.
3. Swipe review deck, Approve all sheet, caught-up state.
4. People list and person page.
5. Task awareness from the task manager plus calendar.
6. Nudges as notifications (due soon, overdue).
7. Daily report screen (aware, due today, overdue, meetings, still open).
8. Interactive Glance widgets 2x2, 4x2, 4x4.
9. Chat answers from memories, tasks and mail, with numbered sources.

Then: unit tests with fake Composio responses, screenshot tests per screen in light and dark, a proof run with a
fake connection that never uses Ayanda's real mail, a debug APK and proof screenshots in
`Downloads\MemGallery\life-agent-build\`, and `adb install -r` on his phone only when he is connected. Merge to main
needs his yes.

## Two things the next agent must know
- **The server cannot run an ordinary Android emulator**: no `/dev/kvm` and no AVD or system image installed
  (`adb` exists at `/usr/bin/adb`, `/home/impetix/android-sdk` exists). Software emulation would crawl. Robolectric
  and Roborazzi (the app's own proof method) do run on the server, and a real phone can be reached over `adb` when
  he plugs it in. **Ayanda's call at 16:21:** as long as the helpers run and do everything on the server and
  he can still see what they are doing, that is fine. So: proof is Robolectric and Roborazzi on the server, plus
  `adb` to his phone when he connects one. Do not go hunting for an emulator, and do not skip the proof either.
- **The app has Glance, WorkManager (hilt-work), DataStore, Room and Roborazzi already** (`ui/widget/` has
  `MemoryPulseWidget` + `CaptureBarWidget` as the pattern). The AI side is provider-per-job
  (`data/remote/ai/JobRouting.kt`), which the proposal builder should reuse when a provider is configured and must
  not require.

## Untouched
No app code was committed by this thread. `AppDatabase`, `SettingsRepository`, `AppNavigation`, `ChatScreen` and
`AndroidManifest.xml` are exactly as v0.4.0 left them.
