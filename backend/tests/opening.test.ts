import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { optimiseResponse } from "../src/api/optimise.ts";
import { normaliseStations } from "../src/ingestion/records.ts";
import { openingStatus, parseWeek, serialiseWeek, ukClock, type DayHours, type WeekHours } from "../src/opening/status.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { runRefresh, type FeedClient } from "../src/ingestion/run.ts";
import { BATCH_SIZE, type BatchOutcome } from "../src/ingestion/pagination.ts";
import { sqliteD1 } from "./d1-shim.ts";

const day = (open: string, close: string, is24Hours = false): DayHours => ({ open, close, is24Hours });
const week = (d: DayHours, over: Record<number, DayHours> = {}): WeekHours => Array.from({ length: 7 }, (_, i) => over[i] ?? d);
// 2026-10-05 is a Monday, and BST (UTC+1) applies, so 12:00Z is 13:00 in the UK.
const at = (iso: string) => new Date(iso);

test("UK clock follows British Summer Time and GMT", () => {
  assert.deepEqual(ukClock(at("2026-10-05T12:00:00Z")), { day: 0, minutes: 13 * 60 });
  assert.deepEqual(ukClock(at("2026-12-07T12:00:00Z")), { day: 0, minutes: 12 * 60 });
  assert.deepEqual(ukClock(at("2026-10-05T23:30:00Z")), { day: 1, minutes: 30 }); // 00:30 Tuesday BST
});

test("open, closed and closing-soon from usual hours", () => {
  const w = week(day("06:00", "22:00"));
  assert.deepEqual(openingStatus(w, at("2026-10-05T12:00:00Z")), { state: "open", basis: "usual_hours", closesAt: "22:00", closesInMinutes: 9 * 60 });
  assert.equal(openingStatus(w, at("2026-10-05T20:50:00Z")).closesInMinutes, 10); // 21:50 local
  assert.deepEqual(openingStatus(w, at("2026-10-05T21:30:00Z")), { state: "closed", basis: "usual_hours", opensAt: "06:00" }); // 22:30
  assert.deepEqual(openingStatus(w, at("2026-10-05T03:00:00Z")), { state: "closed", basis: "usual_hours", opensAt: "06:00" }); // 04:00
});

test("24-hour days, missing hours and open-equals-close are handled honestly", () => {
  assert.deepEqual(openingStatus(week(day("00:00", "23:59", true)), at("2026-10-05T03:00:00Z")), { state: "open", basis: "usual_hours", is24Hours: true });
  assert.equal(openingStatus(null, at("2026-10-05T12:00:00Z")).state, "unknown");
  assert.equal(openingStatus(week(day("00:00", "00:00")), at("2026-10-05T12:00:00Z")).state, "unknown");
  assert.equal(openingStatus(week(day("10:00", "10:00")), at("2026-10-05T12:00:00Z")).state, "unknown");
});

test("hours past midnight spill into the next day, and close at 00:00 means end of day", () => {
  const w = week(day("06:00", "02:00"));
  assert.deepEqual(openingStatus(w, at("2026-10-05T23:00:00Z")), { state: "open", basis: "usual_hours", closesAt: "02:00", closesInMinutes: 120 }); // 00:00 Tuesday
  assert.equal(openingStatus(w, at("2026-10-06T02:00:00Z")).state, "closed"); // 03:00
  const midnight = week(day("06:00", "00:00"));
  assert.equal(openingStatus(midnight, at("2026-10-05T22:30:00Z")).closesInMinutes, 30); // 23:30
  assert.equal(openingStatus(midnight, at("2026-10-05T23:30:00Z")).state, "closed"); // 00:30, closed until 06:00
});

test("a closed day uses the next day's opening time when known", () => {
  const w = week(day("06:00", "22:00"), { 1: day("08:00", "20:00") });
  assert.equal(openingStatus(w, at("2026-10-05T21:30:00Z")).opensAt, "08:00");
});

test("storage format round-trips and rejects malformed data", () => {
  const w = week(day("06:00", "22:00"), { 2: day("00:00", "23:59", true) });
  assert.deepEqual(parseWeek(JSON.parse(serialiseWeek(w))), w);
  assert.equal(parseWeek([1, 2]), null);
  assert.equal(parseWeek(Array.from({ length: 7 }, () => ({ o: "6am", c: "10pm", h: 0 }))), null);
});

const feedStation = (hours: unknown) => ({
  node_id: "a".repeat(64), trading_name: "S", location: { latitude: 51.5, longitude: -1 },
  opening_times: hours,
});
const usual = (open: string, close: string, h24 = false) => ({
  usual_days: Object.fromEntries(["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"].map((d) => [d, { open, close, is_24_hours: h24 }])),
  bank_holiday: { type: "standard", open_time: "00:00:00", close_time: "00:00:00", is_24_hours: false },
});

test("feed hours become HH:MM weeks; bad or missing hours never quarantine a station", () => {
  const ok = normaliseStations([feedStation(usual("06:00:00", "23:59:00"))]).valid[0]!;
  assert.deepEqual(ok.openingHours?.[0], { open: "06:00", close: "23:59", is24Hours: false });
  for (const bad of [null, undefined, {}, { usual_days: { monday: { open: "x", close: "y" } } }, usual("6am", "10pm")]) {
    const r = normaliseStations([feedStation(bad)]);
    assert.equal(r.valid.length, 1);
    assert.equal(r.valid[0]!.openingHours, null);
    assert.equal(r.quarantined.length, 0);
  }
});

function oneStationFeed(hours: unknown): FeedClient {
  const stations = Array.from({ length: 1200 }, (_, i) => ({ ...feedStation(hours), node_id: i.toString(16).padStart(64, "0"), location: { latitude: 51.4 + i * 0.0001, longitude: -1 } }));
  const prices = stations.map((s) => ({ node_id: s.node_id, trading_name: "S", fuel_prices: [] }));
  const serve = (all: unknown[]) => async (b: number): Promise<BatchOutcome> => {
    const start = (b - 1) * BATCH_SIZE;
    return start >= all.length ? { kind: "not-available" } : { kind: "records", records: all.slice(start, start + BATCH_SIZE) };
  };
  return { fetchStationBatch: serve(stations), fetchPriceBatch: serve(prices) };
}

test("a refresh stores hours, and a later change to hours rewrites only what changed", async () => {
  const { db, sql } = sqliteD1();
  const repo = createFuelRepository(db);
  let t = Date.parse("2026-10-05T12:00:00Z");
  const first = await runRefresh(oneStationFeed(usual("06:00:00", "22:00:00")), repo, () => t);
  assert.equal(first.status, "complete");
  assert.match(String((sql.prepare("SELECT opening_hours AS h FROM stations LIMIT 1").get() as { h: string }).h), /"o":"06:00"/);
  t += 900_000;
  const same = await runRefresh(oneStationFeed(usual("06:00:00", "22:00:00")), repo, () => t);
  assert.equal(same.status === "complete" && same.stationWrites, 0);
  t += 900_000;
  const changed = await runRefresh(oneStationFeed(usual("07:00:00", "22:00:00")), repo, () => t);
  assert.equal(changed.status === "complete" && changed.stationWrites, 1200);
});

// --- API ---------------------------------------------------------------------

const KEY = "test-key-0123456789-abcdefghij";
const NOW = Date.parse("2026-10-05T12:00:00Z"); // 13:00 Monday in the UK
const A = { lat: 51.45, lon: -0.97 };
const B = { lat: 51.75, lon: -1.25 };
const onRoute = (t: number, off = 0) => ({ lat: A.lat + t * (B.lat - A.lat) + off, lon: A.lon + t * (B.lon - A.lon) });

function seed() {
  const { db, sql } = sqliteD1();
  sql.exec(`INSERT INTO ingestion_state (feed, last_success_at, last_status) VALUES ('fuel-finder-national', '2026-10-05T11:50:00.000Z', 'complete')`);
  let n = 0;
  const add = (name: string, at: { lat: number; lon: number }, pence: string, hours: WeekHours | null) => {
    const id = `st${++n}-${name}`;
    sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,0,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',?)`).run(id, name, "B", "RG1", at.lat, at.lon, hours ? serialiseWeek(hours) : null);
    sql.prepare(`INSERT INTO current_prices VALUES (?,?,?,?,?,?)`).run(id, "E10", pence, "2026-10-04T08:00:00.000Z", "2026-10-04T08:00:00.000Z", "2026-10-04T08:00:00.000Z");
    return id;
  };
  return { db, sql, add, repo: createFuelRepository(db) };
}

test("optimise leaves out stations closed now, keeps unknown ones flagged, and says how many it dropped", async () => {
  const s = seed();
  const openId = s.add("Open", onRoute(0.4), "140.0", week(day("06:00", "22:00")));
  const closedId = s.add("ClosedCheap", onRoute(0.5), "100.0", week(day("06:00", "12:00")));
  const unknownId = s.add("Unknown", onRoute(0.6), "141.0", null);
  const soonId = s.add("ClosesSoon", onRoute(0.7), "142.0", week(day("06:00", "13:20")));
  const provider = {
    contextId: "t|v1",
    async route(stops: { lat: number; lon: number }[]) {
      return { kind: "ok" as const, path: { totals: { distanceMetres: String(40_000 + stops.length), durationSeconds: "2400", contextId: "t|v1", usesFerry: false, usesToll: false }, geometry: "??", snapMetres: [1, 1, 1], provider: "t", graphVersion: "v1" } };
    },
  };
  const real = { ...provider, async route(stops: { lat: number; lon: number }[]) {
    const geometry = (await import("../src/routing/polyline.ts")).encodePolyline(stops.map((p) => [p.lat, p.lon]));
    const r = await provider.route(stops);
    return { ...r, path: { ...r.path, geometry } };
  } };
  const res = await optimiseResponse(new Request("https://t.test/x", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ mode: "along_journey", origin: A, destination: B, fuelType: "E10", vehicle: { mpgImperial: "45" }, litresToBuy: "30" }),
  }), s.repo, real as never, () => NOW);
  const body = (await res.json()) as any;
  assert.equal(res.status, 200);
  assert.equal(body.coverage.closedNowExcluded, 1);
  const ids = body.candidates.map((c: any) => c.stationId);
  assert.ok(!ids.includes(closedId));
  assert.deepEqual(ids.sort(), [openId, soonId, unknownId].sort());
  const by = Object.fromEntries(body.candidates.map((c: any) => [c.stationId, c.opening]));
  assert.equal(by[openId].state, "open");
  assert.equal(by[unknownId].state, "unknown");
  assert.equal(by[soonId].closesInMinutes, 20);
});

test("nearby reports opening status for every station without hiding closed ones", async () => {
  const s = seed();
  s.add("Open", onRoute(0.1), "140.0", week(day("06:00", "22:00")));
  s.add("Closed", onRoute(0.1, 0.002), "140.0", week(day("06:00", "12:00")));
  s.add("Unknown", onRoute(0.1, 0.004), "140.0", null);
  const env = { DB: s.db, API_KEYS: KEY };
  const real = Date.now;
  Date.now = () => NOW;
  try {
    const res = await worker.fetch(new Request(`https://t.test/v1/stations/nearby?lat=${(A.lat + 0.03).toFixed(5)}&lon=${(A.lon - 0.028).toFixed(5)}&radiusMetres=2000`, { headers: { "x-tank-bear-key": KEY } }), env);
    const body = (await res.json()) as any;
    assert.equal(res.status, 200, JSON.stringify(body));
    assert.deepEqual(body.stations.map((x: any) => x.opening.state).sort(), ["closed", "open", "unknown"]);
    assert.equal(body.stations.find((x: any) => x.opening.state === "closed").opening.opensAt, "06:00");
  } finally { Date.now = real; }
});

test("nearby can return the fill cost for one fuel using the economics rounding, and validates the pair", async () => {
  const s = seed();
  s.add("Open", onRoute(0.1), "139.9", week(day("06:00", "22:00")));
  const env = { DB: s.db, API_KEYS: KEY };
  const base = `https://t.test/v1/stations/nearby?lat=${(A.lat + 0.03).toFixed(5)}&lon=${(A.lon - 0.028).toFixed(5)}&radiusMetres=2000`;
  const get = (q: string) => worker.fetch(new Request(base + q, { headers: { "x-tank-bear-key": KEY } }), env);
  const ok = (await (await get("&fuelType=E10&litres=30")).json()) as any;
  assert.equal(ok.stations[0].fill.fillCostPence, 4197); // 30 L x 139.9p
  assert.equal(ok.stations[0].fill.pencePerLitre, "139.9");
  const diesel = (await (await get("&fuelType=B7&litres=30")).json()) as any;
  assert.equal(diesel.stations[0].fill, null); // No diesel price here: nothing invented.
  for (const q of ["&fuelType=E10", "&litres=30", "&fuelType=E5&litres=30", "&fuelType=E10&litres=0", "&fuelType=E10&litres=1e2"]) {
    assert.equal((await get(q)).status, 400, q);
  }
  const plain = (await (await get("")).json()) as any;
  assert.equal("fill" in plain.stations[0], false);
});
