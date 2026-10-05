import type { FuelRepository } from "../repositories/fuel.ts";
import { diffRefresh } from "./diff.ts";
import { collectBatches, type BatchOutcome } from "./pagination.ts";
import { normalisePrices, normaliseStations } from "./records.ts";
import { assessRefresh } from "./refresh.ts";

export interface FeedClient {
  fetchStationBatch(batch: number): Promise<BatchOutcome>;
  fetchPriceBatch(batch: number): Promise<BatchOutcome>;
}

export type RunResult =
  | { status: "complete"; stations: number; pricedStations: number; stationWrites: number; priceWrites: number; priceChanges: number; absentStationCandidates: number }
  | { status: "skipped"; reason: string }
  | { status: "rejected" | "failed"; reasons: string[] };

const LEASE_MS = 10 * 60 * 1000;

/** One nationwide refresh. Anything short of a validated complete run leaves current data alone. */
export async function runRefresh(client: FeedClient, repo: FuelRepository, now: () => number = Date.now): Promise<RunResult> {
  const at = new Date(now()).toISOString();
  if (!(await repo.acquireLease(at, new Date(now() + LEASE_MS).toISOString()))) {
    return { status: "skipped", reason: "another refresh holds the lease" };
  }
  try {
    const stations = await collectBatches((b) => client.fetchStationBatch(b));
    const prices = stations.complete ? await collectBatches((b) => client.fetchPriceBatch(b)) : { complete: false as const, reason: "not fetched after station failure", batches: 0, records: [] };
    const ns = normaliseStations(stations.records);
    const np = normalisePrices(prices.records, now());
    const previous = await repo.previousCounts();
    const decision = assessRefresh({ stations, prices, normalisedStations: ns, normalisedPrices: np, previous });

    if (!decision.complete) {
      const transport = !stations.complete || !prices.complete;
      const status = transport ? "failed" : "rejected";
      await repo.recordFailure({ at, status, reasons: decision.reasons });
      return { status, reasons: decision.reasons };
    }

    const current = await repo.loadCurrent();
    const diff = diffRefresh(current, ns.valid, np.valid);
    const absent = decision.absentStationIds(new Set(current.stations.keys())).length;
    await repo.applyComplete(diff, { at, status: "complete", reasons: [], stationCount: ns.valid.length, pricedStationCount: np.valid.length }, at);
    return {
      status: "complete", stations: ns.valid.length, pricedStations: np.valid.length,
      stationWrites: diff.stationUpserts.length, priceWrites: diff.priceUpserts.length, priceChanges: diff.priceChanges.length,
      absentStationCandidates: absent,
    };
  } catch {
    // Raw errors may carry URLs or credentials; keep only a generic reason.
    const reasons = ["unexpected error during refresh"];
    await repo.recordFailure({ at, status: "failed", reasons }).catch(() => undefined);
    return { status: "failed", reasons };
  } finally {
    await repo.releaseLease().catch(() => undefined);
  }
}
