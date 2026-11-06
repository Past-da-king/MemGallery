# MemGallery Agent Bridge — Plan

**Status:** BUILT — all five phases. See "What shipped" below.
**Date:** 2026-08-06
**Target app version:** 0.3.4 → 0.4.0
**Goal:** let *any* agent or tool (A.O.S is just the first client) read everything in MemGallery, write to it, and get the operator's attention on his phone — over Tailscale when it's up, and still usefully when it isn't.

---

## 0. What we're actually building

Three things, cleanly separated so none of them knows A.O.S exists:

| Layer | What | Lives where |
|---|---|---|
| **A. `AgentApi`** | One Kotlin facade covering every MemGallery capability. Transport-agnostic. | In the Android app |
| **B. Transports** | Embedded HTTP+SSE server (Tailscale/LAN), and a hub/outbox path for when the phone isn't reachable | In the Android app |
| **C. Connectors** | `memgallery-mcp` (an MCP server), an OpenAPI 3.1 spec, outbound webhooks | Outside the repo — a small Node/TS package |

**The "concrete thing" is the MCP server.** That's what makes this not-a-raw-socket: A.O.S, Claude Code, Cursor, Cline, OpenCode — anything that speaks MCP — points at `memgallery-mcp` and immediately has ~16 typed tools. Anything that doesn't speak MCP uses the OpenAPI spec. The Android app itself only ever ships a documented REST API; it has zero knowledge of MCP or A.O.S.

---

## 1. What MemGallery already has (audited)

Native Android — Kotlin, Compose, Hilt, Room v17, WorkManager. `com.example.memgallery`, minSdk 24, targetSdk 36. Zero-cloud: there is no backend anywhere, the phone is the only source of truth.

Domain objects that exist today:

- **`MemoryEntity`** — the core. userText / imageUris / audio / bookmark URL, plus AI-generated `aiTitle`, `aiSummary`, `aiTags`, `aiImageAnalysis`, `aiAudioTranscription`, `aiActions`, and a `status` lifecycle (PENDING → PROCESSING → COMPLETED/FAILED).
- **`TaskEntity`** — title, description, `dueDate` (YYYY-MM-DD), `dueTime` (HH:MM), `priority`, `type` (TODO/EVENT), `isCompleted`, `isRecurring`/`recurrenceRule`, optional `memoryId` parent, and — importantly — **`isApproved`**.
- **`CollectionEntity`** + `MemoryCollectionCrossRef` — the closest thing to "projects".
- **`ChatEntity`/`ChatMessageEntity`** — in-app AI chat history.

Two existing pieces we should reuse rather than reinvent:

1. **`ChatTools.queryDatabase(table, filters, fields)`** — the app *already* has an agent-shaped read layer over memories/tasks/collections, plus `getDatabaseSchema()`. The REST layer should be built on the same query semantics, not a parallel one.
2. **`ActionNotificationHandler`** — already builds Android notifications from AI-detected actions, already honours `notificationsEnabledFlow` and a filter. Agent notifications become a second channel next to it.

And the sleeper feature: **`isApproved = false`**. `UnapprovedTasksSheet` already gives the operator an inbox of AI-proposed tasks to accept or bin. That *is* the "agent asks me something and I decide on the phone" primitive — it just needs an answer to travel back out.

**Gap:** there is no `Project` entity. See §7.

---

## 2. Architecture

```
                        ┌──────────────────────────── PHONE (source of truth) ──────┐
                        │                                                            │
   MCP client           │   ┌────────────┐    ┌───────────────────────────────────┐  │
   (A.O.S / Claude      │   │  Room DB   │◄──►│  AgentApi  (Kotlin facade)        │  │
    Code / Cursor)      │   └────────────┘    │  tasks · memories · collections   │  │
        │               │                     │  notify · ask · digest · events   │  │
        ▼               │                     └───┬──────────────┬────────────────┘  │
  memgallery-mcp        │                         │              │                   │
   (Node, stdio+HTTP)   │              ┌──────────▼───┐   ┌──────▼────────────┐      │
        │               │              │ HTTP + SSE   │   │ Outbox / Sync     │      │
        ├──── direct ───┼─────────────►│ :8790        │   │ worker            │      │
        │  (Tailscale)  │              │ bearer token │   └──────┬────────────┘      │
        │               │              └──────────────┘          │                   │
        │               └────────────────────────────────────────┼───────────────────┘
        │                                                        │  (plain internet,
        └──── fallback ──────► ┌──────────────────────┐ ◄────────┘   no Tailscale)
                               │  HUB (on A.O.S box)  │
                               │  · read-only mirror  │
                               │  · command outbox    │
                               │  · push relay (ntfy) │
                               └──────────────────────┘
```

Two paths, one API surface. `memgallery-mcp` tries the phone directly first (Tailscale IP, ~10ms), falls back to the hub. The agent never has to know which one answered — but the response carries `"source": "device" | "mirror"` and `"as_of"` so an agent can tell how fresh the data is.

---

## 3. Layer A — the `AgentApi` facade

A single Kotlin interface. Every transport calls it; no transport contains business logic. This is the thing that keeps the design honest — if a capability isn't on `AgentApi`, it isn't exposed anywhere.

```kotlin
interface AgentApi {
    // read
    suspend fun digest(): Digest                       // "what is he up to right now"
    suspend fun listTasks(q: TaskQuery): Page<Task>
    suspend fun getTask(id: Int): Task?
    suspend fun listMemories(q: MemoryQuery): Page<Memory>
    suspend fun getMemory(id: Int): Memory?
    suspend fun getMemoryMedia(id: Int, index: Int): MediaBlob?
    suspend fun listCollections(): List<Collection>
    suspend fun listChats(q: ChatQuery): Page<Chat>
    suspend fun search(q: String, scope: Set<Entity>): SearchResult

    // write
    suspend fun createTask(req: CreateTask, client: AgentClient): Task
    suspend fun updateTask(id: Int, patch: TaskPatch, client: AgentClient): Task
    suspend fun completeTask(id: Int, client: AgentClient): Task
    suspend fun deleteTask(id: Int, client: AgentClient)
    suspend fun createMemory(req: CreateMemory, client: AgentClient): Memory   // runs the normal AI pipeline
    suspend fun createCollection(...): Collection
    suspend fun addToCollection(...)

    // operator attention
    suspend fun notify(req: NotifyRequest, client: AgentClient): NotificationRef
    suspend fun ask(req: AskRequest, client: AgentClient): Ask          // returns immediately
    suspend fun pollAsk(id: String, waitSec: Int): Ask                  // long-poll for the answer

    // streaming
    fun events(filter: Set<EventType>): Flow<AgentEvent>
}
```

`AgentClient` is the authenticated caller — carried through so every write is attributable (`source = "AGENT:aos-mike"`) and so the in-app audit log can show *who* did what.

---

## 4. Layer B — transports

### 4.1 Embedded HTTP + SSE server

- **Library:** Ktor CIO server (~1.3 MB, Kotlin-native, coroutine-friendly, no servlet baggage). NanoHTTPD is smaller but has no SSE and a thread-per-request model I don't want here.
- **Runs in:** a foreground service (`AgentBridgeService`, `foregroundServiceType="dataSync"`), with a persistent low-priority notification — Android will not let a long-lived socket survive otherwise. Starts on boot via the existing `BootReceiver` if the toggle is on.
- **Binds:** `0.0.0.0:8790`. Reachable at the phone's Tailscale 100.x address, and on LAN.
- **Auth:** `Authorization: Bearer <token>`. Tokens are created in-app (Settings → *Agent Access*), stored hashed (Argon2 or SHA-256+salt via `androidx.security.crypto`), each with a name, scopes (`read` / `write` / `notify` / `admin`), created/last-seen timestamps, and a revoke button. **The server refuses to bind until at least one token exists.**
- **Network policy:** by default accept only loopback + the Tailscale CGNAT range `100.64.0.0/10`. A separate explicit toggle ("Allow local network") opens it to RFC1918. Never anything else.
- **Pairing:** the app shows a QR code containing `{baseUrl, token, deviceId, fingerprint}`; the MCP server has a `memgallery-mcp pair` command that reads it from a screenshot/paste. One-step setup, no hand-copied secrets.

### 4.2 Endpoint surface

```
GET   /v1/meta                      app+api version, device name, capability flags
GET   /v1/openapi.json              the spec, served by the app itself
GET   /v1/health

GET   /v1/digest                    ← the money endpoint (see §5)

GET   /v1/tasks                     ?status,&due_before,&due_after,&project,&approved,&q,&limit,&cursor
POST  /v1/tasks                     supports requires_approval:true
GET   /v1/tasks/{id}
PATCH /v1/tasks/{id}
POST  /v1/tasks/{id}/complete
POST  /v1/tasks/{id}/approve | /reject
DELETE /v1/tasks/{id}

GET   /v1/memories                  ?q,&from,&to,&type,&collection,&has_media,&limit,&cursor
POST  /v1/memories                  {text | url | image(base64/multipart) | audio} → queues the AI pipeline
GET   /v1/memories/{id}
GET   /v1/memories/{id}/media/{n}   raw bytes
PATCH /v1/memories/{id}   DELETE /v1/memories/{id}

GET   /v1/collections   POST /v1/collections
POST  /v1/collections/{id}/memories

GET   /v1/chats   GET /v1/chats/{id}/messages

POST  /v1/notify                    fire-and-forget notification
POST  /v1/ask                       ask the operator something, get an answer back
GET   /v1/ask/{id}?wait=30          long-poll

GET   /v1/events                    SSE: task.*, memory.*, ask.answered, collection.*
POST  /v1/webhooks                  register url + event types; HMAC-SHA256 signed deliveries
GET   /v1/webhooks   DELETE /v1/webhooks/{id}
```

Cursor pagination everywhere (`?cursor=` + `next_cursor`), `If-None-Match`/ETag on reads, and every response envelope carries `{data, source, as_of, next_cursor}`.

### 4.3 Notify and Ask — the part that matters on the go

**`POST /v1/notify`**
```json
{ "title": "Deploy finished", "body": "growos-app is live on prod",
  "priority": "high", "deep_link": "memgallery://detail/412",
  "actions": [{"id":"open","label":"Open"},{"id":"snooze","label":"Snooze 1h"}] }
```
Posts on a dedicated `agent_alerts` channel so he can mute agents without muting MemGallery's own reminders. Action taps come back as events.

**`POST /v1/ask`** — this is the "throw a modal in front of me while I'm out" primitive.
```json
{ "kind": "choice",
  "question": "Ruan's lender-selector is failing on prod. Roll back or hotfix?",
  "options": ["roll back", "hotfix", "leave it"],
  "context": "3 failed deploys since 14:02",
  "timeout_sec": 3600 }
```
- `kind: confirm` → notification with **Yes / No** action buttons, answered from the shade without unlocking.
- `kind: choice` → up to 3 buttons in the shade, full list in the app.
- `kind: text` → Android `RemoteInput` on the notification, so he can type a reply straight from the shade.
- Unanswered asks live in an **Agent Inbox** screen in-app, next to the existing unapproved-tasks sheet.
- Agent side: `POST /v1/ask` returns `{id, status:"pending"}` immediately, then either long-polls `GET /v1/ask/{id}?wait=30` or receives `ask.answered` on SSE/webhook. Timeout returns `status:"expired"`.

Tasks created with `requires_approval: true` reuse `isApproved=false` — they land in the existing sheet, and approval/rejection emits `task.approved` / `task.rejected` back to the agent. No new UI needed for that path.

### 4.4 The no-Tailscale story

Be honest about the physics: an HTTP server on the phone is reachable only when the phone is reachable. So split the problem.

**Outbound (phone → world) always works**, on any network, no Tailscale. So:

- **Read-mirror.** On every change (debounced) and on a 15-min WorkManager tick, the app pushes a compact snapshot — all tasks, memory *metadata* (no blobs), collections, digest — to the hub. Agents can then answer "what is he up to" **instantly, even with the phone off**, and the response is stamped `source: "mirror", as_of: <ts>`.
- **Outbound notifications never needed a hub at all** for the ntfy path — the app can post directly, and agents can too.

**Inbound (world → phone) needs a rendezvous.** Three tiers, tried in order:

1. **Tailscale direct** — full API, real-time, sub-100ms. The good case.
2. **Push-wake + outbox** — agent writes the command to the hub's outbox and fires a push; the phone wakes, dials *out* to the hub, drains the queue, posts results back. Latency seconds to a minute. Works on cellular, hotel wifi, anything.
3. **WorkManager poll** — every 15 min minimum (Android's floor). Always works, never fails, slow. The floor under everything.

**Push transport — the one real decision.** Options, ranked:

| Option | Doze-proof | Private | Cost | Notes |
|---|---|---|---|---|
| **ntfy via UnifiedPush** | yes, if the ntfy app is installed as distributor | yes — self-hostable | free | **Recommended.** No Google dependency, no extra battery drain, and the payload can be an opaque encrypted wake-ping. Self-host on the A.O.S box later; start on public `ntfy.sh` with a random topic. |
| **FCM** | yes, best-in-class | no — Google sees delivery metadata | free | Most reliable on stock Android. Data-only messages, zero content, just "wake and sync". Ship as an optional build flavour so the F-Droid-ish/zero-cloud build stays clean. |
| **App's own websocket** | no — OEM battery managers kill it | yes | free | Only viable while a foreground service runs. Fine on a charger, unreliable in a pocket. |
| **Poll only** | n/a | yes | free | The floor. 15-min latency. |

**Decided (D1):** ntfy — UnifiedPush when the ntfy app is installed, otherwise a websocket held by the already-running `AgentBridgeService` — with the WorkManager poll as the floor. No FCM in v1. Broker is public `ntfy.sh` on a random topic, and the payload is an encrypted wake-ping with no content, no titles, no IDs. Self-hosting the broker is explicitly *not* the answer: a broker on the A.O.S box isn't reachable when the phone is off Tailscale, which is the whole point of this tier.

**Hub (D2): built, and exposed with `tailscale funnel`.** Without it, "works when Tailscale is off" doesn't exist, and neither does "answer questions about my tasks while my phone is in a drawer." Funnel gives the hub a stable public HTTPS name with no domain, no port forwarding and no third-party account — reusing infrastructure already on the box. The phone reaches it from any network; when it happens to be on the tailnet it dials the tailnet address directly and skips the Funnel hop. Cloudflare Tunnel is the documented fallback. The hub stays dumb — mirror, queue, relay, nothing else.

---

## 5. `/v1/digest` — one call for "what is Ayanda up to"

The single most useful endpoint for an agent, and the reason not to make agents stitch five queries:

```json
{
  "as_of": "2026-08-06T14:32:11Z", "source": "device",
  "now": { "date": "2026-08-06", "tz": "Africa/Johannesburg" },
  "overdue":        [ {task}, ... ],
  "due_today":      [ {task}, ... ],
  "due_this_week":  [ {task}, ... ],
  "events_today":   [ {task, type:"EVENT"}, ... ],
  "awaiting_approval": { "tasks": 3, "asks": 1 },
  "recent_memories": [ {id, title, summary, tags, kind, created} x10 ],
  "processing":     { "pending": 0, "failed": 1 },
  "activity":       { "memories_today": 4, "tasks_completed_today": 2, "streak_days": 11 }
}
```

Cheap to compute, cheap to mirror, and it's exactly what an agent needs before deciding whether to interrupt him.

---

## 6. Layer C — `memgallery-mcp`

A TypeScript package living in this repo at `connectors/mcp/` and published to npm (D6), so any machine installs it with `npx memgallery-mcp`. Runs as **stdio** for local agents and **streamable HTTP** for remote ones. Config: `MEMGALLERY_URL`, `MEMGALLERY_TOKEN`, optional `MEMGALLERY_HUB_URL`. Nothing in it knows what A.O.S is — Claude Code, Codex, Cursor and Cline point at the same binary with the same token.

Tools:

```
memgallery_digest              memgallery_notify
memgallery_list_tasks          memgallery_ask            (blocking, with a timeout)
memgallery_create_task         memgallery_search
memgallery_update_task         memgallery_list_memories
memgallery_complete_task       memgallery_get_memory
memgallery_approve_task        memgallery_create_memory
memgallery_list_collections    memgallery_create_collection
```

MCP **resources** (so agents can pull context without a tool call):
`memgallery://digest`, `memgallery://tasks/today`, `memgallery://tasks/overdue`, `memgallery://memories/recent`

MCP **prompts**: `whats-on-my-plate`, `triage-unapproved`.

For non-MCP consumers: the OpenAPI spec is served by the app at `/v1/openapi.json` and mirrored in the repo, which is enough for curl, n8n, a Composio custom tool, or a GPT action.

---

## 7. Schema changes

Room **v17 → v18**, all additive with the existing try/catch migration style:

**New tables**
- `agent_clients` — id, name, tokenHash, salt, scopes, createdAt, lastSeenAt, revoked
- `agent_asks` — id, clientId, kind, question, contextText, optionsJson, answer, status (pending/answered/expired/dismissed), createdAt, expiresAt, answeredAt
- `agent_events` — outbox: id, type, payloadJson, createdAt, deliveredAt, attempts
- `agent_webhooks` — id, url, eventTypes, secret, createdAt, lastStatus
- `agent_audit` — id, clientId, action, entity, entityId, at  *(so the in-app audit screen can show what agents did)*

**`TaskEntity` additions**
- `source: String = "USER"` — `USER` / `AI` / `AGENT:<client name>`
- `externalId: String?` — lets an agent upsert idempotently against its own IDs
- `updatedAt: Long` — needed for mirror sync
- `projectId: Int?` — see below

**`CollectionEntity` additions (D3 — collections become projects)**
- `status: String = "ACTIVE"` — `ACTIVE` / `PAUSED` / `DONE`
- `color: Int?`, `targetDate: String?`, `archived: Boolean = false`, `sortOrder: Int = 0`

Tasks point at them via `projectId`. The API's primary noun is `/v1/projects`, with `/v1/collections` kept as an alias so nothing existing breaks. One table, no second screen, no second mental model — and if projects ever need a lifecycle collections shouldn't have, splitting them is a rename plus a migration rather than a redesign.

---

## 8. Manifest / permission changes

```xml
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>  <!-- already present -->
<service android:name=".service.AgentBridgeService"
         android:exported="false"
         android:foregroundServiceType="dataSync"/>
```
Plus `BootReceiver` starting the service when the toggle is on, and a battery-optimisation exemption prompt in the Agent Access setup flow (unavoidable on Samsung/Xiaomi if the server is to survive).

---

## 9. Phasing

| Phase | Deliverable | Why this order | Rough size |
|---|---|---|---|
| **0** | `AgentApi` facade + DTOs + OpenAPI 3.1 spec written **first**, migration 17→18 | Contract before code; everything else is an implementation of this | ~1 day |
| **1** | `AgentBridgeService` (Ktor), token auth, Settings → Agent Access + QR pairing, all read endpoints, `/v1/digest` | The moment this lands, agents on Tailscale can *see* everything | ~2–3 days |
| **2** | Write endpoints, `/v1/notify`, `/v1/ask` + notification actions + RemoteInput, Agent Inbox screen, SSE `/v1/events` | Now agents can act and interrupt — this is the bulk of the value | ~3 days |
| **3** | `memgallery-mcp` package + OpenAPI publish + `pair` command | The "concrete thing" any tool plugs into | ~1–2 days |
| **4** | Hub (mirror + outbox + relay) and push-wake via ntfy/UnifiedPush; WorkManager poll floor | Makes it work without Tailscale and with the phone asleep | ~2–3 days |
| **5** | Outbound webhooks, agent audit screen, per-client rate limits, scope enforcement polish | Hardening | ~1–2 days |

Phases 1–3 alone are already a complete, useful product on Tailscale. Phase 4 is the "on the go" promise.

---

## 10. Risks and how they're handled

| Risk | Handling |
|---|---|
| Android kills the server (OEM battery managers) | Foreground service + boot receiver + battery-optimisation exemption prompt; WorkManager poll as a floor that never depends on the socket |
| A token leaks → full read of a very personal database | Per-client tokens with scopes, revocable, hashed at rest, bound to Tailscale range by default, in-app audit log of every agent action |
| Agents spamming notifications | Per-client rate limit, a dedicated `agent_alerts` channel he can mute independently, quiet-hours setting, and a kill switch in Settings |
| Zero-cloud mandate erosion | Content never leaves the device via a third party — push is a contentless wake-ping. Hub is his own box. FCM stays an optional flavour. Say this in the README. |
| Mirror goes stale and an agent acts on old data | Every response stamped `source` + `as_of`; MCP tool descriptions instruct agents to check freshness before acting; writes always go to the device (queued if offline), never to the mirror |
| Scope creep into a sync product | Hub is deliberately dumb: mirror + queue + relay. The phone stays authoritative. No conflict resolution beyond last-write-wins on `updatedAt`. |

---

## 11. Decisions — LOCKED 2026-08-06

All six calls made. No open questions blocking Phase 0.

### D1 — Push transport: **ntfy, two-tier, no FCM in v1**

- **Primary:** UnifiedPush with ntfy as distributor, *if* the ntfy app is installed. Best battery, doze-proof, zero cost.
- **Built-in fallback:** an ntfy websocket held open by `AgentBridgeService` — the foreground service is **already running for the HTTP server**, so the subscription is essentially free. No extra service, no extra wakelock.
- **Floor:** WorkManager 15-min poll, always on, independent of both.
- **FCM:** not built. A `push-fcm` source set stub is left in place so it can be added as a build flavour later if latency ever disappoints. Google gets nothing today.
- **Broker:** public `ntfy.sh`, random 32-char topic, payload is an **encrypted wake-ping** — literally `{"v":1,"n":<nonce>}` sealed with a device key. No titles, no bodies, no IDs. Self-hosting ntfy is deliberately *not* the answer here: a self-hosted broker on the A.O.S box isn't reachable when the phone is off Tailscale, which is exactly the case this tier exists to solve.

### D2 — Hub: **build it, and expose it with Tailscale Funnel**

- Hub is built. It stays dumb: read-mirror + command outbox + event fan-out. No business logic, no conflict resolution beyond last-write-wins on `updatedAt`. Node/TS + SQLite, ~300 lines.
- **Reachability:** `tailscale funnel` gives it a stable public HTTPS name (`hub-xxxx.tailXXXX.ts.net`) with no domain, no port forwarding, no Cloudflare account — using infrastructure already on the box. The phone reaches it **from any network whether or not the phone is on the tailnet**; when the phone *is* on the tailnet it dials the tailnet address directly and skips the Funnel hop.
- Cloudflare Tunnel documented as the fallback if Funnel is ever unavailable.
- Auth on the hub is the same bearer-token scheme as the device, plus an HMAC device key so the hub can't forge device writes.

### D3 — Projects: **Collections promoted to first-class containers**

One entity, project semantics. `CollectionEntity` gains `status`, `color`, `targetDate`, `archived`, `sortOrder`; `TaskEntity` gains `projectId: Int?` referencing it. The API exposes `/v1/projects` as the primary noun with `/v1/collections` as an alias, so agents get the vocabulary asked for without a second table, a second screen, or a second mental model. If projects later need a lifecycle collections shouldn't have, splitting them is a rename plus a migration — not a redesign.

### D4 — Media: **capable, but off by default**

Agents can pull real bytes, gated behind an explicit `media:read` scope that is **not** in the default token scope set — it must be ticked per client when the token is created. Bytes are served **only from the device**, never mirrored to the hub, via short-lived (60 s) signed URLs. Memory metadata + `aiSummary` + `aiImageAnalysis` + `aiAudioTranscription` are in the default `read` scope, which is enough for almost every agent task without shipping his photos anywhere.

### D5 — Write scope: **agents may create memories, with a cost brake**

Creating memories is core value ("save this for me while I'm driving"), so it's in. Guards:
- `POST /v1/memories` takes `process: true|false` — `false` stores it raw and it processes later like any other pending memory.
- Per-client daily cap on AI-processed creates (default 50), configurable, surfaced in Settings.
- Settings kill switch: *"Agents may run AI processing"* — default **on** (his keys, his agents), one tap to off.
- Every agent-created memory and task is tagged `source = "AGENT:<client>"` and appears in the in-app audit log.

### D6 — Connector location: **in this repo, published to npm**

Source lives here as `connectors/mcp/` and `connectors/hub/` — one repo, one PR changes the API and its client together, version-locked to the API. Both publish to npm, so any machine anywhere installs with:

```
npx memgallery-mcp pair        # scan the QR from the app
npx memgallery-hub             # on the box, once
```

Nothing about either package knows what A.O.S is. Claude Code, Codex, Cursor, Cline, or anything else that speaks MCP points at the same binary with the same token.

### Consequences for the phasing in §9

- Phase 0 additionally covers the `CollectionEntity` field additions and `projectId` (D3), so migration 17→18 is written **once**.
- Phase 3 grows the `connectors/` workspace and npm publishing.
- Phase 4 is now specifically: hub + Tailscale Funnel + ntfy two-tier + poll floor.
- Scopes shipped in Phase 1: `read`, `write`, `notify`, `media:read`, `admin`. `media:read` off by default.

---

## 12. What shipped

Built 2026-08-06. `:app:assembleDebug` green; both connectors typecheck, build and were
smoke-tested end to end.

### In the app

| Area | Files |
|---|---|
| Contract | `agent/AgentApi.kt` (40 methods), `agent/model/{AgentCore,AgentDomain,AgentQueries,AgentRequests,AgentEvents}.kt`, `agent/AgentMappers.kt` |
| Implementation | `agent/AgentApiImpl.kt` |
| Security | `agent/AgentTokens.kt` — PBKDF2 + constant-time verify; prefix-narrowed lookup |
| Transport | `agent/server/{AgentServer,AgentJson}.kt`, `service/AgentBridgeService.kt` |
| Attention | `agent/AgentNotifier.kt`, `receiver/AgentActionReceiver.kt` |
| Events | `agent/AgentEventBus.kt` — outbox-first, SSE + HMAC webhooks |
| Offline | `agent/sync/{HubSync,MirrorSyncWorker}.kt`, `agent/push/PushWakeManager.kt` |
| Settings | `agent/AgentBridgeSettings.kt`, `ui/screens/AgentAccessScreen.kt`, `ui/viewmodels/AgentAccessViewModel.kt` |
| Schema | 5 agent tables + `MIGRATION_17_18`; `TaskEntity` and `CollectionEntity` extended |

### Outside the app

- `connectors/openapi.yaml` — 26 paths, 34 schemas; bundled into assets and served at `/v1/openapi.json`
- `connectors/mcp` — 17 tools, 3 resources, 2 prompts; stdio; device-first with hub fallback
- `connectors/hub` — mirror + outbox + doorbell; **zero dependencies**

### Verified, not assumed

- `:app:assembleDebug` — BUILD SUCCESSFUL, including Room KSP validation of every new DAO query
- OpenAPI parses as 3.1 with no broken `$ref`s
- MCP: handshake + `tools/list` returns all 17 over real stdio
- Hub: full round trip — auth rejection, mirror push, mirror read with correct `as_of`,
  `POST /queue` → `GET /device/commands` → `POST /device/results` → `GET /queue/:id`
- Fallback: with the device unreachable, `memgallery_digest` returned mirror data labelled
  *"from the hub MIRROR, 252s old"*, and `memgallery_create_task` queued the exact
  `create_task` op that `HubSync` dispatches

### Not done, and deliberately

- **QR pairing.** The screen prints a `npx memgallery-mcp pair …` command instead. Same
  one-step setup, no camera permission, and it can be pasted over SSH.
- **UnifiedPush distributor registration.** The ntfy websocket path is live and costs
  nothing extra inside the already-running foreground service. UnifiedPush is a battery
  optimisation on top, worth adding once there's evidence the socket is the problem.
- **On-device test of the migration.** It is additive and guarded, but it has not run
  against a real 0.3.4 database. Back up before first launch.
