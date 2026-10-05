import { assessCoverage, buildHistory } from "../history/series.ts";
import type { FuelRepository } from "../repositories/fuel.ts";

const headers = { "Cache-Control": "no-store" };
const err = (status: number, code: string, message: string) => Response.json({ error: { code, message } }, { status, headers });
const FEED_FUEL = { E10: "E10", B7: "B7_STANDARD" } as const;

export const DEFAULT_DAYS = 7;
export const MAX_DAYS = 30;

/**
 * GET /v1/stations/{id}/history?fuelType=E10|B7&days=7. Recorded price changes for one station and grade,
 * with an honest account of how long and how completely Tank Bear has been watching. A trend is returned
 * only when the whole window was observed; otherwise `trend` is null and `coverage.reason` says why.
 */
export async function historyResponse(url: URL, id: string, repo: FuelRepository, now: () => number = Date.now): Promise<Response> {
  if (!/^[0-9a-f]{64}$/.test(id)) return err(400, "INVALID_REQUEST", "Unknown station id format.");
  const fuel = url.searchParams.get("fuelType");
  if (fuel !== "E10" && fuel !== "B7") return err(400, "INVALID_REQUEST", "fuelType must be E10 or B7.");
  const daysParam = url.searchParams.get("days");
  const days = daysParam === null ? DEFAULT_DAYS : /^\d{1,2}$/.test(daysParam) ? Number(daysParam) : NaN;
  if (!Number.isInteger(days) || days < 1 || days > MAX_DAYS) return err(400, "INVALID_REQUEST", `days must be 1 to ${MAX_DAYS}.`);
  try {
    const station = await repo.station(id);
    if (!station) return err(404, "NOT_FOUND", "No such station.");
    const at = new Date(now());
    const from = new Date(at.getTime() - days * 86_400_000);
    const feedFuel = FEED_FUEL[fuel];
    const [coverage, changes, current] = await Promise.all([
      repo.refreshCoverage(from.toISOString()),
      repo.priceChanges(id, feedFuel, from.toISOString()),
      repo.currentPrice(id, feedFuel),
    ]);
    const result = buildHistory(changes, from, at, assessCoverage(days, at, coverage.first, coverage.observedSince), current?.pencePerLitre ?? null);
    return Response.json({
      station: { id, name: station.tradingName },
      fuelType: fuel,
      window: { days, from: from.toISOString(), to: at.toISOString() },
      current,
      ...result,
      note: "Each point is the price from the provider's own change time until the next point. Changes made between refreshes are dated by the provider.",
    }, { headers });
  } catch {
    return err(503, "UNAVAILABLE", "History is temporarily unavailable.");
  }
}
