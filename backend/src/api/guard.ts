import type { Env } from "../env.ts";
import type { RateLimiter } from "../repositories/ratelimit.ts";

export const API_KEY_HEADER = "x-tank-bear-key";
export const MIN_KEY_LENGTH = 24;

/** Per-caller and whole-service ceilings. Provisional; tune with real traffic. */
export const LIMITS = {
  optimisePerCallerPerMinute: 10,
  nearbyPerCallerPerMinute: 60,
  historyPerCallerPerMinute: 30,
  optimiseGlobalPerHour: 1500,
} as const;

const headers = { "Cache-Control": "no-store" };
const fail = (status: number, code: string, message: string, extra: Record<string, string> = {}) =>
  Response.json({ error: { code, message } }, { status, headers: { ...headers, ...extra } });

async function digest(text: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}

/** Compares fixed-length digests without early exit, so timing does not reveal matching prefixes. */
async function sameSecret(supplied: string, configured: string): Promise<boolean> {
  const [a, b] = await Promise.all([digest(supplied), digest(configured)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i]! ^ b[i]!;
  return diff === 0;
}

async function callerBucket(request: Request, key: string): Promise<string> {
  // Prefer Cloudflare's connecting IP; fall back to the key so local runs still limit.
  const ip = request.headers.get("cf-connecting-ip") ?? "";
  const hash = await digest(`${ip}|${key}`);
  return [...hash.slice(0, 8)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export type Route = "optimise" | "nearby" | "history";

/**
 * Gate for every /v1 route. Fails closed: with no configured keys, or a limiter fault, nothing is served.
 * The key identifies the app build, not a person (Tank Bear has no accounts). A key shipped inside an
 * Android app can be extracted, so this deters casual abuse and enables rotation; it is not user auth.
 */
export async function guard(request: Request, env: Pick<Env, "API_KEYS">, limiter: RateLimiter, route: Route, now = Date.now()): Promise<Response | null> {
  const configured = (env.API_KEYS ?? "").split(",").map((k) => k.trim()).filter((k) => k.length >= MIN_KEY_LENGTH);
  if (configured.length === 0) return fail(503, "AUTH_NOT_CONFIGURED", "The service is not configured to accept requests.");
  const supplied = request.headers.get(API_KEY_HEADER) ?? "";
  let matched = false;
  for (const k of configured) if (await sameSecret(supplied, k)) matched = true; // No early exit.
  if (!matched) return fail(401, "UNAUTHORISED", "A valid API key is required.");

  const minute = Math.floor(now / 60_000) * 60;
  const hour = Math.floor(now / 3_600_000) * 3600;
  try {
    const caller = await callerBucket(request, supplied);
    const perCaller = route === "optimise" ? LIMITS.optimisePerCallerPerMinute : route === "history" ? LIMITS.historyPerCallerPerMinute : LIMITS.nearbyPerCallerPerMinute;
    if ((await limiter.hit(`${route}:${caller}`, minute)) > perCaller) {
      return fail(429, "RATE_LIMITED", "Too many requests. Try again shortly.", { "Retry-After": String(60 - (Math.floor(now / 1000) - minute)) });
    }
    if (route === "optimise" && (await limiter.hit("optimise:all", hour)) > LIMITS.optimiseGlobalPerHour) {
      return fail(429, "SERVICE_BUSY", "The service is busy. Try again later.", { "Retry-After": "300" });
    }
  } catch {
    return fail(503, "UNAVAILABLE", "The service is temporarily unavailable.");
  }
  return null;
}
