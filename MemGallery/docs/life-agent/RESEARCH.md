# Composio Connect: the call shapes (live, 6 Nov 2026)

Measured with his consumer key (`ck_`) over `POST https://connect.composio.dev/mcp`, header `x-consumer-api-key`.
Read-only. This note only exists to keep the calls right.

## Discovery and status
- `COMPOSIO_SEARCH_TOOLS {queries:[{use_case}]}` → `data.toolkit_connection_statuses[] {toolkit, description,
  has_active_connection}`, `data.tool_schemas {SLUG:{toolkit, tool_slug, input_schema}}`. This is the safe way to learn
  what is connected.
- `COMPOSIO_MANAGE_CONNECTIONS {toolkits:[{name, action:"list"|"add"}]}` → `data.results.<slug> {toolkit, status,
  accounts[]}`; each account: `id` (`slack_charuk-sheen`), `status`, `user_info{email,name}`.
  **Trap:** `list` on a toolkit with nothing connected answers `status: initiated` and can start a pending auth. Only
  ask for accounts of toolkits a search already reports as connected.
- `action:"add"` → `redirect_url` (Composio's hosted auth page). No callback/deep-link parameter exists: he finishes in
  the browser and comes back, so the app polls `list` until `status: active`.

## Reads
- `COMPOSIO_MULTI_EXECUTE_TOOL {tools:[{tool_slug, arguments, account}], thought}` → `data.results[] {tool_slug,
  response:{successful, data}}`, **and for a large answer `data_preview` replaces `data`**. Read both.
- ClickUp: `CLICKUP_GET_AUTHORIZED_TEAMS_WORKSPACES {}` → `data.teams[].id`, then
  `CLICKUP_GET_FILTERED_TEAM_TASKS {team_Id, page:0}` → `data.tasks[]` (`name`, `description`, `date_updated` millis
  string, `assignees[].username`, `status.status`, `url`, `last_page`). The team id is required; an empty-argument call
  is refused, which is why ClickUp reported nothing.
- Slack: `SLACK_LIST_CONVERSATIONS {types:"public_channel,private_channel", limit}` → `data.channels[] {id, name}`,
  then `SLACK_FETCH_CONVERSATION_HISTORY {channel, limit}` → `data.messages[] {ts, user, text}`. Channel id is
  required.
- Gmail `GMAIL_FETCH_EMAILS {max_results, query:"newer_than:7d"}`; Calendar `GOOGLECALENDAR_EVENTS_LIST {timeMin,
  maxResults, singleEvents, orderBy}`.
- Logos: `https://logos.composio.dev/api/<slug>` answers 200 (slack, clickup, gmail, googlecalendar, notion), no key.

## Why his account looked empty
His Slack, ClickUp and Gmail connections live in Composio's **consumer** account (For You), which a `ck_` key sees and a
developer project key does not. The first check said zero because accounts were discovered the wrong way; the key has
three active apps. A `ck_` key can never see the connections of a developer project (the Agent desktop app's), so those
need a project key (`ak_`), which the app also supports.
