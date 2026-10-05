import assert from "node:assert/strict";
import test from "node:test";
import { createFuelFinderClient } from "../src/ingestion/client.ts";
import { BATCH_SIZE, MAX_BATCHES, collectBatches, type BatchOutcome } from "../src/ingestion/pagination.ts";
import { normalisePrices, normaliseStations } from "../src/ingestion/records.ts";
import { assessRefresh } from "../src/ingestion/refresh.ts";

const NOW = Date.parse("2026-10-05T12:00:00Z");
const id = (n: number) => n.toString(16).padStart(64, "0");
const station = (n: number, over: Record<string, unknown> = {}) => ({
  node_id: id(n), trading_name: `Forecourt ${n}`, brand_name: "Brand", temporary_closure: false,
  permanent_closure: false, is_motorway_service_station: false, is_supermarket_service_station: false,
  location: { postcode: "RG1 1AA", latitude: 51.45, longitude: -0.97 }, ...over,
});
const priced = (n: number, prices: unknown[] = [price("E10", 132.9)]) => ({ node_id: id(n), trading_name: `Forecourt ${n}`, fuel_prices: prices });
const price = (fuel_type: string, p: unknown, over: Record<string, unknown> = {}) => ({
  fuel_type, price: p, price_last_updated: "2026-10-05T11:00:00.000Z",
  price_change_effective_timestamp: "2026-10-05T10:00:00.000Z", ...over,
});
const range = (from: number, count: number) => Array.from({ length: count }, (_, i) => from + i);

/** Serves a feed of `total` items in 500-sized batches, as observed live. */
function feed(total: number, make: (n: number) => unknown, endAs: BatchOutcome = { kind: "not-available" }) {
  return async (batch: number): Promise<BatchOutcome> => {
    const start = (batch - 1) * BATCH_SIZE;
    if (start >= total) return endAs;
    return { kind: "records", records: range(start, Math.min(BATCH_SIZE, total - start)).map(make) };
  };
}

test("a short final batch completes the feed without needing the 404", async () => {
  let calls = 0;
  const inner = feed(1126, (i) => station(i));
  const result = await collectBatches(async (b) => { calls++; return inner(b); });
  assert.deepEqual([result.complete, result.complete && result.end, result.batches, result.records.length], [true, "short-batch", 3, 1126]);
  assert.equal(calls, 3);
});

test("an exact multiple of 500 ends on not-available or empty, flagged as unconfirmed", async () => {
  for (const end of [{ kind: "not-available" }, { kind: "records", records: [] }] as BatchOutcome[]) {
    const r = await collectBatches(feed(1000, (i) => station(i), end));
    assert.equal(r.complete && r.end, "end-after-full-batch");
    assert.equal(r.records.length, 1000);
  }
});

test("a failed batch is never completion and keeps partial records out of the decision", async () => {
  const inner = feed(2000, (i) => station(i));
  const r = await collectBatches(async (b) => (b === 3 ? { kind: "error", reason: "HTTP 503" } : inner(b)));
  assert.equal(r.complete, false);
  assert.match(!r.complete ? r.reason : "", /batch 3 failed/);
});

test("empty or missing first batch, oversize batch and runaway feeds are incomplete", async () => {
  assert.equal((await collectBatches(async () => ({ kind: "records", records: [] }))).complete, false);
  assert.equal((await collectBatches(async () => ({ kind: "not-available" }))).complete, false);
  assert.equal((await collectBatches(async () => ({ kind: "records", records: range(0, 501) }))).complete, false);
  let calls = 0;
  const runaway = await collectBatches(async () => { calls++; return { kind: "records", records: range(0, 500) }; });
  assert.equal(runaway.complete, false);
  assert.equal(calls, MAX_BATCHES);
});

test("stations: malformed records are quarantined, duplicates reported, valid kept", () => {
  const out = normaliseStations([
    station(1), station(1), station(2, { location: { latitude: 0, longitude: 0 } }),
    station(3, { node_id: "nope" }), station(4, { trading_name: " " }), null, station(5, { permanent_closure: null }),
  ]);
  assert.deepEqual(out.valid.map((s) => s.nodeId), [id(1), id(5)]);
  assert.deepEqual(out.duplicateNodeIds, [id(1)]);
  assert.equal(out.quarantined.length, 4);
  assert.equal(out.valid[1]?.permanentClosure, false);
});

test("prices: units, grades and timestamps are validated; bad entries do not drop good ones", () => {
  const out = normalisePrices([
    priced(1, [
      price("E10", 132.9), price("B7_STANDARD", 141), price("B7_PREMIUM", 150.5), price("E5", 12.3),
      price("E10", 133.9), price("ZZZ", 140), price("HVO", "150"), price("B10", 140, { price_last_updated: "2026-10-05T12:30:00.000Z" }),
      price("E5", 145.1234), price("HVO", 160.25, { price_change_effective_timestamp: "05/10/2026" }),
    ]),
    priced(2, []),
  ], NOW);
  const first = out.valid[0]!;
  assert.deepEqual(first.prices.map((p) => [p.feedFuelType, p.fuel, p.pencePerLitre]),
    [["E10", "E10", "132.9"], ["B7_STANDARD", "B7", "141"], ["B7_PREMIUM", null, "150.5"]]);
  assert.equal(out.quarantined.length, 7);
  assert.equal(out.valid[1]?.prices.length, 0); // Station with no prices is valid, not an error.
});

const ok = (n: number, end: "short-batch" | "end-after-full-batch" = "short-batch") =>
  ({ complete: true, end, batches: Math.ceil(n / BATCH_SIZE), records: range(0, n) }) as const;

function assess(opts: { n: number; priced?: number; previous?: { stationCount: number; priceStationCount: number } | null; end?: "short-batch" | "end-after-full-batch"; stationsRaw?: unknown[] }) {
  const n = opts.n;
  const stationsRaw = opts.stationsRaw ?? range(0, n).map((i) => station(i));
  const pricesRaw = range(0, opts.priced ?? n).map((i) => priced(i));
  return assessRefresh({
    stations: ok(n, opts.end), prices: ok(opts.priced ?? n, opts.end),
    normalisedStations: normaliseStations(stationsRaw), normalisedPrices: normalisePrices(pricesRaw, NOW),
    previous: opts.previous === undefined ? { stationCount: 8126, priceStationCount: 8122 } : opts.previous,
  });
}

test("a normal national refresh is complete and only reports absent stations as candidates", () => {
  const d = assess({ n: 8100, priced: 8100 });
  assert.equal(d.complete, true);
  if (d.complete) {
    assert.deepEqual(d.absentStationIds(new Set([id(1), "gone"])), ["gone"]);
    assert.equal(d.priceOrphanCount, 0);
  }
});

test("a clean-looking but dramatically small refresh is rejected", () => {
  const d = assess({ n: 1500, priced: 1500 });
  assert.equal(d.complete, false);
  assert.match(d.complete ? "" : d.reasons.join(), /implausibly low/);
});

test("first ever refresh needs a sane floor; no previous count is not a free pass", () => {
  assert.equal(assess({ n: 400, previous: null }).complete, false);
  assert.equal(assess({ n: 5000, previous: null }).complete, true);
});

test("the unobserved end signal is held to a stricter total", () => {
  assert.equal(assess({ n: 7500, priced: 7500, end: "short-batch" }).complete, true); // 92% of previous
  assert.equal(assess({ n: 7500, priced: 7500, end: "end-after-full-batch" }).complete, false);
});

test("any incomplete pagination or duplicate across batches keeps last good data", () => {
  const raws = [...range(0, 8100).map((i) => station(i)), station(5)];
  const dup = assess({ n: 8100, stationsRaw: raws });
  assert.equal(dup.complete, false);
  const incomplete = assessRefresh({
    stations: { complete: false, reason: "batch 4 failed: HTTP 503", batches: 3, records: range(0, 1500) },
    prices: ok(8100),
    normalisedStations: normaliseStations(range(0, 1500).map((i) => station(i))),
    normalisedPrices: normalisePrices(range(0, 8100).map((i) => priced(i)), NOW),
    previous: { stationCount: 8126, priceStationCount: 8122 },
  });
  assert.equal(incomplete.complete, false);
  assert.ok(!incomplete.complete && incomplete.reasons.some((r) => r.startsWith("stations:")));
  assert.ok(!("absentStationIds" in incomplete)); // Nothing to infer deletion from.
});

test("a feed mostly made of malformed records is not a complete refresh", () => {
  const raws = range(0, 8100).map((i) => (i % 5 === 0 ? { node_id: "bad" } : station(i)));
  assert.equal(assess({ n: 8100, stationsRaw: raws }).complete, false);
});

// --- client ---------------------------------------------------------------

function harness(responses: Array<() => Response | Promise<Response>>) {
  const log: Array<{ url: string; auth: string | null; at: number; ua: string | null }> = [];
  let clock = 0;
  let i = 0;
  const client = createFuelFinderClient({ clientId: "id", clientSecret: "secret" }, {
    now: () => clock,
    sleep: async (ms) => { clock += ms; },
    fetch: (async (url: string, init?: RequestInit) => {
      log.push({ url: String(url), auth: new Headers(init?.headers).get("authorization"), at: clock, ua: new Headers(init?.headers).get("user-agent") });
      const next = responses[i++];
      if (!next) throw new Error("unexpected request");
      return next();
    }) as typeof fetch,
  });
  return { client, log };
}
const tokenResponse = () => Response.json({ data: { access_token: "T", expires_in: 3600 } });

test("client reuses one token, paces requests and sends the batch number", async () => {
  const { client, log } = harness([tokenResponse, () => Response.json([1]), () => Response.json([2])]);
  assert.deepEqual(await client.fetchStationBatch(1), { kind: "records", records: [1] });
  await client.fetchStationBatch(2);
  assert.equal(log.filter((l) => l.url.includes("generate_access_token")).length, 1);
  assert.match(log[2]!.url, /\/api\/v1\/pfs\?batch-number=2$/);
  assert.equal(log[2]!.auth, "Bearer T");
  assert.ok(log.every((l) => /^TankBear\//.test(l.ua ?? "")), "every request names the client; Fuel Finder 403s without a User-Agent");
  assert.ok(log[1]!.at - log[0]!.at >= 750 && log[2]!.at - log[1]!.at >= 750);
});

test("client maps the observed 404 to not-available, other 404 and 429 to errors without retrying 429", async () => {
  const notAvail = () => new Response(JSON.stringify({ data: { data: { message: "Requested batch 18 is not available" } } }), { status: 404 });
  const a = harness([tokenResponse, notAvail]);
  assert.deepEqual(await a.client.fetchPriceBatch(18), { kind: "not-available" });
  const b = harness([tokenResponse, () => new Response("nope", { status: 404 })]);
  assert.deepEqual(await b.client.fetchPriceBatch(2), { kind: "error", reason: "HTTP 404" });
  const c = harness([tokenResponse, () => new Response("slow down", { status: 429 })]);
  assert.deepEqual(await c.client.fetchPriceBatch(2), { kind: "error", reason: "HTTP 429" });
  assert.equal(c.log.length, 2);
});

test("client retries 5xx then recovers, and a 5xx streak surfaces as error", async () => {
  const ok = harness([tokenResponse, () => new Response("", { status: 503 }), () => Response.json([])]);
  assert.deepEqual(await ok.client.fetchStationBatch(1), { kind: "records", records: [] });
  const bad = harness([tokenResponse, ...[1, 2, 3].map(() => () => new Response("", { status: 502 }))]);
  assert.equal((await bad.client.fetchStationBatch(1)).kind, "error");
});

test("client errors never contain the secret or token, and a non-array body is an error", async () => {
  const denied = harness([() => new Response("secret", { status: 401 })]);
  const r = await denied.client.fetchStationBatch(1);
  assert.equal(r.kind, "error");
  assert.doesNotMatch(JSON.stringify(r), /secret|"T"/);
  const obj = harness([tokenResponse, () => Response.json({ data: [] })]);
  assert.deepEqual(await obj.client.fetchStationBatch(1), { kind: "error", reason: "response was not an array" });
});
