import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createServer, type Server } from "node:http";
import type { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { URL as NodeURL, fileURLToPath, pathToFileURL } from "node:url";
import worker from "../src/index.ts";
import { clientAddress, createNodeHandler } from "../server/http.ts";
import { applyMigrations, openSqliteD1 } from "../server/sqlite-d1.ts";

const KEY = "server-test-key-0123456789-abc";
const id = (n: number) => n.toString(16).padStart(64, "0");

// --- SQLite as D1 --------------------------------------------------------------

test("migrations apply once, in order, and are skipped on the next start", () => {
  const a = openSqliteD1(":memory:");
  const names = (a.sql.prepare("SELECT name FROM _migrations ORDER BY name").all() as { name: string }[]).map((r) => r.name);
  assert.ok(names.length >= 5 && names[0]!.startsWith("0001"));
  assert.deepEqual(applyMigrations(a.sql), []); // Nothing left to apply.
  a.close();
});

test("a failed migration rolls back and names the file without leaking SQL", () => {
  const dir = mkdtempSync(join(tmpdir(), "mig-"));
  try {
    const { sql } = openSqliteD1(":memory:", { migrate: false });
    const fs = require_fs();
    fs.writeFileSync(join(dir, "0001_ok.sql"), "CREATE TABLE a (x INTEGER);");
    fs.writeFileSync(join(dir, "0002_bad.sql"), "CREATE TABLE b (x INTEGER); THIS IS NOT SQL;");
    assert.throws(() => applyMigrations(sql, new NodeURL(`file://${dir}/`)), /0002_bad\.sql/);
    assert.equal((sql.prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name='b'").get() as { n: number }).n, 0);
    assert.equal((sql.prepare("SELECT COUNT(*) AS n FROM _migrations").get() as { n: number }).n, 1);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
function require_fs() { return process.getBuiltinModule("node:fs"); }

test("batch is one transaction: a failing statement rolls the rest back", async () => {
  const { db, sql } = openSqliteD1(":memory:");
  sql.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT NOT NULL)");
  await assert.rejects(db.batch([
    db.prepare("INSERT INTO t (id, v) VALUES (1, 'a')"),
    db.prepare("INSERT INTO t (id, v) VALUES (2, NULL)"),
  ]));
  assert.equal((sql.prepare("SELECT COUNT(*) AS n FROM t").get() as { n: number }).n, 0);
  const ok = await db.batch([db.prepare("INSERT INTO t (id, v) VALUES (?, ?)").bind(1, "a"), db.prepare("INSERT INTO t (id, v) VALUES (?, ?)").bind(2, "b")]);
  assert.deepEqual(ok.map((r) => r.meta.changes), [1, 1]);
});

test("first, all and run behave like D1", async () => {
  const { db, sql } = openSqliteD1(":memory:");
  sql.exec("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)");
  const r = await db.prepare("INSERT INTO t (v) VALUES (?)").bind("x").run();
  assert.equal(r.meta.changes, 1);
  assert.deepEqual(await db.prepare("SELECT v FROM t").first(), { v: "x" });
  assert.equal(await db.prepare("SELECT v FROM t").first("v"), "x");
  assert.equal(await db.prepare("SELECT v FROM t WHERE id = 99").first(), null);
  assert.deepEqual((await db.prepare("SELECT v FROM t").all()).results, [{ v: "x" }]);
});

test("a file database persists across opens and uses WAL", () => {
  const dir = mkdtempSync(join(tmpdir(), "db-"));
  try {
    const path = join(dir, "sub", "t.sqlite");
    const a = openSqliteD1(path);
    a.sql.prepare("INSERT INTO ingestion_state (feed) VALUES ('x')").run();
    assert.equal((a.sql.prepare("PRAGMA journal_mode").get() as { journal_mode: string }).journal_mode, "wal");
    a.close();
    const b = openSqliteD1(path);
    assert.equal((b.sql.prepare("SELECT COUNT(*) AS n FROM ingestion_state").get() as { n: number }).n, 1);
    b.close();
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("concurrent processes apply each migration only once", async () => {
  const dir = mkdtempSync(join(tmpdir(), "concurrent-migrations-"));
  const children: ReturnType<typeof spawn>[] = [];
  try {
    const path = join(dir, "t.sqlite");
    const initial = openSqliteD1(path, { migrate: false });
    applyMigrations(initial.sql, pathToFileURL(`${dir}/`));
    initial.close();
    writeFileSync(join(dir, "0001.sql"), `CREATE TABLE startup (id INTEGER PRIMARY KEY);
      WITH RECURSIVE numbers(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM numbers WHERE n<100000)
      INSERT INTO startup SELECT n FROM numbers;`);
    writeFileSync(join(dir, "0002.sql"), "ALTER TABLE startup ADD COLUMN value TEXT;");
    const script = `import { openSqliteD1, applyMigrations } from ${JSON.stringify(new NodeURL("../server/sqlite-d1.ts", import.meta.url).href)};
      const db = openSqliteD1(${JSON.stringify(path)}, { migrate: false });
      process.stdin.once('data', () => {
        applyMigrations(db.sql, new URL(${JSON.stringify(pathToFileURL(`${dir}/`).href)}));
        db.close(); process.exit(0);
      });
      console.log('ready');`;
    const runs = Array.from({ length: 4 }, () => {
      const child = spawn(process.execPath, ["--input-type=module", "-e", script], { stdio: ["pipe", "pipe", "pipe"] });
      children.push(child);
      let errors = "";
      child.stderr.on("data", (chunk) => { errors += chunk; });
      return {
        ready: new Promise<void>((resolve, reject) => {
          child.stdout.once("data", () => resolve());
          child.once("error", reject);
          child.once("exit", () => reject(new Error("child exited before migration start")));
        }),
        done: new Promise<void>((resolve, reject) => {
          child.once("error", reject);
          child.once("exit", (code) => code === 0 ? resolve() : reject(new Error(errors)));
        }),
      };
    });
    const completion = Promise.all(runs.map((run) => run.done));
    await Promise.all(runs.map((run) => run.ready));
    for (const child of children) child.stdin!.end("start");
    await completion;
    const result = openSqliteD1(path, { migrate: false });
    assert.equal(result.sql.prepare("SELECT COUNT(*) AS n FROM _migrations").get()!.n, 2);
    assert.equal(result.sql.prepare("SELECT COUNT(*) AS n FROM startup").get()!.n, 100000);
    result.close();
  } finally {
    for (const child of children) if (child.exitCode === null) child.kill();
    rmSync(dir, { recursive: true, force: true });
  }
});

// --- HTTP wrapper --------------------------------------------------------------

async function serve(trustProxy: boolean) {
  const { db, sql } = openSqliteD1(":memory:");
  const lines: string[] = [];
  const server: Server = createServer(createNodeHandler(worker, { DB: db, API_KEYS: KEY }, { trustProxy, maxBodyBytes: 2048, log: (l) => lines.push(l) }));
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return { base, sql, lines, close: () => new Promise<void>((r) => server.close(() => r())) };
}

test("the Node server serves the same handlers: health, status, auth and validation", async () => {
  const s = await serve(false);
  try {
    assert.equal((await fetch(`${s.base}/health`)).status, 200);
    const st = (await (await fetch(`${s.base}/status`)).json()) as any;
    assert.deepEqual([st.fuelData, st.status], ["never_loaded", "degraded"]);
    assert.equal((await fetch(`${s.base}/v1/stations/nearby?lat=51.45&lon=-0.97`)).status, 401);
    const bad = await fetch(`${s.base}/v1/stations/nearby?lat=0&lon=0`, { headers: { "x-tank-bear-key": KEY } });
    assert.equal(bad.status, 400);
    const head = await fetch(`${s.base}/health`, { method: "HEAD" });
    assert.equal([head.status, await head.text()].join("|"), "200|");
    const post = await fetch(`${s.base}/v1/journeys/optimise`, { method: "POST", headers: { "x-tank-bear-key": KEY, "content-type": "application/json" }, body: "{}" });
    assert.equal(post.status, 503); // Valid key and body shape reached routing config: not configured.
    assert.equal(post.headers.get("x-content-type-options"), "nosniff");
  } finally { await s.close(); }
});

test("request logs carry method, path, status and time but never the query string or key", async () => {
  const s = await serve(false);
  try {
    await fetch(`${s.base}/v1/stations/nearby?lat=51.4543&lon=-0.9781`, { headers: { "x-tank-bear-key": KEY } });
    const text = s.lines.join("\n");
    assert.match(text, /GET \/v1\/stations\/nearby \d{3} \d+ms/);
    assert.doesNotMatch(text, /51\.4543|0\.9781|server-test-key/);
  } finally { await s.close(); }
});

test("request logs redact arbitrary paths and station identifiers", async () => {
  const s = await serve(false);
  try {
    await fetch(`${s.base}/private-location-51.4543/${KEY}`);
    await fetch(`${s.base}/v1/stations/private-station/history`);
    const text = s.lines.join("\n");
    assert.match(text, /GET \[unmatched\] 404/);
    assert.match(text, /GET \/v1\/stations\/:id\/history/);
    assert.doesNotMatch(text, /51\.4543|server-test-key|private-station/);
  } finally { await s.close(); }
});

test("oversized bodies are refused before reaching the app", async () => {
  const s = await serve(false);
  try {
    const res = await fetch(`${s.base}/v1/journeys/optimise`, { method: "POST", headers: { "x-tank-bear-key": KEY, "content-type": "application/json" }, body: "x".repeat(5000) });
    assert.equal(res.status, 413);
  } finally { await s.close(); }
});

test("a client cannot choose its own rate-limit identity", () => {
  const sock = (addr: string) => ({ remoteAddress: addr }) as never;
  const req = (addr: string, headers: Record<string, string>) => ({ socket: sock(addr), headers }) as never;
  // Without a trusted proxy the socket address is used and headers are ignored.
  assert.equal(clientAddress(req("203.0.113.5", { "x-real-ip": "1.2.3.4" }), false), "203.0.113.5");
  // Behind a local proxy, X-Real-IP is used only when the connection really came from loopback.
  assert.equal(clientAddress(req("127.0.0.1", { "x-real-ip": "198.51.100.7" }), true), "198.51.100.7");
  assert.equal(clientAddress(req("::ffff:127.0.0.1", { "x-real-ip": "198.51.100.7" }), true), "198.51.100.7");
  assert.equal(clientAddress(req("203.0.113.5", { "x-real-ip": "1.2.3.4" }), true), "203.0.113.5");
  assert.equal(clientAddress(req("127.0.0.1", {}), true), "127.0.0.1");
});

test("a forged cf-connecting-ip header does not change which bucket a caller lands in", async () => {
  const s = await serve(false);
  try {
    // Burn the per-minute allowance while claiming a different address each time.
    let last = 0;
    for (let i = 0; i < 25; i++) {
      const r = await fetch(`${s.base}/v1/stations/nearby?lat=51.45&lon=-0.97`, { headers: { "x-tank-bear-key": KEY, "cf-connecting-ip": `9.9.9.${i}` } });
      last = r.status;
    }
    assert.equal(last, 429);
  } finally { await s.close(); }
});

// --- Refresh job -------------------------------------------------------------

function fakeFuelFinder(opts: { fail?: boolean } = {}): Promise<{ url: string; close: () => Promise<void> }> {
  const stations = Array.from({ length: 1200 }, (_, i) => ({
    node_id: id(i), trading_name: `S${i}`, location: { latitude: 51.4 + i * 0.0001, longitude: -1, postcode: "RG1 1AA" },
  }));
  const prices = stations.map((s) => ({ node_id: s.node_id, trading_name: s.trading_name, fuel_prices: [
    { fuel_type: "E10", price: 139.9, price_last_updated: "2026-10-05T10:00:00.000Z", price_change_effective_timestamp: "2026-10-05T09:00:00.000Z" },
  ] }));
  const server = createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://x");
    res.setHeader("content-type", "application/json");
    if (req.method === "POST" && url.pathname === "/api/v1/oauth/generate_access_token") {
      if (opts.fail) { res.statusCode = 403; res.end("{}"); return; }
      res.end(JSON.stringify({ data: { access_token: "t", expires_in: 3600 } })); return;
    }
    const all = url.pathname === "/api/v1/pfs" ? stations : url.pathname === "/api/v1/pfs/fuel-prices" ? prices : null;
    const b = Number(url.searchParams.get("batch-number"));
    if (!all) { res.statusCode = 404; res.end("{}"); return; }
    const start = (b - 1) * 500;
    if (start >= all.length) { res.statusCode = 404; res.end(JSON.stringify({ data: { message: `Requested batch ${b} is not available` } })); return; }
    res.end(JSON.stringify(all.slice(start, start + 500)));
  });
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve({
    url: `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    close: () => new Promise<void>((r) => server.close(() => r())),
  })));
}

function runRefresh(env: Record<string, string>): Promise<{ code: number | null; out: string }> {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [fileURLToPath(new NodeURL("../server/refresh.ts", import.meta.url))], {
      env: { PATH: process.env.PATH ?? "", ...env }, stdio: ["ignore", "pipe", "pipe"],
    });
    let out = "";
    child.stdout.on("data", (d) => (out += d));
    child.stderr.on("data", (d) => (out += d));
    child.on("close", (code) => resolve({ code, out }));
  });
}

test("the refresh job loads the feed into the database file and exits 0; a second run is a no-op", async () => {
  const dir = mkdtempSync(join(tmpdir(), "refresh-"));
  const fake = await fakeFuelFinder();
  try {
    const env = { DATABASE_PATH: join(dir, "t.sqlite"), FUEL_FINDER_CLIENT_ID: "id", FUEL_FINDER_CLIENT_SECRET: "secret-value", FUEL_FINDER_BASE_URL: fake.url };
    const first = await runRefresh(env);
    assert.equal(first.code, 0, first.out);
    assert.match(first.out, /"status":"complete"/);
    assert.doesNotMatch(first.out, /secret-value/);
    const db = openSqliteD1(env.DATABASE_PATH);
    assert.equal((db.sql.prepare("SELECT COUNT(*) AS n FROM stations").get() as { n: number }).n, 1200);
    assert.equal((db.sql.prepare("SELECT COUNT(*) AS n FROM current_prices").get() as { n: number }).n, 1200);
    db.close();
    const second = await runRefresh(env);
    assert.equal(second.code, 0, second.out);
    assert.match(second.out, /"stationWrites":0/);
  } finally { await fake.close(); rmSync(dir, { recursive: true, force: true }); }
});

test("a refused or failing feed exits 1 with last good data kept, and missing credentials exit 2", async () => {
  const dir = mkdtempSync(join(tmpdir(), "refresh-"));
  const good = await fakeFuelFinder();
  const bad = await fakeFuelFinder({ fail: true });
  try {
    const path = join(dir, "t.sqlite");
    const base = { DATABASE_PATH: path, FUEL_FINDER_CLIENT_ID: "id", FUEL_FINDER_CLIENT_SECRET: "s" };
    assert.equal((await runRefresh({ ...base, FUEL_FINDER_BASE_URL: good.url })).code, 0);
    const failed = await runRefresh({ ...base, FUEL_FINDER_BASE_URL: bad.url });
    assert.equal(failed.code, 1, failed.out);
    assert.match(failed.out, /"status":"failed"/);
    const db = openSqliteD1(path);
    assert.equal((db.sql.prepare("SELECT COUNT(*) AS n FROM stations").get() as { n: number }).n, 1200);
    assert.equal((db.sql.prepare("SELECT last_status FROM ingestion_state").get() as { last_status: string }).last_status, "failed");
    db.close();
    assert.equal((await runRefresh({ DATABASE_PATH: path })).code, 2);
  } finally { await good.close(); await bad.close(); rmSync(dir, { recursive: true, force: true }); }
});

// --- Whole hosted path with a fake routing engine ----------------------------------

test("the Node server answers a journey search end to end: database, ranking and the Valhalla adapter", async () => {
  const { encodePolyline } = await import("../src/routing/polyline.ts");
  const { haversine } = await import("../src/repositories/fuel.ts");
  // A stand-in for Valhalla's /route that speaks its response format.
  const valhalla = createServer((req, res) => {
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      const stops = (JSON.parse(raw).locations as { lat: number; lon: number }[]);
      const legs = stops.slice(0, -1).map((from, i) => {
        const to = stops[i + 1]!;
        return { shape: encodePolyline([[from.lat, from.lon], [(from.lat + to.lat) / 2, (from.lon + to.lon) / 2], [to.lat, to.lon]]) };
      });
      let metres = 0;
      for (let i = 0; i < stops.length - 1; i++) metres += haversine(stops[i]!.lat, stops[i]!.lon, stops[i + 1]!.lat, stops[i + 1]!.lon);
      res.setHeader("content-type", "application/json");
      res.end(JSON.stringify({ trip: { status: 0, units: "kilometers", legs, summary: { length: metres / 1000, time: metres / 18, has_toll: false, has_ferry: false } } }));
    });
  });
  await new Promise<void>((r) => valhalla.listen(0, "127.0.0.1", r));
  const routingUrl = `http://127.0.0.1:${(valhalla.address() as AddressInfo).port}`;

  const { db, sql } = openSqliteD1(":memory:");
  sql.exec(`INSERT INTO ingestion_state (feed, last_success_at, last_status) VALUES ('fuel-finder-national', '${new Date().toISOString()}', 'complete')`);
  const hours = JSON.stringify({ w: Array.from({ length: 7 }, () => ({ o: "00:00", c: "23:59", h: 1 })), bh: null, d: "england-and-wales" });
  const add = (n: number, lat: number, lon: number, pence: string) => {
    sql.prepare(`INSERT INTO stations VALUES (?,?,?,?,?,?,0,0,0,0,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z',?,NULL)`).run(id(n), `Station ${n}`, "B", "RG1", lat, lon, hours);
    sql.prepare(`INSERT INTO current_prices VALUES (?,?,?,?,?,?)`).run(id(n), "E10", pence, "2026-10-04T08:00:00.000Z", "2026-10-04T08:00:00.000Z", "2026-10-04T08:00:00.000Z");
  };
  add(1, 51.60, -1.11, "141.9");
  add(2, 51.605, -1.115, "129.9");
  const env = { DB: db, API_KEYS: KEY, ROUTING_BASE_URL: routingUrl, ROUTING_GRAPH_VERSION: "fake-1" };
  const server = createServer(createNodeHandler(worker, env, { trustProxy: false, maxBodyBytes: 4096 }));
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  try {
    const res = await fetch(`http://127.0.0.1:${(server.address() as AddressInfo).port}/v1/journeys/optimise`, {
      method: "POST", headers: { "x-tank-bear-key": KEY, "content-type": "application/json" },
      body: JSON.stringify({ mode: "along_journey", origin: { lat: 51.45, lon: -0.97 }, destination: { lat: 51.75, lon: -1.25 }, fuelType: "E10", vehicle: { mpgImperial: "45" }, litresToBuy: "30" }),
    });
    const body = (await res.json()) as any;
    assert.equal(res.status, 200, JSON.stringify(body));
    assert.equal(body.status, "ranked");
    assert.equal(body.feed.attribution, "Fuel Finder/ICS");
    assert.equal(body.candidates.length, 2);
    assert.equal(body.labels.cheapestPumpStationId, id(2));
    assert.equal(body.routing.graphVersion, "fake-1");
  } finally {
    await new Promise<void>((r) => server.close(() => r()));
    await new Promise<void>((r) => valhalla.close(() => r()));
  }
});
