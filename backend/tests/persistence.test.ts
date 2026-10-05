import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";
import { runRefresh, type FeedClient } from "../src/ingestion/run.ts";
import { BATCH_SIZE, type BatchOutcome } from "../src/ingestion/pagination.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { sqliteD1 } from "./d1-shim.ts";

const N = 1200;
const id = (n: number) => n.toString(16).padStart(64, "0");
const iso = (mins: number) => new Date(Date.parse("2026-10-05T09:00:00Z") + mins * 60_000).toISOString();

interface World { stations: Record<string, unknown>[]; prices: Record<string, unknown>[] }
function world(count = N): World {
  return {
    // Spread around Reading (51.45, -0.97) so nearby tests have known distances.
    stations: Array.from({ length: count }, (_, i) => ({
      node_id: id(i), trading_name: `Forecourt ${i}`, brand_name: "B", temporary_closure: i === 3, permanent_closure: false,
      is_motorway_service_station: false, is_supermarket_service_station: false,
      location: { postcode: "RG1 1AA", latitude: 51.45 + i * 0.001, longitude: -0.97 },
    })),
    prices: Array.from({ length: count }, (_, i) => ({
      node_id: id(i), trading_name: `Forecourt ${i}`,
      fuel_prices: [
        { fuel_type: "E10", price: 130 + (i % 10), price_last_updated: iso(1), price_change_effective_timestamp: iso(0) },
        { fuel_type: "B7_STANDARD", price: 140 + (i % 10), price_last_updated: iso(1), price_change_effective_timestamp: iso(0) },
      ],
    })),
  };
}
function client(w: World, opts: { failPriceBatch?: number } = {}): FeedClient {
  const serve = (all: unknown[], failAt?: number) => async (b: number): Promise<BatchOutcome> => {
    if (b === failAt) return { kind: "error", reason: "HTTP 503" };
    const start = (b - 1) * BATCH_SIZE;
    return start >= all.length ? { kind: "not-available" } : { kind: "records", records: all.slice(start, start + BATCH_SIZE) };
  };
  return { fetchStationBatch: serve(w.stations), fetchPriceBatch: serve(w.prices, opts.failPriceBatch) };
}
const count = (sql: ReturnType<typeof sqliteD1>["sql"], table: string) =>
  Number((sql.prepare(`SELECT COUNT(*) AS n FROM ${table}`).get() as { n: number }).n);
const clock = (start = "2026-10-05T12:00:00Z") => { let t = Date.parse(start); return { now: () => t, advance: (ms: number) => { t += ms; } }; };

test("first refresh stores stations, current prices and first observations", async () => {
  const { db, sql } = sqliteD1();
  const r = await runRefresh(client(world()), createFuelRepository(db), clock().now);
  assert.equal(r.status, "complete");
  assert.equal(count(sql, "stations"), N);
  assert.equal(count(sql, "current_prices"), N * 2);
  assert.equal(count(sql, "price_changes"), N * 2);
  const state = sql.prepare("SELECT * FROM ingestion_state").get() as Record<string, unknown>;
  assert.equal(state.last_status, "complete");
  assert.equal(state.station_count, N);
  assert.equal(state.lease_until, null);
});

test("an unchanged second poll writes no stations, prices or history", async () => {
  const { db, sql } = sqliteD1();
  const c = clock();
  await runRefresh(client(world()), createFuelRepository(db), c.now);
  c.advance(15 * 60_000);
  const r = await runRefresh(client(world()), createFuelRepository(db), c.now);
  assert.deepEqual(r.status === "complete" && [r.stationWrites, r.priceWrites, r.priceChanges], [0, 0, 0]);
  assert.equal(count(sql, "price_changes"), N * 2);
  const state = sql.prepare("SELECT last_attempt_at, last_success_at FROM ingestion_state").get() as Record<string, string>;
  assert.equal(state.last_success_at, "2026-10-05T12:15:00.000Z"); // Feed health moves; price rows do not.
});

test("a changed price appends history and updates current; older updates are ignored", async () => {
  const { db, sql } = sqliteD1();
  const c = clock();
  await runRefresh(client(world()), createFuelRepository(db), c.now);
  const w = world();
  (w.prices[0] as any).fuel_prices[0] = { fuel_type: "E10", price: 128.9, price_last_updated: iso(20), price_change_effective_timestamp: iso(15) };
  (w.prices[1] as any).fuel_prices[0] = { fuel_type: "E10", price: 99.9, price_last_updated: iso(1), price_change_effective_timestamp: iso(-30) }; // out of order
  c.advance(60_000);
  const r = await runRefresh(client(w), createFuelRepository(db), c.now);
  assert.deepEqual(r.status === "complete" && [r.priceWrites, r.priceChanges], [1, 1]);
  const cur = sql.prepare("SELECT pence_per_litre FROM current_prices WHERE node_id=? AND feed_fuel_type='E10'").get(id(0)) as { pence_per_litre: string };
  assert.equal(cur.pence_per_litre, "128.9");
  const kept = sql.prepare("SELECT pence_per_litre FROM current_prices WHERE node_id=? AND feed_fuel_type='E10'").get(id(1)) as { pence_per_litre: string };
  assert.equal(kept.pence_per_litre, "131");
  assert.equal(count(sql, "price_changes"), N * 2 + 1);
});

test("a failed price batch keeps last good data and records a failed attempt", async () => {
  const { db, sql } = sqliteD1();
  const c = clock();
  await runRefresh(client(world()), createFuelRepository(db), c.now);
  const w = world();
  (w.prices[0] as any).fuel_prices[0].price = 1.5;
  c.advance(60_000);
  const r = await runRefresh(client(w, { failPriceBatch: 2 }), createFuelRepository(db), c.now);
  assert.equal(r.status, "failed");
  const state = sql.prepare("SELECT * FROM ingestion_state").get() as Record<string, string>;
  assert.equal(state.last_status, "failed");
  assert.equal(state.last_success_at, "2026-10-05T12:00:00.000Z");
  assert.equal(count(sql, "stations"), N);
  const p = sql.prepare("SELECT pence_per_litre FROM current_prices WHERE node_id=? AND feed_fuel_type='E10'").get(id(0)) as { pence_per_litre: string };
  assert.equal(p.pence_per_litre, "130");
});

test("a suspiciously small but well-formed refresh is rejected and nothing is deleted", async () => {
  const { db, sql } = sqliteD1();
  const c = clock();
  await runRefresh(client(world()), createFuelRepository(db), c.now);
  c.advance(60_000);
  const r = await runRefresh(client(world(300)), createFuelRepository(db), c.now);
  assert.equal(r.status, "rejected");
  assert.equal(count(sql, "stations"), N);
  assert.equal(count(sql, "current_prices"), N * 2);
});

test("stations missing from a complete run are reported, never deleted; vanished fuel is dropped from current but kept in history", async () => {
  const { db, sql } = sqliteD1();
  const c = clock();
  await runRefresh(client(world()), createFuelRepository(db), c.now);
  const w = world(N - 20);
  (w.prices[0] as any).fuel_prices.pop();
  c.advance(60_000);
  const r = await runRefresh(client(w), createFuelRepository(db), c.now);
  assert.equal(r.status, "complete");
  assert.equal(r.status === "complete" && r.absentStationCandidates, 20);
  assert.equal(count(sql, "stations"), N);
  assert.equal(count(sql, "price_changes"), N * 2);
  assert.equal(sql.prepare("SELECT COUNT(*) AS n FROM current_prices WHERE node_id=?").get(id(0))!.n, 1);
});

test("only one refresh may hold the lease; an expired lease can be taken over", async () => {
  const { db } = sqliteD1();
  const repo = createFuelRepository(db);
  assert.equal(await repo.acquireLease("2026-10-05T12:00:00.000Z", "2026-10-05T12:10:00.000Z"), true);
  assert.equal(await repo.acquireLease("2026-10-05T12:05:00.000Z", "2026-10-05T12:15:00.000Z"), false);
  assert.equal(await repo.acquireLease("2026-10-05T12:11:00.000Z", "2026-10-05T12:21:00.000Z"), true);
  const skipped = await runRefresh(client(world()), repo, () => Date.parse("2026-10-05T12:12:00Z"));
  assert.equal(skipped.status, "skipped");
});

test("nearby returns bounded, distance-ordered stations with prices and separate feed health", async () => {
  const { db } = sqliteD1();
  await runRefresh(client(world()), createFuelRepository(db), clock().now);
  const env = { DB: db };
  const res = await worker.fetch(new Request("https://t.test/v1/stations/nearby?lat=51.45&lon=-0.97&radiusMetres=1000&limit=5"), env);
  assert.equal(res.status, 200);
  const body = (await res.json()) as any;
  assert.equal(body.stations.length, 5);
  assert.deepEqual(body.stations.map((s: any) => s.distanceMetres), [0, 111, 222, 334, 445]);
  assert.equal(body.stations[3].temporaryClosure, true);
  assert.deepEqual(body.stations[0].prices.map((p: any) => p.feedFuelType), ["B7_STANDARD", "E10"]);
  assert.equal(body.feed.lastSuccessfulRefreshAt, "2026-10-05T12:00:00.000Z");
});

test("nearby validates input and does not touch the database for bad requests", async () => {
  const env = { DB: { prepare() { throw new Error("must not be called"); } } as unknown as D1Database };
  for (const q of ["", "?lat=51&lon=-1&radiusMetres=900000", "?lat=0&lon=0", "?lat=51&lon=-1&limit=0", "?lat=abc&lon=-1", "?lat=51&lon=-1&limit=500"]) {
    const res = await worker.fetch(new Request(`https://t.test/v1/stations/nearby${q}`), env);
    assert.equal(res.status, 400, q);
  }
});

test("a database failure on nearby is 503 without leaking the error", async () => {
  const env = { DB: { prepare() { throw new Error("secret connection string"); } } as unknown as D1Database };
  const res = await worker.fetch(new Request("https://t.test/v1/stations/nearby?lat=51.45&lon=-0.97"), env);
  assert.equal(res.status, 503);
  assert.doesNotMatch(await res.text(), /secret/);
});

test("scheduled handler without credentials does nothing", async () => {
  const env = { DB: { prepare() { throw new Error("must not be called"); } } as unknown as D1Database };
  await (worker as any).scheduled({}, env);
});
