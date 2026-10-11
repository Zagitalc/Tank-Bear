// One Fuel Finder refresh from this machine. Run by a systemd timer every 15 minutes.
// Exit code 0: refreshed, or another run holds the lease. Exit code 1: failed or rejected (last good data kept).
import { createFuelFinderClient } from "../src/ingestion/client.ts";
import { runRefresh } from "../src/ingestion/run.ts";
import { createFuelRepository } from "../src/repositories/fuel.ts";
import { createRateLimiter } from "../src/repositories/ratelimit.ts";
import { openSqliteD1 } from "./sqlite-d1.ts";

const e = process.env;
if (!e.FUEL_FINDER_CLIENT_ID || !e.FUEL_FINDER_CLIENT_SECRET) {
  console.error("FUEL_FINDER_CLIENT_ID and FUEL_FINDER_CLIENT_SECRET must be set.");
  process.exit(2);
}

const { db, close } = openSqliteD1(e.DATABASE_PATH ?? "./tankbear.sqlite");
const repo = createFuelRepository(db);
const client = createFuelFinderClient(
  { clientId: e.FUEL_FINDER_CLIENT_ID, clientSecret: e.FUEL_FINDER_CLIENT_SECRET },
  {
    fetch: (input, init) => fetch(input, init), sleep: (ms) => new Promise((r) => setTimeout(r, ms)), now: Date.now,
    // Test hook only: points the client at a fake server. Unset in production.
    ...(e.FUEL_FINDER_BASE_URL ? { baseUrl: e.FUEL_FINDER_BASE_URL, minGapMs: 0 } : {}),
  },
);

const result = await runRefresh(client, repo);
// Counts and safe reasons only; never feed bodies, tokens or credentials.
console.log(JSON.stringify({ fuelFinderRefresh: result }));

// Housekeeping that must never fail the refresh.
await createRateLimiter(db).purgeBefore(Math.floor(Date.now() / 1000) - 2 * 86_400).catch(() => undefined);
await repo.purgeRefreshLog(new Date(Date.now() - 35 * 86_400_000).toISOString()).catch(() => undefined);
close();
process.exit(result.status === "complete" || result.status === "skipped" ? 0 : 1);
