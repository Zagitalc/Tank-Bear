import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { LIMITS, guard } from "../src/api/guard.ts";
import { createRateLimiter } from "../src/repositories/ratelimit.ts";
import { sqliteD1 } from "./d1-shim.ts";

const KEY = "test-key-0123456789-abcdefghij";
const KEY2 = "second-key-0123456789-abcdefg";
const NOW = Date.parse("2026-10-05T12:00:30Z");
const req = (key?: string, ip = "203.0.113.7") => new Request("https://t.test/v1/journeys/optimise", {
  method: "POST", headers: { ...(key ? { "x-tank-bear-key": key } : {}), "cf-connecting-ip": ip },
});
const setup = () => { const t = sqliteD1(); return { ...t, limiter: createRateLimiter(t.db) }; };

test("no configured keys fails closed; short keys are ignored", async () => {
  const { limiter } = setup();
  for (const API_KEYS of [undefined, "", "short,keys"]) {
    const r = await guard(req(KEY), { API_KEYS } as any, limiter, "optimise", NOW);
    assert.equal(r?.status, 503);
    assert.equal(((await r!.json()) as any).error.code, "AUTH_NOT_CONFIGURED");
  }
});

test("missing or wrong key is 401 and consumes no rate budget", async () => {
  const { limiter, sql } = setup();
  for (const k of [undefined, "", "wrong-key-0123456789-abcdefghi", KEY.slice(0, -1)]) {
    assert.equal((await guard(req(k), { API_KEYS: KEY }, limiter, "optimise", NOW))?.status, 401);
  }
  assert.equal(Number((sql.prepare("SELECT COUNT(*) AS n FROM rate_limits").get() as { n: number }).n), 0);
});

test("any configured key works, so keys can be rotated", async () => {
  const { limiter } = setup();
  assert.equal(await guard(req(KEY2), { API_KEYS: `${KEY}, ${KEY2}` }, limiter, "optimise", NOW), null);
});

test("per-caller limit returns 429 with Retry-After, other callers and the next minute are unaffected", async () => {
  const { limiter } = setup();
  const env = { API_KEYS: KEY };
  for (let i = 0; i < LIMITS.optimisePerCallerPerMinute; i++) assert.equal(await guard(req(KEY), env, limiter, "optimise", NOW), null);
  const blocked = await guard(req(KEY), env, limiter, "optimise", NOW);
  assert.equal(blocked?.status, 429);
  assert.equal(blocked?.headers.get("retry-after"), "30");
  assert.equal(((await blocked!.json()) as any).error.code, "RATE_LIMITED");
  assert.equal(await guard(req(KEY, "198.51.100.9"), env, limiter, "optimise", NOW), null);
  assert.equal(await guard(req(KEY), env, limiter, "optimise", NOW + 60_000), null);
  assert.equal(await guard(req(KEY), env, limiter, "nearby", NOW), null); // Separate budget per route.
});

test("a whole-service hourly ceiling protects routing even across many callers", async () => {
  const { limiter, sql } = setup();
  const hour = Math.floor(NOW / 3_600_000) * 3600;
  sql.prepare("INSERT INTO rate_limits VALUES ('optimise:all', ?, ?)").run(hour, LIMITS.optimiseGlobalPerHour);
  const r = await guard(req(KEY), { API_KEYS: KEY }, limiter, "optimise", NOW);
  assert.equal(r?.status, 429);
  assert.equal(((await r!.json()) as any).error.code, "SERVICE_BUSY");
});

test("a limiter fault fails closed without leaking the error", async () => {
  const broken = { prepare() { throw new Error("secret"); } } as unknown as D1Database;
  const r = await guard(req(KEY), { API_KEYS: KEY }, createRateLimiter(broken), "optimise", NOW);
  assert.equal(r?.status, 503);
  assert.doesNotMatch(await r!.text(), /secret/);
});

test("stored buckets contain no IP address, key or coordinates", async () => {
  const { limiter, sql } = setup();
  await guard(req(KEY, "203.0.113.7"), { API_KEYS: KEY }, limiter, "optimise", NOW);
  const rows = JSON.stringify(sql.prepare("SELECT * FROM rate_limits").all());
  assert.doesNotMatch(rows, /203\.0\.113|test-key/);
});

test("worker applies the gate to /v1 routes, leaves /health open, and adds nosniff", async () => {
  const { db } = setup();
  const noKey = await worker.fetch(new Request("https://t.test/v1/stations/nearby?lat=51.45&lon=-0.97"), { DB: db, API_KEYS: KEY });
  assert.equal(noKey.status, 401);
  assert.equal(noKey.headers.get("x-content-type-options"), "nosniff");
  const post = await worker.fetch(new Request("https://t.test/v1/journeys/optimise", { method: "POST", body: "{}" }), { DB: db, API_KEYS: KEY });
  assert.equal(post.status, 401);
  const health = await worker.fetch(new Request("https://t.test/health"), { DB: db });
  assert.equal(health.status, 200);
});

test("scheduled job purges old counters only", async () => {
  const { db, sql } = setup();
  const now = Math.floor(Date.now() / 1000);
  sql.prepare("INSERT INTO rate_limits VALUES ('old', ?, 1), ('new', ?, 1)").run(now - 10_000, now);
  await (worker as any).scheduled({}, { DB: db });
  assert.deepEqual((sql.prepare("SELECT bucket FROM rate_limits").all() as { bucket: string }[]).map((r) => r.bucket), ["new"]);
});
