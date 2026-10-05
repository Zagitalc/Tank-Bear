import assert from "node:assert/strict";
import test from "node:test";
import { rankFuelStops } from "../src/domain/economics.ts";
import type { Coordinates } from "../src/domain/types.ts";
import { withRouteCache } from "../src/routing/cache.ts";
import { CONCURRENCY, MAX_CANDIDATES, routeJourney } from "../src/routing/journey.ts";
import { decodePolyline, encodePolyline } from "../src/routing/polyline.ts";
import type { RouteOutcome, RoutingProvider } from "../src/routing/types.ts";
import { createValhallaProvider } from "../src/routing/valhalla.ts";

const A: Coordinates = { lat: 51.45, lon: -0.97 };
const B: Coordinates = { lat: 51.75, lon: -1.25 };
const station = (n: number): Coordinates => ({ lat: 51.5 + n * 0.01, lon: -1.0 });

// Hand-built to the documented route format; not captured from a live Valhalla instance.
function trip(stops: Coordinates[], over: { length?: number; time?: number; ferry?: boolean; toll?: boolean; snap?: number[]; units?: string } = {}) {
  const legs = stops.slice(0, -1).map((from, i) => {
    const to = stops[i + 1]!;
    const off = (over.snap?.[i] ?? 0) / 111_320; // metres to degrees latitude
    const offEnd = (over.snap?.[i + 1] ?? 0) / 111_320;
    return { shape: encodePolyline([[from.lat + off, from.lon], [(from.lat + to.lat) / 2, (from.lon + to.lon) / 2], [to.lat + offEnd, to.lon]]) };
  });
  return {
    trip: {
      status: 0, units: over.units ?? "kilometers", locations: stops.map((s) => ({ ...s, type: "break" })), legs,
      summary: { length: over.length ?? 40.123, time: over.time ?? 2400.4, has_toll: over.toll ?? false, has_ferry: over.ferry ?? false, has_highway: true },
    },
  };
}

function valhalla(handler: (body: any) => Response | Promise<Response>) {
  const calls: any[] = [];
  const provider = createValhallaProvider({
    baseUrl: "https://routing.test/", graphVersion: "2026-09",
    fetch: (async (_url: string, init: RequestInit) => { const body = JSON.parse(String(init.body)); calls.push(body); return handler(body); }) as typeof fetch,
  });
  return { provider, calls };
}

test("polyline6 round-trips and rejects corrupt input", () => {
  const pts: [number, number][] = [[51.450001, -0.970002], [51.6, -1.1], [51.75, -1.25]];
  assert.deepEqual(decodePolyline(encodePolyline(pts)), pts);
  assert.throws(() => decodePolyline("_"));
});

test("valhalla: every stop is a break, totals are whole metres and seconds, flags are explicit", async () => {
  const { provider, calls } = valhalla((b) => Response.json(trip(b.locations)));
  const out = await provider.route([A, station(1), B]);
  assert.equal(out.kind, "ok");
  assert.deepEqual(calls[0].locations.map((l: any) => l.type), ["break", "break", "break"]);
  assert.equal(calls[0].units, "kilometers");
  if (out.kind === "ok") {
    assert.deepEqual(out.path.totals, { distanceMetres: "40123", durationSeconds: "2400", contextId: "valhalla|2026-09|auto|km", usesFerry: false, usesToll: false });
    assert.equal(out.path.snapMetres.length, 3);
    assert.ok(out.path.snapMetres.every((m) => m < 1));
    assert.equal(decodePolyline(out.path.geometry).length, 5); // Joined legs share the middle stop once.
  }
});

test("valhalla: no path is no_route; other failures are errors, never a route", async () => {
  const none = valhalla(() => Response.json({ error_code: 442, error: "No path could be found for input" }, { status: 400 }));
  assert.deepEqual(await none.provider.route([A, B]), { kind: "no_route" });
  for (const error_code of [171, 170]) {
    const r = await valhalla(() => Response.json({ error_code, error: "x", status_code: 400 }, { status: 400 })).provider.route([A, B]);
    assert.deepEqual(r, { kind: "no_route" });
  }
  for (const status of [400, 429, 500, 503]) {
    const r = await valhalla(() => Response.json({ error_code: 100 }, { status })).provider.route([A, B]);
    assert.equal(r.kind, "error");
  }
  const thrown = createValhallaProvider({ baseUrl: "https://x.test", graphVersion: "v", fetch: (async () => { throw new Error("secret url"); }) as typeof fetch });
  const t = await thrown.route([A, B]);
  assert.deepEqual(t, { kind: "error", reason: "request failed or timed out" });
});

test("valhalla: malformed or unexpected responses are errors", async () => {
  const bodies = [
    {}, { trip: { summary: { length: 1, time: 1 }, legs: [] } },
    trip([A, B], { units: "miles" }), { ...trip([A, B]), trip: { ...trip([A, B]).trip, legs: [] } },
    { trip: { ...trip([A, B]).trip, summary: { length: -1, time: 5, has_toll: false, has_ferry: false } } },
    { trip: { ...trip([A, B]).trip, summary: { length: 1, time: 5, has_toll: false } } },
    { trip: { ...trip([A, B]).trip, legs: [{ shape: "~~~" }] } },
  ];
  for (const body of bodies) {
    const r = await valhalla(() => Response.json(body)).provider.route([A, B]);
    assert.equal(r.kind, "error", JSON.stringify(body).slice(0, 80));
  }
});

test("valhalla: ferry/toll flags and snapping distance are surfaced", async () => {
  const far = valhalla((b) => Response.json(trip(b.locations, { ferry: true, toll: true, snap: [0, 400, 0] })));
  const r = await far.provider.route([A, station(1), B]);
  assert.equal(r.kind, "ok");
  if (r.kind === "ok") {
    assert.equal(r.path.totals.usesFerry && r.path.totals.usesToll, true);
    assert.ok(r.path.snapMetres[1]! > 350 && r.path.snapMetres[1]! < 450);
  }
});

// --- journey -----------------------------------------------------------------

function fake(handler: (stops: readonly Coordinates[]) => RouteOutcome | Promise<RouteOutcome>) {
  const calls: Coordinates[][] = [];
  let inFlight = 0;
  let peak = 0;
  const provider: RoutingProvider = {
    contextId: "fake|v1",
    async route(stops) {
      calls.push([...stops]); inFlight++; peak = Math.max(peak, inFlight);
      await new Promise((r) => setTimeout(r, 2));
      try { return await handler(stops); } finally { inFlight--; }
    },
  };
  return { provider, calls, peak: () => peak };
}
const okPath = (distance: number, over: { snap?: number[]; ctx?: string; ferry?: boolean } = {}): RouteOutcome => ({
  kind: "ok",
  path: {
    totals: { distanceMetres: String(distance), durationSeconds: String(Math.round(distance / 15)), contextId: over.ctx ?? "fake|v1", usesFerry: over.ferry ?? false, usesToll: false },
    geometry: "x", snapMetres: over.snap ?? [0, 5, 0], provider: "fake", graphVersion: "v1",
  },
});
const stations = (n: number) => Array.from({ length: n }, (_, i) => ({ stationId: `s${i}`, coordinates: station(i) }));

test("along journey: one baseline A->B, then A->station->B per station, within the call budget", async () => {
  const f = fake((s) => okPath(s.length === 2 ? 30_000 : 31_000));
  const r = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(5) });
  assert.equal(r.status, "ok");
  assert.equal(f.calls.length, 6);
  assert.deepEqual(f.calls[0], [A, B]);
  assert.ok(f.calls.slice(1).every((c) => c.length === 3 && c[0] === A && c[2] === B));
  assert.ok(f.peak() <= CONCURRENCY);
  { assert.equal(r.routed.length, 5); assert.equal(r.baseline?.route.distanceMetres, "30000"); }
});

test("hard cap: at most 12 candidates are routed (13 calls) and the rest are reported", async () => {
  const f = fake(() => okPath(31_000));
  const r = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(20), maxCandidates: 50 });
  assert.equal(f.calls.length, 1 + MAX_CANDIDATES);
  assert.equal(r.status, "partial");
  assert.equal(r.unrouted.filter((u) => u.reason === "not_attempted_over_cap").length, 8);
});

test("baseline failure stops everything: no candidate is routed or costed", async () => {
  const f = fake(() => ({ kind: "no_route" }));
  const r = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(3) });
  assert.deepEqual(r, { status: "baseline_failed", reason: "no_route", routingCalls: 1 });
  assert.equal(f.calls.length, 1);
  const e = await routeJourney(fake(() => ({ kind: "error", reason: "x" })).provider, { mode: "along_journey", origin: A, destination: B, stations: stations(1) });
  assert.equal(e.status === "baseline_failed" && e.reason, "routing_error");
});

test("failed, unreachable, badly snapped and wrong-context stations are reported, not costed", async () => {
  const f = fake((s) => {
    if (s.length === 2) return okPath(30_000);
    const id = Math.round((s[1]!.lat - 51.5) / 0.01);
    if (id === 0) return { kind: "no_route" };
    if (id === 1) return { kind: "error", reason: "HTTP 503" };
    if (id === 2) return okPath(31_000, { snap: [0, 900, 0] });
    if (id === 3) return okPath(31_000, { ctx: "other|v2" });
    return okPath(31_000);
  });
  const r = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(5) });
  assert.equal(r.status, "partial");
  if (r.status === "partial") {
    assert.deepEqual(Object.fromEntries(r.unrouted.map((u) => [u.stationId, u.reason])),
      { s0: "no_route", s1: "routing_error", s2: "snap_too_far", s3: "routing_error" });
    assert.deepEqual(r.routed.map((x) => x.stationId), ["s4"]);
  }
});

test("a deadline stops new routes and reports the remainder", async () => {
  let t = 0;
  const f = fake(() => { t += 100; return okPath(31_000); });
  const r = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(8), deadline: 250, now: () => t });
  assert.equal(r.status, "partial");
  if (r.status === "partial") assert.ok(r.unrouted.some((u) => u.reason === "not_attempted_deadline"));
});

test("fuel trip routes origin->station->origin with no baseline", async () => {
  const f = fake(() => okPath(12_000));
  const r = await routeJourney(f.provider, { mode: "fuel_trip", origin: A, stations: stations(2) });
  assert.equal(f.calls.length, 2);
  assert.ok(f.calls.every((c) => c[0] === A && c[2] === A));
  assert.equal(r.status === "ok" && r.baseline, null);
});

// --- cache -------------------------------------------------------------------

test("cache: exact stops hit; nearby-but-different stops and errors do not", async () => {
  let n = 0;
  let fail = true;
  const inner: RoutingProvider = { contextId: "c", async route() { n++; return fail ? { kind: "error", reason: "x" } : okPath(1); } };
  const cache = withRouteCache(inner, { maxEntries: 2, ttlMs: 1000, now: () => 0 });
  await cache.route([A, B]); await cache.route([A, B]);
  assert.equal(n, 2); // Errors are not cached.
  fail = false;
  await cache.route([A, B]); await cache.route([A, B]);
  assert.equal(n, 3);
  await cache.route([A, { lat: B.lat + 0.0000001, lon: B.lon }]);
  assert.equal(n, 4); // No rounding of endpoints.
});

test("cache: entries expire and the cache is bounded", async () => {
  let n = 0;
  let t = 0;
  const inner: RoutingProvider = { contextId: "c", async route() { n++; return { kind: "no_route" }; } };
  const cache = withRouteCache(inner, { maxEntries: 2, ttlMs: 1000, now: () => t });
  await cache.route([A, B]); t = 999; await cache.route([A, B]);
  assert.equal(n, 1);
  t = 1001; await cache.route([A, B]);
  assert.equal(n, 2);
  for (const i of [1, 2, 3]) await cache.route([A, station(i)]);
  const before = n;
  await cache.route([A, B]); // Evicted by the bound.
  assert.equal(n, before + 1);
});

// --- hand-off to the economics engine ------------------------------------------

test("routed totals feed the economics engine; ferry routes are excluded, never silently costed", async () => {
  const f = fake((s) => {
    if (s.length === 2) return okPath(30_000);
    const id = Math.round((s[1]!.lat - 51.5) / 0.01);
    return okPath(30_000 + id * 800, { ferry: id === 2 });
  });
  const routing = await routeJourney(f.provider, { mode: "along_journey", origin: A, destination: B, stations: stations(3) });
  assert.notEqual(routing.status, "baseline_failed");
  if (routing.status === "baseline_failed" || !routing.baseline) return;
  const priced = (id: string, p: string) => ({ fuelType: "E10" as const, pencePerLitre: p, source: "Fuel Finder", sourceUpdatedAt: "2026-10-05T10:00:00Z", fetchedAt: "2026-10-05T11:00:00Z" });
  const prices: Record<string, string> = { s0: "140.0", s1: "132.9", s2: "120.0" };
  const result = rankFuelStops({
    mode: "along_journey", origin: A, destination: B, fuelType: "E10", mpgImperial: "45", litresToBuy: "30",
    routingContextId: f.provider.contextId, now: "2026-10-05T12:00:00Z", maxObservationAgeSeconds: 86_400,
    baselineRoute: routing.baseline.route,
    candidates: routing.routed.map((r) => ({
      stationId: r.stationId, name: r.stationId, coordinates: stations(3).find((s) => s.stationId === r.stationId)!.coordinates,
      price: priced(r.stationId, prices[r.stationId]!), route: r.route,
    })),
  });
  assert.equal(result.status, "ranked");
  assert.deepEqual(result.excluded.map((e) => [e.stationId, e.reason]), [["s2", "unpriced_ferry_or_toll"]]);
  assert.equal(result.labels?.cheapestPumpStationId, "s1");
});
