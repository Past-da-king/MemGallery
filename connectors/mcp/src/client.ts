import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { homedir } from "node:os";
import { join, dirname } from "node:path";

export interface MemGalleryConfig {
  /** The phone, over Tailscale or LAN. Tried first. */
  url?: string;
  token?: string;
  /** The always-on relay. Answers reads from its mirror and queues writes. */
  hubUrl?: string;
  hubToken?: string;
  /** Milliseconds before giving up on the device and falling back to the hub. */
  deviceTimeoutMs?: number;
}

export interface Envelope<T> {
  data: T;
  source: "device" | "mirror";
  as_of: number;
  api_version: string;
  next_cursor?: string | null;
  total?: number;
}

const CONFIG_PATH = join(homedir(), ".memgallery", "config.json");

export function loadConfig(): MemGalleryConfig {
  const fromFile: MemGalleryConfig = existsSync(CONFIG_PATH)
    ? JSON.parse(readFileSync(CONFIG_PATH, "utf8"))
    : {};

  // Environment wins over the file, so a single machine can point different agents at
  // different devices without rewriting shared config.
  return {
    url: process.env.MEMGALLERY_URL ?? fromFile.url,
    token: process.env.MEMGALLERY_TOKEN ?? fromFile.token,
    hubUrl: process.env.MEMGALLERY_HUB_URL ?? fromFile.hubUrl,
    hubToken: process.env.MEMGALLERY_HUB_TOKEN ?? fromFile.hubToken ?? fromFile.token,
    deviceTimeoutMs: Number(process.env.MEMGALLERY_DEVICE_TIMEOUT_MS ?? fromFile.deviceTimeoutMs ?? 2500),
  };
}

export function saveConfig(config: MemGalleryConfig): string {
  mkdirSync(dirname(CONFIG_PATH), { recursive: true });
  writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), { mode: 0o600 });
  return CONFIG_PATH;
}

export class MemGalleryError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/**
 * Talks to MemGallery, device-first.
 *
 * The device is the source of truth, so it is always tried first. When it doesn't answer
 * within `deviceTimeoutMs` — phone asleep, off Tailscale, in a tunnel — reads fall back to
 * the hub's mirror and writes are queued there for the phone to drain when it next dials
 * out.
 *
 * Every result carries where it came from and how old it is. Tools surface that verbatim
 * rather than hiding it: an agent that completes a task based on a ten-minute-old mirror
 * read should at least have been in a position to know.
 */
export class MemGalleryClient {
  private deviceHealthy = true;
  private lastDeviceProbe = 0;

  constructor(private readonly config: MemGalleryConfig) {
    if (!config.url && !config.hubUrl) {
      throw new Error(
        "No MemGallery endpoint configured. Run `memgallery-mcp pair --url http://<device>:8790 --token <token>`, " +
          "or set MEMGALLERY_URL and MEMGALLERY_TOKEN.",
      );
    }
  }

  private async attempt(
    base: string,
    token: string | undefined,
    path: string,
    init: RequestInit,
    timeoutMs: number,
    /**
     * Mirror reads live under `/v1` so the hub can mimic the device's URL shape. The
     * hub's own control endpoints (`/queue`) do not — they are the hub's API, not
     * MemGallery's, and pretending otherwise is how you end up POSTing to /v1/queue.
     */
    prefix = "/v1",
  ): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      return await fetch(`${base.replace(/\/$/, "")}${prefix}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          ...(init.headers ?? {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          "Content-Type": "application/json",
        },
      });
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Writes must never be silently answered by the mirror — a mirror "success" that the
   * phone later rejects is worse than an honest failure. Queueing at the hub is explicit
   * and reported as such.
   */
  async request<T>(
    method: string,
    path: string,
    body?: unknown,
    /** `queueable` is the hub-side operation name, present only for writes safe to defer. */
    opts: { queueable?: string } = {},
  ): Promise<{ result: T; source: "device" | "mirror" | "queued"; asOf: number }> {
    const init: RequestInit = { method, body: body === undefined ? undefined : JSON.stringify(body) };
    const timeout = this.config.deviceTimeoutMs ?? 2500;

    if (this.config.url && this.shouldTryDevice()) {
      try {
        const response = await this.attempt(this.config.url, this.config.token, path, init, timeout);
        this.deviceHealthy = true;
        return await this.unwrap<T>(response, "device");
      } catch (error) {
        if (error instanceof MemGalleryError) throw error; // a real 4xx is an answer, not an outage
        this.deviceHealthy = false;
        this.lastDeviceProbe = Date.now();
      }
    }

    if (!this.config.hubUrl) {
      throw new Error(
        "The device is unreachable and no hub is configured. Set MEMGALLERY_HUB_URL to keep working while the phone is away.",
      );
    }

    const isRead = method === "GET";
    if (!isRead && !opts.queueable) {
      throw new Error("The device is unreachable and this operation cannot be safely queued.");
    }

    const hubBody = isRead ? undefined : { op: opts.queueable, args: body };
    const response = await this.attempt(
      this.config.hubUrl,
      this.config.hubToken,
      isRead ? path : "/queue",
      { method: isRead ? "GET" : "POST", body: hubBody === undefined ? undefined : JSON.stringify(hubBody) },
      15000,
      isRead ? "/v1" : "",
    );
    const unwrapped = await this.unwrap<T>(response, isRead ? "mirror" : "device");
    return { ...unwrapped, source: isRead ? "mirror" : "queued" };
  }

  /** Re-probe a device believed down every 30s rather than writing it off for the session. */
  private shouldTryDevice(): boolean {
    if (this.deviceHealthy) return true;
    return Date.now() - this.lastDeviceProbe > 30_000;
  }

  private async unwrap<T>(
    response: Response,
    fallbackSource: "device" | "mirror",
  ): Promise<{ result: T; source: "device" | "mirror"; asOf: number }> {
    const text = await response.text();
    let parsed: any = undefined;
    try {
      parsed = text ? JSON.parse(text) : undefined;
    } catch {
      parsed = undefined;
    }

    if (!response.ok) {
      throw new MemGalleryError(
        parsed?.message ?? `HTTP ${response.status}`,
        parsed?.code ?? "http_error",
        response.status,
      );
    }

    if (parsed && typeof parsed === "object" && "data" in parsed && "source" in parsed) {
      const envelope = parsed as Envelope<T>;
      return { result: envelope.data, source: envelope.source, asOf: envelope.as_of };
    }
    return { result: parsed as T, source: fallbackSource, asOf: Date.now() };
  }

  get(path: string) {
    return this.request<any>("GET", path);
  }

  post(path: string, body?: unknown, queueableOp?: string) {
    return this.request<any>("POST", path, body, { queueable: queueableOp });
  }

  patch(path: string, body?: unknown, queueableOp?: string) {
    return this.request<any>("PATCH", path, body, { queueable: queueableOp });
  }

  del(path: string, queueableOp?: string) {
    return this.request<any>("DELETE", path, undefined, { queueable: queueableOp });
  }
}

/** Turns a query object into a query string, dropping anything unset. */
export function qs(params: Record<string, unknown>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "");
  if (entries.length === 0) return "";
  return "?" + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join("&");
}
