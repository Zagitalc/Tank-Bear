import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { assessCoverage, buildHistory, fromThousandths, medianThousandths, toThousandths } from "../src/history/series.ts";
import { runRefresh, type FeedClient } from "../src/ingestion/run.ts";
import { BATCH_SIZE, type BatchOutcome } from "../src/ingestion/pagination.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { sqliteD1 } from "./d1-shim.ts";

const NOW = new Date("2026-10-12T12:00:00Z");
const day = (n: number) => new Date(NOW.getTime() - n * 86_400_000).toISOString();

test("thousandths arithmetic is exact and prints without float noise", () => {
  assert.equal(toThousandths("139.9"), 139900);
  assert.equal(toThousandths("132.999"), 132999);
  assert.equal(fromThousandths(139900), "139.9");
  assert.equal(fromThousandths(-2100), "-2.1");
  assert.equal(fromThousandths(0), "0");
  assert.equal(fromThousandths(500), "0.5");
  assert.equal(medianThousandths([]), null);
  assert.equal(medianThousandths([3000, 1000, 2000]), 2000);
  assert.equal(medianThousandths([1000, 2001]), 1501); // Mean of the middle two, rounded half up.
});

test("coverage: nothing recorded, too short, gappy and complete windows are told apart", () => {
  assert.equal(assessCoverage(7, NOW, null, 0).sufficientForTrend, false);
  const short = assessCoverage(7, NOW, day(0.5), 40);
  assert.equal(short.sufficientForTrend, false);
  assert.match(short.reason!, /0\.5 of the 7 days/);
  const gappy = assessCoverage(7, NOW, day(8), 300); // 672 expected
  assert.equal(gappy.sufficientForTrend, false);
  assert.match(gappy.reason!, /missed/);
  const good = assessCoverage(7, NOW, day(8), 650);
  assert.equal(good.sufficientForTrend, true);
  assert.equal(good.reason, null);
  assert.ok(good.coverage > 0.9 && good.coverage <= 1);
});

test("history keeps the price in force at the window start and gives a trend only with enough coverage", () => {
  const rows = [
    { effective: day(10), pencePerLitre: "140.9", observedAt: day(8) },
    { effective: day(4), pencePerLitre: "138.9", observedAt: day(4) },
    { effective: day(1), pencePerLitre: "136.9", observedAt: day(1) },
  ];
  const good = buildHistory(rows, new Date(day(7)), NOW, assessCoverage(7, NOW, day(8), 660), "136.9");
  assert.deepEqual(good.points.map((p) => p.pencePerLitre), ["140.9", "138.9", "136.9"]);
  assert.equal(good.points[0]!.at, day(7)); // Carried to the window start.
  assert.equal(good.startKnown, true);
  assert.deepEqual(good.trend, { changePencePerLitre: "-4", direction: "down", sincePencePerLitre: "140.9" });

  const short = buildHistory(rows, new Date(day(7)), NOW, assessCoverage(7, NOW, day(0.5), 40), "136.9");
  assert.equal(short.trend, null);
  assert.equal(short.points.length, 3); // The recorded points are still shown.
});

test("an unknown start gives no trend even with good coverage, and unchanged is reported as such", () => {
  const onlyRecent = [{ effective: day(2), pencePerLitre: "139.9", observedAt: day(2) }];
  const r = buildHistory(onlyRecent, new Date(day(7)), NOW, assessCoverage(7, NOW, day(8), 660), "139.9");
  assert.equal(r.startKnown, false);
  assert.equal(r.trend, null);
  const flat = buildHistory([{ effective: day(9), pencePerLitre: "139.9", observedAt: day(8) }], new Date(day(7)), NOW, assessCoverage(7, NOW, day(8), 660), "139.9");
  assert.equal(flat.trend?.direction, "unchanged");
});

// --- through the Worker ------------------------------------------------------

const KEY = "test-key-0123456789-abcdefghij";
const ID = "ab".repeat(32);

function seed(opts: { watchedDays: number; missEvery?: number }) {
  const { db, sql } = sqliteD1();
  sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,0,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',NULL)`).run(ID, "History Garage", "B", "RG1", 51.45, -0.97);
  sql.prepare(`INSERT INTO current_prices VALUES (?,?,?,?,?,?)`).run(ID, "E10", "136.9", day(1), day(1), day(1));
  const change = sql.prepare(`INSERT INTO price_changes (node_id, feed_fuel_type, pence_per_litre, price_last_updated, price_change_effective, observed_at) VALUES (?,?,?,?,?,?)`);
  change.run(ID, "E10", "140.9", day(10), day(10), day(9));
  change.run(ID, "E10", "138.9", day(4), day(4), day(4));
  change.run(ID, "E10", "136.9", day(1), day(1), day(1));
  change.run(ID, "B7_STANDARD", "150.9", day(10), day(10), day(9));
  const log = sql.prepare(`INSERT INTO refresh_log VALUES (?,?,?)`);
  const total = Math.floor((opts.watchedDays * 24 * 60) / 15);
  for (let i = 0; i < total; i++) {
    if (opts.missEvery && i % opts.missEvery === 0) continue;
    log.run(new Date(NOW.getTime() - opts.watchedDays * 86_400_000 + i * 900_000).toISOString(), 8000, 7900);
  }
  return { db, sql };
}

async function get(db: D1Database, path: string, key: string | null = KEY) {
  const real = Date.now;
  Date.now = () => NOW.getTime();
  try {
    return await worker.fetch(new Request(`https://t.test${path}`, key ? { headers: { "x-tank-bear-key": key } } : {}), { DB: db, API_KEYS: KEY });
  } finally { Date.now = real; }
}

test("a station watched for the whole week gets a series and a trend", async () => {
  const { db } = seed({ watchedDays: 8 });
  const res = await get(db, `/v1/stations/${ID}/history?fuelType=E10`);
  const body = (await res.json()) as any;
  assert.equal(res.status, 200);
  assert.equal(body.station.name, "History Garage");
  assert.equal(body.coverage.sufficientForTrend, true);
  assert.deepEqual(body.points.map((p: any) => p.pencePerLitre), ["140.9", "138.9", "136.9"]);
  assert.equal(body.trend.changePencePerLitre, "-4");
  assert.equal(body.current.pencePerLitre, "136.9");
  assert.match(body.note, /provider's own change time/);
});

test("a station watched for only one day shows what is known but makes no trend claim", async () => {
  const { db } = seed({ watchedDays: 1 });
  const body = (await (await get(db, `/v1/stations/${ID}/history?fuelType=E10&days=7`)).json()) as any;
  assert.equal(body.trend, null);
  assert.equal(body.coverage.sufficientForTrend, false);
  assert.match(body.coverage.reason, /1\.0 of the 7 days/);
  assert.ok(body.points.length >= 1);
});

test("a window with many missed refreshes is not called a trend", async () => {
  const { db } = seed({ watchedDays: 8, missEvery: 2 });
  const body = (await (await get(db, `/v1/stations/${ID}/history?fuelType=E10`)).json()) as any;
  assert.equal(body.trend, null);
  assert.match(body.coverage.reason, /missed/);
});

test("history validates input, needs the key, and 404s unknown stations", async () => {
  const { db } = seed({ watchedDays: 8 });
  assert.equal((await get(db, `/v1/stations/${ID}/history?fuelType=E10`, null)).status, 401);
  for (const q of ["", "?fuelType=E5", "?fuelType=E10&days=0", "?fuelType=E10&days=99", "?fuelType=E10&days=x"]) {
    assert.equal((await get(db, `/v1/stations/${ID}/history${q}`)).status, 400, q);
  }
  assert.equal((await get(db, `/v1/stations/not-an-id/history?fuelType=E10`)).status, 400);
  assert.equal((await get(db, `/v1/stations/${"cd".repeat(32)}/history?fuelType=E10`)).status, 404);
  const diesel = (await (await get(db, `/v1/stations/${ID}/history?fuelType=B7`)).json()) as any;
  assert.equal(diesel.current, null);
  assert.equal(diesel.points.length, 1);
});

// --- refresh log -------------------------------------------------------------

function tinyFeed(): FeedClient {
  const stations = Array.from({ length: 1200 }, (_, i) => ({ node_id: i.toString(16).padStart(64, "0"), trading_name: "S", location: { latitude: 51.4, longitude: -1 } }));
  const prices = stations.map((s) => ({ node_id: s.node_id, trading_name: "S", fuel_prices: [] }));
  const serve = (all: unknown[]) => async (b: number): Promise<BatchOutcome> => {
    const start = (b - 1) * BATCH_SIZE;
    return start >= all.length ? { kind: "not-available" } : { kind: "records", records: all.slice(start, start + BATCH_SIZE) };
  };
  return { fetchStationBatch: serve(stations), fetchPriceBatch: serve(prices) };
}

test("each complete refresh is logged, failed ones are not, and old log rows are purged", async () => {
  const { db, sql } = sqliteD1();
  const repo = createFuelRepository(db);
  let t = Date.parse("2026-10-05T12:00:00Z");
  await runRefresh(tinyFeed(), repo, () => t);
  t += 900_000;
  await runRefresh(tinyFeed(), repo, () => t);
  const failing: FeedClient = { fetchStationBatch: async () => ({ kind: "error", reason: "HTTP 503" }), fetchPriceBatch: async () => ({ kind: "error", reason: "x" }) };
  t += 900_000;
  await runRefresh(failing, repo, () => t);
  assert.equal(Number((sql.prepare("SELECT COUNT(*) AS n FROM refresh_log").get() as { n: number }).n), 2);
  const cov = await repo.refreshCoverage("2026-10-05T12:10:00.000Z");
  assert.deepEqual(cov, { first: "2026-10-05T12:00:00.000Z", observedSince: 1 });
  await repo.purgeRefreshLog("2026-10-05T12:10:00.000Z");
  assert.equal(Number((sql.prepare("SELECT COUNT(*) AS n FROM refresh_log").get() as { n: number }).n), 1);
});

// --- local comparison --------------------------------------------------------

test("nearby gives a local median and each station's difference from it, ignoring unavailable ones", async () => {
  const { db, sql } = sqliteD1();
  sql.exec(`INSERT INTO ingestion_state (feed, last_success_at, last_status) VALUES ('fuel-finder-national', '2026-10-12T11:50:00.000Z', 'complete')`);
  const add = (id: string, lat: number, pence: string | null, temp = 0) => {
    sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,?,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',NULL)`).run(id, id, "B", "RG1", lat, -0.97, temp);
    if (pence) sql.prepare(`INSERT INTO current_prices VALUES (?,?,?,?,?,?)`).run(id, "E10", pence, day(1), day(1), day(1));
  };
  add("a", 51.450, "130.9"); add("b", 51.451, "140.9"); add("c", 51.452, "150.9"); add("d", 51.453, "90.0", 1); add("e", 51.454, null);
  const res = await get(db, "/v1/stations/nearby?lat=51.45&lon=-0.97&radiusMetres=2000&fuelType=E10&litres=30");
  const body = (await res.json()) as any;
  assert.deepEqual(body.localSummary, { fuelType: "E10", stationsWithPrice: 3, medianPencePerLitre: "140.9", cheapestPencePerLitre: "130.9", dearestPencePerLitre: "150.9" });
  const by = Object.fromEntries(body.stations.map((s: any) => [s.id, s.fill?.vsLocalMedianPencePerLitre]));
  assert.deepEqual([by.a, by.b, by.c], ["-10", "0", "10"]);
  assert.equal(by.e, undefined);
});
