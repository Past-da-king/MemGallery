#!/usr/bin/env node
import { createServer, IncomingMessage, ServerResponse } from "node:http";
import { readFileSync, writeFileSync, renameSync, existsSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join, dirname } from "node:path";
import { randomUUID, timingSafeEqual } from "node:crypto";

/**
 * MemGallery Hub — the always-on half of the "works without Tailscale" story.
 *
 * Deliberately stupid. It stores the last mirror the phone pushed, it queues commands the
 * phone hasn't collected yet, and it rings a doorbell. It contains no business logic, no
 * validation of MemGallery's domain, and no opinions: every one of those decisions belongs
 * on the device, where the actual data is. If this file ever starts to look clever,
 * something has been put in the wrong place.
 *
 * Expose it with `tailscale funnel 8791` — that gives a stable public HTTPS name with no
 * domain, no port forwarding and no third-party account, so the phone can reach it from
 * any network whether or not it's on the tailnet.
 */

const VERSION = "0.1.0";
const PORT = Number(process.env.MEMGALLERY_HUB_PORT ?? 8791);
const TOKEN = process.env.MEMGALLERY_HUB_TOKEN;
const STATE_PATH = process.env.MEMGALLERY_HUB_STATE ?? join(homedir(), ".memgallery", "hub-state.json");
const NTFY_SERVER = (process.env.MEMGALLERY_NTFY_SERVER ?? "https://ntfy.sh").replace(/\/$/, "");
const NTFY_TOPIC = process.env.MEMGALLERY_NTFY_TOPIC;

if (!TOKEN) {
  console.error(
    "MEMGALLERY_HUB_TOKEN is required.\n\n" +
      "  export MEMGALLERY_HUB_TOKEN=$(openssl rand -base64 32)\n" +
      "  export MEMGALLERY_NTFY_TOPIC=memgallery-$(openssl rand -hex 12)\n" +
      "  npx memgallery-hub\n\n" +
      "Then put the same URL, token and topic into MemGallery under Settings → Agent Access.",
  );
  process.exit(1);
}

interface QueuedCommand {
  id: string;
  op: string;
  args: unknown;
  queuedAt: number;
  deliveredAt?: number;
  result?: { ok: boolean; data?: unknown; error?: string; at: number };
}

interface HubState {
  mirror: Record<string, unknown> | null;
  mirrorAt: number;
  deviceId: string | null;
  deviceSeenAt: number;
  queue: QueuedCommand[];
}

function loadState(): HubState {
  if (existsSync(STATE_PATH)) {
    try {
      return JSON.parse(readFileSync(STATE_PATH, "utf8")) as HubState;
    } catch {
      console.error(`Could not parse ${STATE_PATH}; starting empty.`);
    }
  }
  return { mirror: null, mirrorAt: 0, deviceId: null, deviceSeenAt: 0, queue: [] };
}

const state = loadState();

/**
 * Write to a temp file then rename. A half-written state file after a power cut would
 * lose the queue, and the queue is the only copy of a command the agent believes was
 * accepted.
 */
let saveTimer: NodeJS.Timeout | null = null;
function save(): void {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      mkdirSync(dirname(STATE_PATH), { recursive: true });
      const tmp = `${STATE_PATH}.tmp`;
      writeFileSync(tmp, JSON.stringify(state), { mode: 0o600 });
      renameSync(tmp, STATE_PATH);
    } catch (error) {
      console.error("Failed to persist hub state:", error);
    }
  }, 200);
}

function authorised(req: IncomingMessage): boolean {
  const header = req.headers.authorization;
  if (!header?.startsWith("Bearer ")) return false;
  const presented = Buffer.from(header.slice(7).trim());
  const expected = Buffer.from(TOKEN!);
  if (presented.length !== expected.length) return false;
  return timingSafeEqual(presented, expected);
}

function json(res: ServerResponse, status: number, body: unknown): void {
  const text = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(text) });
  res.end(text);
}

async function readBody(req: IncomingMessage): Promise<any> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    // The mirror is metadata only; anything this large is a bug or an attack.
    if (size > 32 * 1024 * 1024) throw new Error("Body too large");
    chunks.push(chunk as Buffer);
  }
  if (chunks.length === 0) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

/**
 * The doorbell. Contentless by design: a third-party broker learns that something is
 * waiting, never what it is.
 */
async function ring(): Promise<void> {
  if (!NTFY_TOPIC) return;
  try {
    await fetch(`${NTFY_SERVER}/${NTFY_TOPIC}`, {
      method: "POST",
      headers: { "X-Priority": "high", "X-Title": "" },
      body: JSON.stringify({ v: 1, n: randomUUID() }),
    });
  } catch (error) {
    console.error("Wake ping failed:", error instanceof Error ? error.message : error);
  }
}

function mirrorSection(name: string): unknown {
  if (!state.mirror) return null;
  return (state.mirror as Record<string, unknown>)[name] ?? null;
}

function envelope(data: unknown) {
  return {
    data,
    source: "mirror" as const,
    as_of: state.mirrorAt,
    api_version: "1.0",
    next_cursor: null,
  };
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://localhost:${PORT}`);
  const path = url.pathname.replace(/\/$/, "") || "/";

  if (path === "/health") {
    return json(res, 200, {
      ok: true,
      version: VERSION,
      device_seen_at: state.deviceSeenAt,
      mirror_at: state.mirrorAt,
      queued: state.queue.filter((c) => !c.result).length,
    });
  }

  if (!authorised(req)) {
    return json(res, 401, { code: "unauthorized", message: "Missing or invalid bearer token." });
  }

  try {
    // ---------------------------------------------------------------
    // Device-facing: the phone pushing state and collecting work
    // ---------------------------------------------------------------

    if (req.method === "POST" && path === "/device/mirror") {
      const body = await readBody(req);
      state.mirror = body;
      state.mirrorAt = typeof body?.as_of === "number" ? body.as_of : Date.now();
      state.deviceId = body?.device_id ?? state.deviceId;
      state.deviceSeenAt = Date.now();
      save();
      return json(res, 200, { ok: true });
    }

    if (req.method === "GET" && path === "/device/commands") {
      state.deviceSeenAt = Date.now();
      const pending = state.queue.filter((c) => !c.result && !c.deliveredAt);
      const now = Date.now();
      for (const command of pending) command.deliveredAt = now;
      save();
      return json(res, 200, {
        commands: pending.map((c) => ({ id: c.id, op: c.op, args: c.args })),
      });
    }

    if (req.method === "POST" && path === "/device/results") {
      const body = await readBody(req);
      const results: Array<{ id: string; ok: boolean; data?: unknown; error?: string }> = body?.results ?? [];
      const at = Date.now();
      for (const result of results) {
        const command = state.queue.find((c) => c.id === result.id);
        if (command) command.result = { ok: result.ok, data: result.data, error: result.error, at };
      }
      // Keep settled commands for an hour so a polling agent can still collect the answer.
      const cutoff = at - 60 * 60 * 1000;
      state.queue = state.queue.filter((c) => !c.result || c.result.at > cutoff);
      state.deviceSeenAt = at;
      save();
      return json(res, 200, { ok: true });
    }

    // ---------------------------------------------------------------
    // Agent-facing: mirror reads and queued writes
    // ---------------------------------------------------------------

    if (req.method === "POST" && path === "/queue") {
      const body = await readBody(req);
      if (!body?.op) return json(res, 400, { code: "invalid_request", message: "'op' is required." });
      const command: QueuedCommand = {
        id: randomUUID(),
        op: String(body.op),
        args: body.args ?? {},
        queuedAt: Date.now(),
      };
      state.queue.push(command);
      save();
      void ring();
      return json(res, 202, { data: { id: command.id, status: "queued" }, source: "queued", as_of: Date.now() });
    }

    if (req.method === "GET" && path.startsWith("/queue/")) {
      const id = path.slice("/queue/".length);
      const command = state.queue.find((c) => c.id === id);
      if (!command) return json(res, 404, { code: "not_found", message: "No such queued command." });
      return json(res, 200, {
        data: command.result
          ? { id, status: command.result.ok ? "applied" : "failed", result: command.result.data, error: command.result.error }
          : { id, status: command.deliveredAt ? "delivered" : "queued" },
        source: "mirror",
        as_of: Date.now(),
      });
    }

    if (req.method === "GET" && path.startsWith("/v1/")) {
      if (!state.mirror) {
        return json(res, 503, {
          code: "unavailable",
          message: "No mirror yet — the device has never checked in.",
        });
      }
      const resource = path.slice("/v1/".length);

      if (resource === "digest") return json(res, 200, envelope(mirrorSection("digest")));
      if (resource === "tasks") return json(res, 200, envelope(mirrorSection("tasks")));
      if (resource === "projects" || resource === "collections") {
        return json(res, 200, envelope(mirrorSection("projects")));
      }
      if (resource === "memories") return json(res, 200, envelope(mirrorSection("memories")));
      if (resource === "chats") return json(res, 200, envelope(mirrorSection("chats")));

      if (resource.startsWith("tasks/")) {
        const id = Number(resource.slice("tasks/".length));
        const task = (mirrorSection("tasks") as any[])?.find((t) => t.id === id);
        return task
          ? json(res, 200, task)
          : json(res, 404, { code: "not_found", message: "Not in the mirror." });
      }
      if (resource.startsWith("memories/")) {
        const id = Number(resource.slice("memories/".length));
        const memory = (mirrorSection("memories") as any[])?.find((m) => m.id === id);
        return memory
          ? json(res, 200, memory)
          : json(res, 404, {
              code: "not_found",
              message: "Not in the mirror. Media and older memories are only on the device.",
            });
      }

      return json(res, 503, {
        code: "unavailable",
        message: `The mirror cannot answer '${resource}'. It carries the digest, tasks, projects, memory metadata and chats — nothing else. Reach the device directly for the rest.`,
      });
    }

    return json(res, 404, { code: "not_found", message: `No route for ${req.method} ${path}` });
  } catch (error) {
    console.error("Request failed:", error);
    return json(res, 500, {
      code: "internal",
      message: error instanceof Error ? error.message : "Internal error",
    });
  }
});

server.listen(PORT, () => {
  console.error(`memgallery-hub ${VERSION} listening on :${PORT}`);
  console.error(`  state:  ${STATE_PATH}`);
  console.error(`  wake:   ${NTFY_TOPIC ? `${NTFY_SERVER}/${NTFY_TOPIC}` : "disabled (set MEMGALLERY_NTFY_TOPIC)"}`);
  console.error(`  expose: tailscale funnel ${PORT}`);
});

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    // Flush synchronously: the debounced save may not have fired, and losing a queued
    // command on shutdown means losing work an agent was told had been accepted.
    try {
      mkdirSync(dirname(STATE_PATH), { recursive: true });
      writeFileSync(STATE_PATH, JSON.stringify(state), { mode: 0o600 });
    } catch {
      /* nothing useful left to do at this point */
    }
    process.exit(0);
  });
}
