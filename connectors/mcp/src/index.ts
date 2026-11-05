#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { MemGalleryClient, loadConfig, saveConfig, qs, MemGalleryError } from "./client.js";

const VERSION = "0.1.0";

// ---------------------------------------------------------------------------
// pair — the one-time setup command
// ---------------------------------------------------------------------------

function pair(argv: string[]): void {
  const arg = (name: string): string | undefined => {
    const i = argv.indexOf(`--${name}`);
    return i >= 0 ? argv[i + 1] : undefined;
  };

  const url = arg("url");
  const token = arg("token");
  const hubUrl = arg("hub-url");
  const hubToken = arg("hub-token");

  if (!url && !hubUrl) {
    console.error(
      "Usage: memgallery-mcp pair --url http://<device>:8790 --token <token> [--hub-url <url>] [--hub-token <token>]\n\n" +
        "Create the token in MemGallery under Settings → Agent Access. It is shown once.",
    );
    process.exit(1);
  }

  const existing = loadConfig();
  const path = saveConfig({
    url: url ?? existing.url,
    token: token ?? existing.token,
    hubUrl: hubUrl ?? existing.hubUrl,
    hubToken: hubToken ?? existing.hubToken,
  });
  console.error(`Saved to ${path}`);
  console.error("Add this to your agent's MCP config:\n");
  console.error(
    JSON.stringify({ mcpServers: { memgallery: { command: "npx", args: ["-y", "memgallery-mcp"] } } }, null, 2),
  );
}

// ---------------------------------------------------------------------------
// Result shaping
// ---------------------------------------------------------------------------

/**
 * Every tool result carries its provenance.
 *
 * A mirror read can be minutes old. Rather than hide that behind a clean-looking answer,
 * the staleness is stated in the text the model actually reads — because "complete the
 * task he already deleted" is the failure mode this whole design is trying to avoid.
 */
function shape(payload: unknown, source: string, asOf: number) {
  const age = Date.now() - asOf;
  const provenance =
    source === "device"
      ? "live from the device"
      : source === "queued"
        ? "QUEUED at the hub — the phone is unreachable and will apply this when it next connects"
        : `from the hub MIRROR, ${Math.round(age / 1000)}s old — the phone is unreachable, so this may be stale`;

  return {
    content: [
      {
        type: "text" as const,
        text: `[${provenance}]\n\n${typeof payload === "string" ? payload : JSON.stringify(payload, null, 2)}`,
      },
    ],
  };
}

function fail(error: unknown) {
  const message =
    error instanceof MemGalleryError
      ? `${error.code}: ${error.message}`
      : error instanceof Error
        ? error.message
        : String(error);
  return { content: [{ type: "text" as const, text: `Error — ${message}` }], isError: true };
}

// ---------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------

async function main(): Promise<void> {
  const client = new MemGalleryClient(loadConfig());
  const server = new McpServer({ name: "memgallery", version: VERSION });

  const tool = (
    name: string,
    description: string,
    schema: z.ZodRawShape,
    run: (args: any) => Promise<{ result: unknown; source: string; asOf: number }>,
  ) => {
    server.tool(name, description, schema, async (args: any) => {
      try {
        const { result, source, asOf } = await run(args);
        return shape(result, source, asOf);
      } catch (error) {
        return fail(error);
      }
    });
  };

  // --- orientation ---

  tool(
    "memgallery_digest",
    "What the operator is up to right now: overdue tasks, what's due today and this week, today's events, " +
      "what's awaiting his approval, what he captured recently, and his current activity. " +
      "Call this FIRST before deciding whether to interrupt him — it is one round trip and replaces five queries.",
    {},
    () => client.get("/digest"),
  );

  tool(
    "memgallery_search",
    "Free-text search across memories, tasks, projects and chats.",
    {
      query: z.string().describe("What to look for, in plain language"),
      scope: z
        .string()
        .optional()
        .describe("Comma-separated: memories, tasks, projects, chats. Defaults to memories,tasks"),
      limit_per_entity: z.number().int().min(1).max(50).optional(),
    },
    (a) => client.get(`/search${qs({ q: a.query, scope: a.scope, limit_per_entity: a.limit_per_entity })}`),
  );

  // --- tasks ---

  tool(
    "memgallery_list_tasks",
    "List the operator's tasks. Defaults to open, approved tasks — unapproved ones (things AI or an agent " +
      "proposed but he hasn't accepted) are excluded unless you ask for them.",
    {
      status: z.enum(["open", "completed", "any"]).optional(),
      approved: z.boolean().optional().describe("Pass false to inspect his approval inbox"),
      type: z.enum(["todo", "event"]).optional(),
      priority: z.enum(["low", "medium", "high"]).optional(),
      project_id: z.number().int().optional(),
      due_before: z.string().optional().describe("YYYY-MM-DD"),
      due_after: z.string().optional().describe("YYYY-MM-DD"),
      query: z.string().optional().describe("Match against title and description"),
      order: z.enum(["due_asc", "due_desc", "created_asc", "created_desc", "priority_desc"]).optional(),
      limit: z.number().int().min(1).max(200).optional(),
      cursor: z.string().optional(),
    },
    (a) =>
      client.get(
        `/tasks${qs({
          status: a.status,
          approved: a.approved,
          type: a.type,
          priority: a.priority,
          project_id: a.project_id,
          due_before: a.due_before,
          due_after: a.due_after,
          q: a.query,
          order: a.order,
          limit: a.limit,
          cursor: a.cursor,
        })}`,
      ),
  );

  tool(
    "memgallery_create_task",
    "Create a task. Set requires_approval=true to PROPOSE rather than assert — it lands in his approval inbox " +
      "on the phone and he accepts or bins it. Prefer that when you're adding work to someone else's day off " +
      "your own initiative. Pass external_id to make retries idempotent.",
    {
      title: z.string(),
      description: z.string().optional(),
      due_date: z.string().optional().describe("YYYY-MM-DD"),
      due_time: z.string().optional().describe("HH:MM, 24-hour"),
      priority: z.enum(["low", "medium", "high"]).optional(),
      type: z.enum(["todo", "event"]).optional(),
      project_id: z.number().int().optional(),
      requires_approval: z.boolean().optional(),
      external_id: z.string().optional(),
      recurrence_rule: z.string().optional().describe("DAILY, WEEKLY, MONTHLY"),
    },
    (a) =>
      client.post(
        "/tasks",
        {
          title: a.title,
          description: a.description ?? "",
          due_date: a.due_date,
          due_time: a.due_time,
          priority: a.priority,
          type: a.type,
          project_id: a.project_id,
          requires_approval: a.requires_approval ?? false,
          external_id: a.external_id,
          recurring: Boolean(a.recurrence_rule),
          recurrence_rule: a.recurrence_rule,
        },
        "create_task",
      ),
  );

  tool(
    "memgallery_update_task",
    "Update a task. Omitted fields are left alone; use clear_due_date / clear_project to actually remove a value.",
    {
      id: z.number().int(),
      title: z.string().optional(),
      description: z.string().optional(),
      due_date: z.string().optional(),
      clear_due_date: z.boolean().optional(),
      due_time: z.string().optional(),
      clear_due_time: z.boolean().optional(),
      priority: z.enum(["low", "medium", "high"]).optional(),
      completed: z.boolean().optional(),
      project_id: z.number().int().optional(),
      clear_project: z.boolean().optional(),
    },
    (a) => client.patch(`/tasks/${a.id}`, { ...a, id: undefined }, "update_task"),
  );

  tool(
    "memgallery_complete_task",
    "Mark a task done.",
    { id: z.number().int() },
    (a) => client.post(`/tasks/${a.id}/complete`, undefined, "complete_task"),
  );

  tool(
    "memgallery_delete_task",
    "Delete a task permanently.",
    { id: z.number().int() },
    (a) => client.del(`/tasks/${a.id}`, "delete_task"),
  );

  tool(
    "memgallery_pending_approval",
    "The operator's approval inbox: tasks proposed by AI or by agents that he hasn't accepted yet.",
    {},
    () => client.get("/tasks/pending-approval"),
  );

  tool(
    "memgallery_approve_task",
    "Accept a proposed task into his live list.",
    { id: z.number().int() },
    (a) => client.post(`/tasks/${a.id}/approve`, undefined, "approve_task"),
  );

  // --- memories ---

  tool(
    "memgallery_list_memories",
    "List captured memories — screenshots, notes, voice recordings, bookmarks — with their AI summaries, " +
      "tags and transcriptions. Metadata only; raw photo and audio bytes need a separate scope.",
    {
      query: z.string().optional(),
      kind: z.enum(["text", "image", "audio", "bookmark", "chat", "mixed"]).optional(),
      status: z.enum(["pending", "processing", "completed", "failed"]).optional(),
      project_id: z.number().int().optional(),
      tag: z.string().optional(),
      from: z.number().int().optional().describe("Epoch millis, inclusive"),
      to: z.number().int().optional(),
      limit: z.number().int().min(1).max(200).optional(),
      cursor: z.string().optional(),
    },
    (a) =>
      client.get(
        `/memories${qs({
          q: a.query,
          kind: a.kind,
          status: a.status,
          project_id: a.project_id,
          tag: a.tag,
          from: a.from,
          to: a.to,
          limit: a.limit,
          cursor: a.cursor,
        })}`,
      ),
  );

  tool(
    "memgallery_get_memory",
    "One memory in full, including AI analysis, transcription and detected actions.",
    { id: z.number().int() },
    (a) => client.get(`/memories/${a.id}`),
  );

  tool(
    "memgallery_create_memory",
    "Capture something into MemGallery on the operator's behalf — a note or a URL. " +
      "process=true runs AI analysis immediately and spends his API quota (subject to a daily cap); " +
      "process=false stores it raw for the normal pipeline to pick up later.",
    {
      text: z.string().optional(),
      url: z.string().optional(),
      process: z.boolean().optional(),
      project_ids: z.array(z.number().int()).optional(),
    },
    (a) =>
      client.post(
        "/memories",
        { text: a.text, url: a.url, process: a.process ?? true, project_ids: a.project_ids ?? [] },
        "create_memory",
      ),
  );

  // --- projects ---

  tool(
    "memgallery_list_projects",
    "List projects (MemGallery's collections, which also group tasks). with_counts adds task and memory totals.",
    {
      status: z.enum(["active", "paused", "done"]).optional(),
      include_archived: z.boolean().optional(),
      with_counts: z.boolean().optional(),
      query: z.string().optional(),
    },
    (a) =>
      client.get(
        `/projects${qs({
          status: a.status,
          include_archived: a.include_archived,
          with_counts: a.with_counts,
          q: a.query,
        })}`,
      ),
  );

  tool(
    "memgallery_create_project",
    "Create a project.",
    {
      name: z.string(),
      description: z.string().optional(),
      status: z.enum(["active", "paused", "done"]).optional(),
      target_date: z.string().optional().describe("YYYY-MM-DD"),
    },
    (a) =>
      client.post(
        "/projects",
        { name: a.name, description: a.description ?? "", status: a.status, target_date: a.target_date },
        "create_project",
      ),
  );

  // --- attention ---

  tool(
    "memgallery_notify",
    "Push a notification to the operator's phone. Fire-and-forget — use it to tell him something, not to ask. " +
      "Rate-limited to 30/hour per client, and he can mute agents independently of his own reminders. " +
      "Don't use it for anything he'd rather read later.",
    {
      title: z.string(),
      body: z.string(),
      priority: z.enum(["low", "normal", "high", "urgent"]).optional(),
      deep_link: z.string().optional().describe("e.g. memgallery://detail/412"),
      actions: z
        .array(z.object({ id: z.string(), label: z.string() }))
        .max(3)
        .optional(),
      tag: z.string().optional().describe("Reuse to replace an earlier notification rather than stacking"),
    },
    (a) => client.post("/notify", a, "notify"),
  );

  tool(
    "memgallery_ask",
    "Ask the operator a question and WAIT for his answer. He answers from the notification shade without " +
      "unlocking: 'confirm' gives Yes/No, 'choice' gives buttons, 'text' lets him type. " +
      "Blocks up to wait_sec. If it returns status 'pending' or 'expired' he simply hasn't seen it — that is " +
      "NOT a 'no'. Use this when you genuinely cannot proceed without a decision.",
    {
      kind: z.enum(["confirm", "choice", "text"]),
      question: z.string(),
      context: z.string().optional().describe("Background he needs to answer sensibly"),
      options: z.array(z.string()).optional().describe("Required for 'choice'"),
      timeout_sec: z.number().int().min(60).max(86400).optional(),
      wait_sec: z.number().int().min(0).max(60).optional().describe("How long to block waiting. Default 45."),
    },
    async (a) => {
      const asked = await client.post(
        "/ask",
        {
          kind: a.kind,
          question: a.question,
          context: a.context,
          options: a.options ?? [],
          timeout_sec: a.timeout_sec ?? 3600,
        },
        "ask",
      );
      const id = (asked.result as any)?.id;
      if (!id || asked.source === "queued") return asked;

      // Poll in ≤30s slices: one long hold is more likely to be cut by an intermediary
      // than several short ones, and each slice is a chance to notice the answer landed.
      const budget = a.wait_sec ?? 45;
      const deadline = Date.now() + budget * 1000;
      let latest = asked;
      while (Date.now() < deadline) {
        const slice = Math.min(30, Math.ceil((deadline - Date.now()) / 1000));
        if (slice <= 0) break;
        latest = await client.get(`/ask/${id}?wait=${slice}`);
        if ((latest.result as any)?.status !== "pending") return latest;
      }
      return latest;
    },
  );

  tool(
    "memgallery_pending_asks",
    "Questions already put to the operator that he hasn't answered yet.",
    {},
    () => client.get("/asks"),
  );

  // --- resources: context without a tool call ---

  server.resource("digest", "memgallery://digest", async (uri) => {
    const { result } = await client.get("/digest");
    return { contents: [{ uri: uri.href, mimeType: "application/json", text: JSON.stringify(result, null, 2) }] };
  });

  server.resource("today", "memgallery://tasks/today", async (uri) => {
    const { result } = await client.get("/digest");
    const digest = result as any;
    return {
      contents: [
        {
          uri: uri.href,
          mimeType: "application/json",
          text: JSON.stringify({ overdue: digest?.overdue, due_today: digest?.due_today }, null, 2),
        },
      ],
    };
  });

  server.resource("recent-memories", "memgallery://memories/recent", async (uri) => {
    const { result } = await client.get("/memories?limit=20");
    return { contents: [{ uri: uri.href, mimeType: "application/json", text: JSON.stringify(result, null, 2) }] };
  });

  // --- prompts ---

  server.prompt("whats-on-my-plate", "Summarise what the operator is up to right now", {}, () => ({
    messages: [
      {
        role: "user" as const,
        content: {
          type: "text" as const,
          text:
            "Call memgallery_digest and give me a short, human summary of where I stand: what's overdue, what's " +
            "due today, anything waiting on my approval. Lead with what actually needs me. Don't pad it.",
        },
      },
    ],
  }));

  server.prompt("triage-unapproved", "Work through the approval inbox", {}, () => ({
    messages: [
      {
        role: "user" as const,
        content: {
          type: "text" as const,
          text:
            "Call memgallery_pending_approval. For each proposed task tell me what it is, where it came from, " +
            "and whether it's worth keeping — then approve the good ones with memgallery_approve_task and tell me " +
            "which you'd bin. Ask me before deleting anything.",
        },
      },
    ],
  }));

  await server.connect(new StdioServerTransport());
  console.error(`memgallery-mcp ${VERSION} ready`);
}

const [, , command, ...rest] = process.argv;

if (command === "pair") {
  pair(rest);
} else if (command === "--version" || command === "-v") {
  console.log(VERSION);
} else {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
  });
}
