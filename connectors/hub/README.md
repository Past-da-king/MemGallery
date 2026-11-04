# memgallery-hub

The always-on half of MemGallery's "works without Tailscale" story. Zero dependencies —
`npx memgallery-hub` and it runs.

## What it's for

An HTTP server on a phone is reachable only while the phone is reachable. Tailscale
covers most of that, but not a phone in a drawer, in a tunnel, or off the tailnet. So the
problem splits in two:

- **Reads** come from a **mirror**. The phone pushes a compact snapshot — the digest,
  tasks, projects, memory metadata, chats — on every change and on a 15-minute tick. An
  agent can answer *"what is he up to"* instantly with the phone switched off.
- **Writes** go into an **outbox**. An agent queues the command here; the hub rings a
  contentless doorbell; the phone wakes, dials *out*, drains the queue and posts results
  back. That works on cellular, hotel wifi, anything that allows outbound HTTPS.

The hub is deliberately stupid. It stores, it queues, it rings. It has no business logic
and no opinions about MemGallery's domain — every one of those decisions belongs on the
device, where the data actually is. **If this codebase ever starts looking clever,
something has been put in the wrong place.**

## Run it

```bash
export MEMGALLERY_HUB_TOKEN=$(openssl rand -base64 32)
export MEMGALLERY_NTFY_TOPIC=memgallery-$(openssl rand -hex 12)
npx memgallery-hub
```

Then expose it:

```bash
tailscale funnel 8791
```

Funnel gives a stable public HTTPS name (`https://<host>.<tailnet>.ts.net`) with no
domain, no port forwarding and no third-party account — reusing infrastructure you
already run. The phone reaches it from **any** network; when it happens to be on the
tailnet it dials the tailnet address directly and skips the Funnel hop.

Cloudflare Tunnel works equally well if Funnel isn't available.

Finally, put the URL, token and topic into MemGallery under **Settings → Agent Access →
Hub**.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MEMGALLERY_HUB_TOKEN` | *required* | Bearer token for both the device and agents |
| `MEMGALLERY_HUB_PORT` | `8791` | Listen port |
| `MEMGALLERY_HUB_STATE` | `~/.memgallery/hub-state.json` | Mirror + queue, written atomically |
| `MEMGALLERY_NTFY_SERVER` | `https://ntfy.sh` | Doorbell broker |
| `MEMGALLERY_NTFY_TOPIC` | *(unset — doorbell disabled)* | Use a long random topic |

## Endpoints

**Device-facing**

| | |
|---|---|
| `POST /device/mirror` | Store the latest snapshot |
| `GET /device/commands` | Collect queued commands |
| `POST /device/results` | Report what happened |

**Agent-facing**

| | |
|---|---|
| `GET /v1/digest`, `/v1/tasks`, `/v1/projects`, `/v1/memories`, `/v1/chats` | Mirror reads, in the device's own envelope shape |
| `POST /queue` | Queue a write; returns an id |
| `GET /queue/:id` | `queued` → `delivered` → `applied`/`failed` |
| `GET /health` | Unauthenticated liveness |

Anything the mirror can't answer returns `503 unavailable` with a message saying so,
rather than a plausible-looking empty result. Media bytes are never mirrored — those stay
on the device behind the `media:read` scope.

## On the doorbell

The ntfy payload is `{"v":1,"n":"<random uuid>"}` and nothing else. No title, no body, no
ids. A third-party broker learns that *something* is waiting, never what.

Self-hosting the broker sounds more private and is actually worse: a broker on your own
machine is unreachable in precisely the situation this tier exists to handle — the phone
being off your network.

## Development

```bash
npm install
npm run build
npm start
```
