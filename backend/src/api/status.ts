import type { FuelRepository } from "../repositories/fuel.ts";
import { FEED_MAX_AGE_SECONDS } from "../optimise/service.ts";

/**
 * GET /status: open, cheap and free of station data, for an uptime monitor. Fuel data is "ok" only when the last
 * complete refresh is within the same window the journey API requires.
 */
export async function statusResponse(repo: FuelRepository, now: () => number = Date.now): Promise<Response> {
  const headers = { "Cache-Control": "no-store" };
  try {
    const feed = await repo.feedStatus();
    const last = feed.lastSuccessAt;
    const ageSeconds = last ? Math.max(0, Math.round((now() - Date.parse(last)) / 1000)) : null;
    const fuelData = last === null ? "never_loaded" : ageSeconds! > FEED_MAX_AGE_SECONDS ? "stale" : "ok";
    return Response.json({
      service: "tank-bear",
      status: fuelData === "ok" ? "ok" : "degraded",
      fuelData,
      lastSuccessfulRefreshAt: last,
      lastAttemptAt: feed.lastAttemptAt,
      lastAttemptStatus: feed.lastStatus,
      ageSeconds,
    }, { headers });
  } catch {
    return Response.json({ service: "tank-bear", status: "degraded", fuelData: "unknown" }, { status: 503, headers });
  }
}
