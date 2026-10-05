import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { optimiseResponse } from "../src/api/optimise.ts";
import type { Coordinates } from "../src/domain/types.ts";
import { buildLine, corridorBoxes, locateOnLine } from "../src/optimise/geometry.ts";
import { shortlist } from "../src/optimise/selection.ts";
import { createFuelRepository, haversine } from "../src/repositories/fuel.ts";
import { encodePolyline } from "../src/routing/polyline.ts";
import type { RouteOutcome, RoutingProvider } from "../src/routing/types.ts";
import { sqliteD1 } from "./d1-shim.ts";

const NOW = Date.parse("2026-10-05T12:00:00Z");
const A: Coordinates = { lat: 51.45, lon: -0.97 };
const B: Coordinates = { lat: 51.75, lon: -1.25 };
const onRoute = (t: number, offsetDeg = 0): Coordinates => ({ lat: A.lat + t * (B.lat - A.lat) + offsetDeg, lon: A.lon + t * (B.lon - A.lon) });

function seed(opts: { lastSuccess?: string | null } = {}) {
  const { db, sql } = sqliteD1();
  const lastSuccess = opts.lastSuccess === undefined ? "2026-10-05T11:50:00.000Z" : opts.lastSuccess;
  if (lastSuccess) sql.exec(`INSERT INTO ingestion_state (feed, last_success_at, last_status) VALUES ('fuel-finder-national', '${lastSuccess}', 'complete')`);
  let n = 0;
  const add = (name: string, at: Coordinates, pence: string, o: { fuel?: string; temp?: boolean; perm?: boolean } = {}) => {
    const id = `st${++n}-${name}`;
    sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`).run(id, name, "Brand", "RG1", at.lat, at.lon, o.temp ? 1 : 0, o.perm ? 1 : 0, 0, 0, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z");
    sql.prepare(`INSERT INTO current_prices VALUES (?,?,?,?,?,?)`).run(id, o.fuel ?? "E10", pence, "2026-10-05T08:00:00.000Z", "2026-10-05T08:00:00.000Z", "2026-10-05T08:00:00.000Z");
    return id;
  };
  return { db, sql, add, repo: createFuelRepository(db) };
}

/** Test engine: straight-line legs at 1.0x. Product code never does this; it only gives deterministic totals. */
function engine(over: { baseline?: RouteOutcome; failStation?: (c: Coordinates) => boolean; ferry?: boolean } = {}) {
  const calls: Coordinates[][] = [];
  const provider: RoutingProvider = {
    contextId: "test|v1",
    async route(stops) {
      calls.push([...stops]);
      if (stops.length === 2 && over.baseline) return over.baseline;
      if (stops.length === 3 && over.failStation?.(stops[1]!)) return { kind: "no_route" };
      let metres = 0;
      for (let i = 0; i < stops.length - 1; i++) metres += haversine(stops[i]!.lat, stops[i]!.lon, stops[i + 1]!.lat, stops[i + 1]!.lon);
      return {
        kind: "ok",
        path: {
          totals: { distanceMetres: String(Math.round(metres)), durationSeconds: String(Math.round(metres / 18)), contextId: "test|v1", usesFerry: over.ferry ?? false, usesToll: false },
          geometry: encodePolyline(stops.map((s) => [s.lat, s.lon])),
          snapMetres: stops.map(() => 3), provider: "test", graphVersion: "v1",
        },
      };
    },
  };
  return { provider, calls };
}

const request = (over: Record<string, unknown> = {}) => new Request("https://t.test/v1/journeys/optimise", {
  method: "POST", headers: { "content-type": "application/json" },
  body: JSON.stringify({ mode: "along_journey", origin: A, destination: B, fuelType: "E10", vehicle: { mpgImperial: "45" }, litresToBuy: "30", ...over }),
});
async function run(s: ReturnType<typeof seed>, e = engine(), over: Record<string, unknown> = {}) {
  const res = await optimiseResponse(request(over), s.repo, e.provider, () => NOW);
  return { res, body: (await res.json()) as any };
}

test("a cheaper station slightly off the route can beat a dearer one on it, with visible reasons", async () => {
  const s = seed();
  const dear = s.add("OnRoute", onRoute(0.5), "142.9");
  const cheap = s.add("OffByTwoKm", onRoute(0.5, 0.018), "128.9");
  s.add("TooFar", onRoute(0.5, 0.2), "99.9"); // ~22 km away: outside the corridor
  s.add("Closed", onRoute(0.5, 0.001), "90.0", { temp: true });
  s.add("Gone", onRoute(0.5, 0.001), "90.0", { perm: true });
  s.add("Diesel", onRoute(0.5, 0.001), "90.0", { fuel: "B7_STANDARD" });
  const { res, body } = await run(s);
  assert.equal(res.status, 200);
  assert.equal(body.status, "ranked");
  assert.equal(body.scope, "Best among the stations checked.");
  assert.equal(body.coverage.stationsInSearchArea, 2);
  assert.equal(body.coverage.partial, false);
  assert.equal(body.labels.bestOverallStationId, cheap);
  assert.equal(body.labels.cheapestPumpStationId, cheap);
  assert.equal(body.labels.smallestDetourStationId, dear);
  assert.equal(body.reference.stationId, dear);
  assert.ok(body.candidates[0].trueSavingPence > 0);
  assert.equal(body.candidates[0].priceSource.feedLastCheckedAt, "2026-10-05T11:50:00.000Z");
  assert.ok(body.candidates[0].routeGeometry.length > 0);
  assert.equal(body.routing.graphVersion, "v1");
});

test("limits drop stations whose routed detour is too large, and say so", async () => {
  const s = seed();
  s.add("OnRoute", onRoute(0.5), "142.9");
  const far = s.add("OffByTwoKm", onRoute(0.5, 0.018), "128.9");
  const { body } = await run(s, engine(), { limits: { maxExtraDistanceMetres: 20 } });
  assert.deepEqual(body.coverage.excludedByLimits, [{ stationId: far, reason: "over_detour_limit" }]);
  assert.equal(body.candidates.length, 1);
});

test("the shortlist is capped at 12 stations (13 routing calls) and spread along the journey", async () => {
  const s = seed();
  for (let i = 0; i < 40; i++) s.add(`S${i}`, onRoute(i / 40, (i % 3) * 0.004), String(130 + (i % 7)));
  const e = engine();
  const { body } = await run(s, e);
  assert.equal(body.coverage.stationsInSearchArea, 40);
  assert.equal(e.calls.length, 13);
  const fractions = e.calls.slice(1).map((c) => locateOnLine(buildLine([[A.lat, A.lon], [B.lat, B.lon]]), c[1]!).fraction);
  assert.ok(Math.min(...fractions) < 0.2 && Math.max(...fractions) > 0.8, "covers both ends of the route");
});

test("unroutable stations are reported as partial coverage, never costed", async () => {
  const s = seed();
  const bad = s.add("Unreachable", onRoute(0.3), "120.0");
  s.add("Fine", onRoute(0.6), "135.0");
  const { body } = await run(s, engine({ failStation: (c) => Math.abs(c.lat - onRoute(0.3).lat) < 1e-9 }));
  assert.equal(body.coverage.partial, true);
  assert.deepEqual(body.coverage.notRouted, [{ stationId: bad, reason: "no_route" }]);
  assert.equal(body.candidates.length, 1);
});

test("fuel trip routes origin to station and back, with no baseline", async () => {
  const s = seed();
  s.add("Near", { lat: A.lat + 0.02, lon: A.lon }, "131.9");
  s.add("TooFar", { lat: A.lat + 0.3, lon: A.lon }, "100.0");
  const e = engine();
  const { res, body } = await run(s, e, { mode: "fuel_trip", destination: undefined });
  assert.equal(res.status, 200);
  assert.equal(body.baseline, null);
  assert.equal(body.candidates.length, 1);
  assert.deepEqual([e.calls.length, e.calls[0]!.length], [1, 3]);
});

test("feed health gates recommendations: never loaded and stale are 503, not silent old prices", async () => {
  s1: {
    const none = seed({ lastSuccess: null });
    none.add("X", onRoute(0.5), "130");
    assert.equal((await run(none)).body.error.code, "NO_FUEL_DATA");
  }
  const stale = seed({ lastSuccess: "2026-10-05T04:00:00.000Z" });
  stale.add("X", onRoute(0.5), "130");
  const r = await run(stale);
  assert.equal(r.res.status, 503);
  assert.equal(r.body.error.code, "FUEL_DATA_STALE");
});

test("an unchanged price from days ago is still usable when the feed itself is fresh", async () => {
  const s = seed();
  s.add("Quiet", onRoute(0.5), "133.9");
  s.sql.exec("UPDATE current_prices SET price_last_updated='2026-09-20T08:00:00.000Z', price_change_effective='2026-09-20T08:00:00.000Z', observed_at='2026-09-20T08:00:00.000Z'");
  assert.equal((await run(s)).body.candidates.length, 1);
});

test("baseline failures: no route is 422, engine down is 502, ferry baseline needs review, long trips are refused", async () => {
  const s = seed();
  s.add("X", onRoute(0.5), "130");
  assert.equal((await run(s, engine({ baseline: { kind: "no_route" } }))).body.error.code, "NO_ROUTE");
  const down = await run(s, engine({ baseline: { kind: "error", reason: "HTTP 503" } }));
  assert.deepEqual([down.res.status, down.body.error.code], [502, "ROUTING_UNAVAILABLE"]);
  assert.equal((await run(s, engine({ ferry: true }))).body.error.code, "ROUTE_NEEDS_REVIEW");
  const long = await run(s, engine(), { origin: { lat: 50.4, lon: -4.1 }, destination: { lat: 58.6, lon: -3.1 } });
  assert.equal(long.body.error.code, "JOURNEY_TOO_LONG");
});

test("request validation, media type, size and configuration are enforced before any database or routing work", async () => {
  const noDb = { prepare() { throw new Error("must not be called"); }, batch() { throw new Error("no"); } } as unknown as D1Database;
  const repo = createFuelRepository(noDb);
  const e = engine();
  const bad: Record<string, unknown>[] = [
    { mode: "nearby" }, { origin: { lat: 0, lon: 0 } }, { fuelType: "E5" }, { vehicle: { mpgImperial: "0" } }, { vehicle: {} },
    { litresToBuy: "1e2" }, { litresToBuy: "9999" }, { limits: { maxExtraDistanceMetres: -1 } }, { mode: "fuel_trip" /* has destination */ },
  ];
  for (const over of bad) {
    const res = await optimiseResponse(request(over), repo, e.provider, () => NOW);
    assert.equal(res.status, 400, JSON.stringify(over));
  }
  assert.equal(e.calls.length, 0);
  const notJson = await optimiseResponse(new Request("https://t.test/x", { method: "POST", headers: { "content-type": "text/plain" }, body: "{}" }), repo, e.provider);
  assert.equal(notJson.status, 415);
  const big = await optimiseResponse(new Request("https://t.test/x", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ pad: "x".repeat(5000) }) }), repo, e.provider);
  assert.equal(big.status, 413);
  assert.equal((await optimiseResponse(request(), repo, null)).status, 503);
});

test("worker routes: POST only, and routing config comes from the environment", async () => {
  const s = seed();
  const get = await worker.fetch(new Request("https://t.test/v1/journeys/optimise"), { DB: s.db });
  assert.equal(get.status, 405);
  assert.equal(get.headers.get("allow"), "POST");
  const unconfigured = await worker.fetch(request(), { DB: s.db });
  assert.equal(unconfigured.status, 503);
  assert.equal(((await unconfigured.json()) as any).error.code, "ROUTING_NOT_CONFIGURED");
});

test("shortlist spreads picks instead of taking only the cheapest cluster", () => {
  const line = buildLine([[A.lat, A.lon], [B.lat, B.lon]]);
  const stations = [
    ...Array.from({ length: 10 }, (_, i) => ({ stationId: `cheap${i}`, name: "c", coordinates: onRoute(0.02 + i * 0.002), pencePerLitre: String(120 + i * 0.1) })),
    ...[0.3, 0.5, 0.7, 0.95].map((t, i) => ({ stationId: `spread${i}`, name: "s", coordinates: onRoute(t), pencePerLitre: "145" })),
  ];
  const { chosen } = shortlist(line, stations, 12);
  const ids = chosen.map((c) => c.stationId);
  for (const id of ["spread0", "spread1", "spread2", "spread3"]) assert.ok(ids.includes(id), id);
  assert.equal(new Set(ids).size, ids.length);
  assert.ok(ids.length <= 12);
});

test("geometry: offsets, fractions and corridor boxes", () => {
  const line = buildLine([[51.0, -1.0], [51.0, -0.9]]);
  const p = locateOnLine(line, { lat: 51.009, lon: -0.95 });
  assert.ok(Math.abs(p.offsetMetres - 1000) < 15);
  assert.ok(Math.abs(p.fraction - 0.5) < 0.01);
  const boxes = corridorBoxes(buildLine(Array.from({ length: 50 }, (_, i) => [51 + i * 0.01, -1] as [number, number])), 3500);
  assert.ok(boxes.length >= 2);
  assert.ok(boxes.every((b) => b.minLat < b.maxLat && b.minLon < b.maxLon));
});
