# MemGallery connectors

Everything outside the Android app that lets agents talk to MemGallery.

```
connectors/
├── openapi.yaml   ← the contract. Served by the app itself at /v1/openapi.json
├── mcp/           ← memgallery-mcp   (phase 3)
└── hub/           ← memgallery-hub   (phase 4)
```

## Why this exists

The Android app ships one thing: a documented REST API on the device. It has no idea
what MCP is and no idea what A.O.S is. These packages are the adapters — and because the
adapter layer is where framework-specific knowledge lives, adding a new one never means
touching the app.

- **`mcp/`** — an MCP server over stdio and streamable HTTP. Point Claude Code, Codex,
  Cursor, Cline or A.O.S at it and they all get the same ~16 typed tools.
- **`hub/`** — a deliberately dumb relay on a machine that's always on: a read-only
  mirror so agents can answer "what is he up to" while the phone is in a drawer, a
  command outbox for writes that arrive while it's unreachable, and a push relay.
  No business logic. The phone stays authoritative.

## Status

| Package | Phase | State |
|---|---|---|
| `openapi.yaml` | 0 | 26 paths, 34 schemas; served by the app at `/v1/openapi.json` |
| `mcp/` | 3 | built — 17 tools, 3 resources, 2 prompts |
| `hub/` | 4 | built — mirror + outbox + doorbell, zero dependencies |

## Quick start

```bash
# on the machine where your agent runs
npx memgallery-mcp pair --url http://<device-ip>:8790 --token mg_...

# optionally, on an always-on box, so agents keep working with the phone asleep
export MEMGALLERY_HUB_TOKEN=$(openssl rand -base64 32)
npx memgallery-hub && tailscale funnel 8791
```

## The contract

`openapi.yaml` is the single source of truth for the wire format. The app serves it at
`GET /v1/openapi.json` so a client can fetch the spec from the device it's about to talk
to, rather than trusting a copy that may have drifted.

Two things in it matter more than they look:

**`source` and `as_of` on every read.** A response may come from the device or from the
hub's mirror. Mirror reads can be minutes stale. Check before acting — completing a task
the operator already deleted is exactly how that goes wrong.

**`requires_approval` on task creation.** It's the difference between an agent adding
work to someone's day and an agent *proposing* it. The task lands in his approval inbox
on the phone, he accepts or bins it, and `task.approved` / `task.rejected` comes back.

## Auth

Bearer tokens, created in the app under Settings → Agent Access, shown exactly once,
stored hashed, revocable, scoped:

| Scope | Grants |
|---|---|
| `read` | tasks, projects, chats, memory metadata incl. AI summaries and transcriptions |
| `write` | create and mutate tasks, projects, memories |
| `notify` | post notifications, ask questions |
| `media:read` | raw photo/audio bytes — **not granted by default** |
| `admin` | manage clients and webhooks; implies the rest |

See `../DOCS/AGENT_BRIDGE_PLAN.md` for the full architecture and the six locked decisions.
