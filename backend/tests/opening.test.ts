import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { optimiseResponse } from "../src/api/optimise.ts";
import { normaliseStations } from "../src/ingestion/records.ts";
import { divisionFor, openingStatus, parseHours, serialiseHours, ukClock, type DayHours, type Division, type StationHours } from "../src/opening/status.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { runRefresh, type FeedClient } from "../src/ingestion/run.ts";
import { BATCH_SIZE, type BatchOutcome } from "../src/ingestion/pagination.ts";
import { sqliteD1 } from "./d1-shim.ts";

const day = (open: string, close: string, is24Hours = false): DayHours => ({ open, close, is24Hours });
const week = (d: DayHours, over: Record<number, DayHours> = {}, extra: { bankHoliday?: DayHours | null; division?: Division | null } = {}): StationHours => ({
  week: Array.from({ length: 7 }, (_, i) => over[i] ?? d),
  bankHoliday: extra.bankHoliday ?? null,
  division: extra.division === undefined ? "england-and-wales" : extra.division,
});
// 2026-10-05 is a Monday, and BST (UTC+1) applies, so 12:00Z is 13:00 in the UK.
const at = (iso: string) => new Date(iso);

test("UK clock follows British Summer Time and GMT", () => {
  assert.deepEqual(ukClock(at("2026-10-05T12:00:00Z")), { date: "2026-10-05", day: 0, minutes: 13 * 60 });
  assert.deepEqual(ukClock(at("2026-12-07T12:00:00Z")), { date: "2026-12-07", day: 0, minutes: 12 * 60 });
  assert.deepEqual(ukClock(at("2026-10-05T23:30:00Z")), { date: "2026-10-06", day: 1, minutes: 30 }); // 00:30 Tuesday BST
});

test("open, closed and closing-soon from usual hours", () => {
  const w = week(day("06:00", "22:00"));
  assert.deepEqual(openingStatus(w, at("2026-10-05T12:00:00Z")), { state: "open", basis: "usual_hours", closesAt: "22:00", closesInMinutes: 9 * 60 });
  assert.equal(openingStatus(w, at("2026-10-05T20:50:00Z")).closesInMinutes, 10); // 21:50 local
  assert.deepEqual(openingStatus(w, at("2026-10-05T21:30:00Z")), { state: "closed", basis: "usual_hours", opensAt: "06:00" }); // 22:30
  assert.deepEqual(openingStatus(w, at("2026-10-05T03:00:00Z")), { state: "closed", basis: "usual_hours", opensAt: "06:00" }); // 04:00
});

test("24-hour days, missing hours and open-equals-close are handled as the provider describes", () => {
  assert.deepEqual(openingStatus(week(day("00:00", "23:59", true)), at("2026-10-05T03:00:00Z")), { state: "open", basis: "usual_hours", is24Hours: true });
  assert.equal(openingStatus(null, at("2026-10-05T12:00:00Z")).state, "unknown");
  // 00:00 to 00:00 without the 24-hour flag is closed all day (Fuel Finder, 6 October 2026).
  assert.equal(openingStatus(week(day("00:00", "00:00")), at("2026-10-05T12:00:00Z")).state, "closed");
  // Any other open == close is not described by the provider, so it stays unknown.
  assert.equal(openingStatus(week(day("10:00", "10:00")), at("2026-10-05T12:00:00Z")).state, "unknown");
});

test("a day closed all day reports the next day's opening time", () => {
  const sundayClosed = week(day("07:00", "21:00"), { 6: day("00:00", "00:00") });
  assert.deepEqual(openingStatus(sundayClosed, at("2026-10-11T11:00:00Z")), { state: "closed", basis: "usual_hours", opensAt: "07:00" }); // Sunday noon
});

test("bank-holiday hours replace usual hours only on that nation's bank holidays", () => {
  const hols = { bankHoliday: day("10:00", "16:00") };
  // 25 Dec 2026 is a Friday and a bank holiday everywhere; 13:00 GMT.
  const xmas = at("2026-12-25T13:00:00Z");
  assert.deepEqual(openingStatus(week(day("06:00", "22:00"), {}, hols), xmas), { state: "open", basis: "bank_holiday_hours", closesAt: "16:00", closesInMinutes: 180 });
  assert.equal(openingStatus(week(day("06:00", "22:00"), {}, { bankHoliday: day("00:00", "00:00") }), xmas).state, "closed");
  // 30 Nov 2026 (St Andrew's Day) is a bank holiday in Scotland only.
  const standrew = at("2026-11-30T17:00:00Z"); // 17:00 GMT
  assert.equal(openingStatus(week(day("06:00", "22:00"), {}, { ...hols, division: "scotland" }), standrew).state, "closed");
  assert.equal(openingStatus(week(day("06:00", "22:00"), {}, { ...hols, division: "england-and-wales" }), standrew).state, "open");
  // Nation unknown on a date that is a bank holiday somewhere: we cannot tell, so unknown.
  assert.equal(openingStatus(week(day("06:00", "22:00"), {}, { ...hols, division: null }), standrew).state, "unknown");
  // No separate holiday hours ("standard"): usual hours apply even on a holiday.
  assert.equal(openingStatus(week(day("06:00", "22:00")), xmas).basis, "usual_hours");
});

test("beyond the bank-holiday calendar, usual hours are used and flagged", () => {
  const r = openingStatus(week(day("06:00", "22:00"), {}, { bankHoliday: day("10:00", "11:00") }), at("2029-12-25T13:00:00Z"));
  assert.equal(r.basis, "usual_hours");
  assert.equal(r.calendarExpired, true);
});

test("the nation comes from a clear country, else from the postcode area", () => {
  assert.equal(divisionFor("ENGLAND", "RG1 1AA"), "england-and-wales");
  assert.equal(divisionFor("Wales", null), "england-and-wales");
  assert.equal(divisionFor("scotland", null), "scotland");
  assert.equal(divisionFor("Northern Ireland", null), "northern-ireland");
  assert.equal(divisionFor("UNITED KINGDOM", "BT1 1AA"), "northern-ireland");
  assert.equal(divisionFor(null, "EH1 1AA"), "scotland");
  assert.equal(divisionFor("", "G2 1AA"), "scotland");
  assert.equal(divisionFor("E", "TD15 1AA"), "england-and-wales");
  assert.equal(divisionFor("E", "TD1 1AA"), "scotland");
  assert.equal(divisionFor("UNITED KINGDOM", "SW1A 1AA"), "england-and-wales");
  assert.equal(divisionFor(null, null), null);
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
  const w = week(day("06:00", "22:00"), { 2: day("00:00", "23:59", true) }, { bankHoliday: day("10:00", "16:00"), division: "scotland" });
  assert.deepEqual(parseHours(JSON.parse(serialiseHours(w))), w);
  assert.equal(parseHours([1, 2]), null);
  assert.equal(parseHours(Array.from({ length: 7 }, () => ({ o: "6am", c: "10pm", h: 0 }))), null);
  // Earlier stored format (a bare week) still reads, as usual hours with no holiday data.
  const legacy = parseHours(Array.from({ length: 7 }, () => ({ o: "06:00", c: "22:00", h: 0 })));
  assert.equal(legacy?.bankHoliday, null);
  assert.equal(legacy?.division, null);
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
  assert.deepEqual(ok.openingHours?.week[0], { open: "06:00", close: "23:59", is24Hours: false });
  assert.equal(ok.openingHours?.bankHoliday, null); // The fixture's holiday entry is type "standard".
  const special = normaliseStations([{ ...feedStation({ ...usual("06:00:00", "22:00:00"), bank_holiday: { type: "bank holiday", open_time: "00:00:00", close_time: "00:00:00", is_24_hours: false } }), location: { latitude: 51.5, longitude: -1, country: "SCOTLAND", postcode: "EH1 1AA" } }]).valid[0]!;
  assert.deepEqual(special.openingHours?.bankHoliday, { open: "00:00", close: "00:00", is24Hours: false });
  assert.equal(special.openingHours?.division, "scotland");
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
  const add = (name: string, at: { lat: number; lon: number }, pence: string, hours: StationHours | null) => {
    const id = `st${++n}-${name}`;
    sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,0,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',?,NULL)`).run(id, name, "B", "RG1", at.lat, at.lon, hours ? serialiseHours(hours) : null);
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
  for (const q of ["&fuelType=E10", "&litres=30", "&fuelType=HVO&litres=30", "&fuelType=E10&litres=0", "&fuelType=E10&litres=1e2"]) {
    assert.equal((await get(q)).status, 400, q);
  }
  const plain = (await (await get("")).json()) as any;
  assert.equal("fill" in plain.stations[0], false);
});
