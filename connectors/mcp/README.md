# memgallery-mcp

An MCP server for MemGallery. Point any MCP client at it — Claude Code, Codex, Cursor,
Cline, A.O.S — and it gets 17 typed tools over your own phone's task list, memories and
notifications.

Nothing in this package is specific to any agent framework. It speaks the REST API the
Android app serves; the app has never heard of MCP.

## Setup

1. In MemGallery: **Settings → Agent Access**, create a token. It is shown once.
2. On the machine where your agent runs:

```bash
npx memgallery-mcp pair --url http://<device-ip>:8790 --token mg_...
```

The device IP is printed on the Agent Access screen — it prefers your Tailscale
(`100.x.x.x`) address, because that's the one reachable from anywhere.

3. Add to your MCP client config:

```json
{
  "mcpServers": {
    "memgallery": { "command": "npx", "args": ["-y", "memgallery-mcp"] }
  }
}
```

### Optional: keep working when the phone is unreachable

```bash
npx memgallery-mcp pair \
  --url http://100.x.x.x:8790 --token mg_... \
  --hub-url https://hub-xxxx.tailnet.ts.net --hub-token <hub token>
```

With a hub configured, reads fall back to its mirror and writes queue for the phone to
apply when it next dials out. See `../hub`.

## Configuration

File (`~/.memgallery/config.json`) or environment; environment wins.

| Variable | Meaning |
|---|---|
| `MEMGALLERY_URL` | The device, e.g. `http://100.64.1.2:8790` |
| `MEMGALLERY_TOKEN` | Token from Agent Access |
| `MEMGALLERY_HUB_URL` | Optional relay |
| `MEMGALLERY_HUB_TOKEN` | Hub token; defaults to the device token |
| `MEMGALLERY_DEVICE_TIMEOUT_MS` | How long to wait for the phone before falling back. Default 2500 |

## Tools

**Orientation** — `memgallery_digest`, `memgallery_search`

**Tasks** — `list_tasks`, `create_task`, `update_task`, `complete_task`, `delete_task`,
`pending_approval`, `approve_task`

**Memories** — `list_memories`, `get_memory`, `create_memory`

**Projects** — `list_projects`, `create_project`

**Attention** — `notify`, `ask`, `pending_asks`

Also exposes resources (`memgallery://digest`, `memgallery://tasks/today`,
`memgallery://memories/recent`) so an agent can pull context without spending a tool call,
and two prompts: `whats-on-my-plate` and `triage-unapproved`.

## Three things worth knowing before you build on this

**Every result says where it came from.** The first line of every tool result is either
`[live from the device]`, `[from the hub MIRROR, 252s old …]`, or `[QUEUED at the hub …]`.
That isn't decoration — a mirror read can be minutes stale, and completing a task the
operator already deleted is the exact failure this design exists to prevent.

**`requires_approval` is the polite mode.** Setting it makes `create_task` a *proposal*:
it lands in an approval inbox on the phone, the operator accepts or bins it, and you get
`task.approved` or `task.rejected` back. Use it whenever you're adding work to someone
else's day off your own initiative.

**A pending or expired `ask` is not a "no".** It means he hasn't seen the notification.
Treating silence as refusal is how an agent talks itself into the wrong branch.

## Development

```bash
npm install
npm run build
npm start
```

The contract lives in `../openapi.yaml`, and the app serves its own copy at
`GET /v1/openapi.json` — fetch that from the device you're about to talk to rather than
trusting a checked-in copy that may have drifted.
