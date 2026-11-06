# Life Agent v1: what is built, and what is not yet proven

Branch `feat/life-agent` (off master v0.4.0). Nothing merged to master. Read only towards every external app.

## Built
- **Data, Composio, collection, work** (74a4072, 35ab1c9, 57f37c3, b36bd12): Room v19 tables, repository,
  read-only Composio client (any slug outside `READ_TOOLS` throws before a request), noise filter, proposal builder
  with optional AI, collection runner, daily worker at the user's time (default 17:00), nudges.
- **Screens and navigation** (2bb7e71, 213b45e, ba8a861, bf5bac0): Connections, Collecting (with cancel), Review
  deck with Approve all sheet and caught up, People, Person, Daily report, chat source rows. Slide-up sheets only.
- **Parsers hardened against recorded fixtures** (e8cd276): 13 fixtures for the four apps plus 401/429/500/garbage
  bodies; Gmail header fallback, timestamp hardening, `response_data` envelope unwrapping; 23 new tests.
- **Widget set** (this change): `lifeagent/widget/`, Glance 2x2, 4x2, 4x4 on one responsive widget.
  - 2x2: counts line, the one thing that needs him now, tick done, Ask.
  - 4x2: left rail (date, counts, next meeting, refresh, Ask) and three tap rows.
  - 4x4: date and counts, sections Late / Due today / Meetings / Open, then the proposed memory with Keep and Drop.
  - Every tap is local: `markDone`, `snooze` (1 hour or 08:00 tomorrow), `keep`, `drop` in MemGallery's own tables,
    then the widget redraws. Nothing is written to Gmail, Calendar, ClickUp or Slack.
  - Ask opens the chat with the item as the question (`LifeChatBridge`), the same path the person page uses.
  - Refresh: after any data change (`repository.onDataChanged`), on widget add and resize, and a 3-hour local worker
    (`LifeAgentWidgetWorker`) so "today" stays honest as the day moves.
- **Nudge Done and Snooze** (this change): `NudgeActionReceiver` behind the nudge notification's two buttons, and
  `MainActivity` routes an `OPEN_ITEM` tap to the daily report (the screen that owns a task).
- **Ask on the review card** (this change): `askContext` feeds the card's own source to the chat as [1].

## Not proven yet
- **Widget visuals have never been rendered on a device.** Glance has no screenshot harness on the server, so the
  layouts were built from the approved mocks and compile, but nobody has looked at them on a home screen. Judge them
  first when the APK is installed.
- **No live Composio read.** Every parser is tested against recorded fixtures in the shape of the documented
  responses, not a real account. One read per app on his phone, then compare.

## The live-read list (one per app, on his phone)
1. Gmail (`GMAIL_FETCH_EMAILS`): subject, sender with name and address, time, open link, UNREAD/CATEGORY labels.
2. Google Calendar (`GOOGLECALENDAR_EVENTS_LIST`): time zone, all-day events, recurring instance ids, hangoutLink,
   cancelled events skipped.
3. ClickUp (`CLICKUP_GET_AUTHORIZED_TEAMS_WORKSPACES` then `CLICKUP_GET_FILTERED_TEAM_TASKS`): team ids, `due_date`
   as string millis, `status.status`, task url, second page.
4. Slack (`SLACK_LIST_ALL_CHANNELS` then `SLACK_FETCH_CONVERSATION_HISTORY`): public, private and DM channels,
   archived skipped, `messages[]` with `ts` and `user`. Slack authors are ids, so no person name appears.
5. Connected accounts (`GET /connected_accounts`): `toolkit.slug` and `status` ACTIVE for the four toolkits.
