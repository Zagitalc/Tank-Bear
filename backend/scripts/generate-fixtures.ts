// Regenerates the synthetic API response fixtures in contracts/fixtures from the real handlers.
// All stations, prices and history are invented; nothing here comes from the live feed.
// Run: npm run fixtures --workspace backend
import { writeFileSync } from "node:fs";
import { URL as NodeURL } from "node:url";
import worker from "../src/index.ts";
import { optimiseResponse } from "../src/api/optimise.ts";
import { createFuelRepository, haversine } from "../src/repositories/fuel.ts";
import { encodePolyline } from "../src/routing/polyline.ts";
import type { RoutingProvider } from "../src/routing/types.ts";
import { sqliteD1 } from "../tests/d1-shim.ts";

const KEY = "fixture-key-0123456789-abcdefgh";
const out = (name: string, body: unknown) =>
  writeFileSync(new NodeURL(`../../contracts/fixtures/${name}`, import.meta.url), JSON.stringify(body, null, 2) + "\n");
const week = (o: string, c: string) => JSON.stringify({ w: Array.from({ length: 7 }, () => ({ o, c, h: 0 })), bh: null, d: "england-and-wales" });

function db(lastSuccess: string) {
  const { db, sql } = sqliteD1();
  sql.exec(`INSERT INTO ingestion_state (feed, last_success_at, last_status) VALUES ('fuel-finder-national', '${lastSuccess}', 'complete')`);
  const station = sql.prepare(`INSERT INTO stations VALUES (?,?,'Example','RG1',?,?,?,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',?,NULL)`);
  const price = sql.prepare(`INSERT INTO current_prices VALUES (?,'E10',?,'2026-10-03T08:00:00.000Z','2026-10-03T08:00:00.000Z','2026-10-03T08:00:00.000Z')`);
  return { db, sql, add: (id: string, name: string, lat: number, lon: number, pence: string | null, hours: string | null, temp = 0) => {
    station.run(id, name, lat, lon, temp, hours);
    if (pence) price.run(id, pence);
  } };
}

async function withClock<T>(iso: string, f: () => Promise<T>): Promise<T> {
  const real = Date.now;
  Date.now = () => Date.parse(iso);
  try { return await f(); } finally { Date.now = real; }
}

// Journey optimise response, with a straight-line test router (never used by the product).
{
  const A = { lat: 51.45, lon: -0.97 };
  const B = { lat: 51.75, lon: -1.25 };
  const on = (t: number, off = 0) => ({ lat: A.lat + t * (B.lat - A.lat) + off, lon: A.lon + t * (B.lon - A.lon) });
  const s = db("2026-10-05T11:50:00.000Z");
  s.add("station-a", "Example Services North", on(0.5).lat, on(0.5).lon, "142.9", week("06:00", "22:00"));
  s.add("station-b", "Example Garage Off Route", on(0.5, 0.018).lat, on(0.5, 0.018).lon, "128.9", null);
  s.add("station-c", "Example Supermarket Fuel", on(0.2, 0.004).lat, on(0.2, 0.004).lon, "131.9", week("06:00", "22:00"));
  const provider: RoutingProvider = {
    contextId: "example|v1",
    async route(stops) {
      let m = 0;
      for (let i = 0; i < stops.length - 1; i++) m += haversine(stops[i]!.lat, stops[i]!.lon, stops[i + 1]!.lat, stops[i + 1]!.lon);
      return { kind: "ok", path: {
        totals: { distanceMetres: String(Math.round(m)), durationSeconds: String(Math.round(m / 18)), contextId: "example|v1", usesFerry: false, usesToll: false },
        geometry: encodePolyline(stops.map((p) => [p.lat, p.lon])), snapMetres: stops.map(() => 3), provider: "example", graphVersion: "v1",
      } };
    },
  };
  const res = await optimiseResponse(new Request("https://t.test/x", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ mode: "along_journey", origin: A, destination: B, fuelType: "E10", vehicle: { mpgImperial: "45" }, litresToBuy: "30" }),
  }), createFuelRepository(s.db), provider, () => Date.parse("2026-10-05T12:00:00Z"));
  out("optimise-response-sample.json", await res.json());
}

// Nearby with fill cost, local median, and open/closed/unknown/no-price cases.
{
  const s = db("2026-10-05T11:50:00.000Z");
  s.add("near-open", "Example Services", 51.456, -0.979, "139.9", week("06:00", "22:00"));
  s.add("near-closed", "Example Garage", 51.458, -0.98, "129.9", week("06:00", "12:00"));
  s.add("near-unknown", "Example Supermarket", 51.46, -0.976, "132.9", null);
  s.add("near-noprice", "Example Forecourt", 51.457, -0.97, null, week("06:00", "22:00"), 1);
  const res = await withClock("2026-10-05T12:00:00Z", () => worker.fetch(new Request(
    "https://t.test/v1/stations/nearby?lat=51.4543&lon=-0.9781&radiusMetres=5000&limit=20&fuelType=E10&litres=30",
    { headers: { "x-tank-bear-key": KEY } }), { DB: s.db, API_KEYS: KEY }));
  out("nearby-response-sample.json", await res.json());
}

// History: a fully watched week, and only one day watched.
for (const [watched, file] of [[8, "history-response-sample.json"], [1, "history-insufficient-sample.json"]] as const) {
  const NOW = Date.parse("2026-10-12T12:00:00Z");
  const day = (n: number) => new Date(NOW - n * 86_400_000).toISOString();
  const ID = "ab".repeat(32);
  const s = db(day(0.01));
  s.add(ID, "Example Services", 51.45, -0.97, null, null);
  s.sql.prepare(`INSERT INTO current_prices VALUES (?,'E10','136.9',?,?,?)`).run(ID, day(1), day(1), day(1));
  const change = s.sql.prepare(`INSERT INTO price_changes (node_id, feed_fuel_type, pence_per_litre, price_last_updated, price_change_effective, observed_at) VALUES (?,'E10',?,?,?,?)`);
  change.run(ID, "140.9", day(10), day(10), day(9));
  change.run(ID, "138.9", day(4), day(4), day(4));
  change.run(ID, "136.9", day(1), day(1), day(1));
  const log = s.sql.prepare(`INSERT INTO refresh_log VALUES (?,8000,7900)`);
  for (let i = 0; i < watched * 96; i++) log.run(new Date(NOW - watched * 86_400_000 + i * 900_000).toISOString());
  const res = await withClock("2026-10-12T12:00:00Z", () => worker.fetch(new Request(
    `https://t.test/v1/stations/${ID}/history?fuelType=E10`, { headers: { "x-tank-bear-key": KEY } }), { DB: s.db, API_KEYS: KEY }));
  out(file, await res.json());
}
console.log("Wrote 4 fixtures to contracts/fixtures.");
