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
3. **Two helpers were started on the server build** and were still working when this thread stopped. Neither had
   pushed a commit at 16:35, so their work may sit uncommitted in their temp clones on the server, or may have been
   lost when the thread ended. First thing to check:
   - `cd ~/repos/MemGallery && git fetch && git log --oneline origin/feat/life-agent -5`
   - `cd ~/repos/MemGallery && git status --short` (untracked files from the mocks branch may be lying around;
     they are already committed on `design/life-agent-mocks`, so they can be cleaned)
   - look for stray clones such as `~/tmp-gen-*/clone` with uncommitted work, and for Gradle daemons
     (`./gradlew --stop`).
   Their split (still the right split): A = data + Composio + collection + WorkManager + nudges + unit tests;
   B = screens + navigation + chat sources + Roborazzi screenshot tests. The widget set (Glance 2x2, 4x2, 4x4 with
   tick, snooze, keep/drop, refresh, Ask) was never handed to a helper and is still to do.

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
