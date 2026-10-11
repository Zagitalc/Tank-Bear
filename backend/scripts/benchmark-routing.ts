// Operational benchmark using public town centres, never a user's saved journey.
// Run as tankbear on the VM after the graph is built, before enabling it in the API:
// DATABASE_PATH=/var/lib/tankbear/tankbear.sqlite node benchmark-routing.ts <graph-version>
import { openSqliteD1 } from "../server/sqlite-d1.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { optimiseJourney } from "../src/optimise/service.ts";
import { createValhallaProvider } from "../src/routing/valhalla.ts";

const graphVersion = process.argv[2];
if (!graphVersion || !process.env.DATABASE_PATH) throw new Error("Graph version and DATABASE_PATH required");
const { db, close } = openSqliteD1(process.env.DATABASE_PATH, { migrate: false });
const provider = createValhallaProvider({ baseUrl: "http://127.0.0.1:8002", graphVersion, fetch });
// No application route cache: every repetition measures real engine calls.
const cases = [
  { name: "Reading–Oxford", origin: { lat: 51.4543, lon: -0.9781 }, destination: { lat: 51.752, lon: -1.2577 } },
  { name: "Manchester–Leeds", origin: { lat: 53.4808, lon: -2.2426 }, destination: { lat: 53.8008, lon: -1.5491 } },
  { name: "Cardiff–Bristol", origin: { lat: 51.4816, lon: -3.1791 }, destination: { lat: 51.4545, lon: -2.5879 } },
  { name: "Glasgow–Edinburgh", origin: { lat: 55.8642, lon: -4.2518 }, destination: { lat: 55.9533, lon: -3.1883 } },
];
try {
  for (const c of cases) {
    for (let run = 1; run <= 3; run++) {
      const started = performance.now();
      const result = await optimiseJourney({ repo: createFuelRepository(db), provider, now: Date.now }, {
        mode: "along_journey", origin: c.origin, destination: c.destination,
        fuelType: "E10", mpgImperial: "45", litresToBuy: "30", limits: {},
      });
      const body = result.body as Record<string, unknown>;
      console.log(JSON.stringify({ name: c.name, run, elapsedMs: Math.round(performance.now() - started),
        status: result.status, resultStatus: body.status, coverage: body.coverage,
        baseline: (body.baseline as { route?: unknown } | null)?.route,
        candidates: Array.isArray(body.candidates) ? body.candidates.length : 0,
        error: body.error }));
      if (result.status !== 200) process.exitCode = 1;
    }
  }
} finally { close(); }
