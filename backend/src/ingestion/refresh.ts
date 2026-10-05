import type { PaginationResult } from "./pagination.ts";
import type { Normalised, Station, StationPrices } from "./records.ts";

/** Counts from the last refresh that was accepted as complete. */
export interface PreviousRefresh {
  stationCount: number;
  priceStationCount: number;
}

/** Provisional thresholds; observed national feed was 8126 stations / 8122 priced. */
export const MIN_FIRST_REFRESH_STATIONS = 1000;
export const MIN_RATIO_OF_PREVIOUS = 0.9;
/** Stricter bar when completion rests on the unobserved end-after-full-batch signal. */
export const MIN_RATIO_UNCONFIRMED_END = 0.98;
export const MAX_QUARANTINE_RATIO = 0.05;

export interface RefreshInput {
  stations: PaginationResult;
  prices: PaginationResult;
  normalisedStations: Normalised<Station>;
  normalisedPrices: Normalised<StationPrices>;
  previous: PreviousRefresh | null;
}

export type RefreshDecision =
  | {
      /** Safe to replace current data and treat the run as a completed nationwide refresh. */
      complete: true;
      /** Known stations missing from this feed. Candidates only: never deleted automatically. */
      absentStationIds: (previous: ReadonlySet<string>) => string[];
      priceOrphanCount: number;
    }
  | {
      /** Keep last good data. No deletions, no cursor advance, record the reasons in health. */
      complete: false;
      reasons: string[];
    };

function belowFloor(count: number, previous: number | undefined, ratio: number, firstFloor: number): boolean {
  return previous === undefined ? count < firstFloor : count < previous * ratio;
}

export function assessRefresh(input: RefreshInput): RefreshDecision {
  const { stations, prices, normalisedStations, normalisedPrices, previous } = input;
  const reasons: string[] = [];

  if (!stations.complete) reasons.push(`stations: ${stations.reason}`);
  if (!prices.complete) reasons.push(`prices: ${prices.reason}`);

  for (const [name, feed, norm] of [
    ["stations", stations, normalisedStations],
    ["prices", prices, normalisedPrices],
  ] as const) {
    if (norm.duplicateNodeIds.length > 0) reasons.push(`${name}: duplicate node_id across batches`);
    const total = feed.records.length;
    if (total > 0 && norm.quarantined.filter((q) => q.index >= 0).length / total > MAX_QUARANTINE_RATIO) {
      reasons.push(`${name}: too many malformed records`);
    }
  }

  const unconfirmed = [stations, prices].some((f) => f.complete && f.end === "end-after-full-batch");
  const ratio = unconfirmed ? MIN_RATIO_UNCONFIRMED_END : MIN_RATIO_OF_PREVIOUS;
  if (belowFloor(normalisedStations.valid.length, previous?.stationCount, ratio, MIN_FIRST_REFRESH_STATIONS)) {
    reasons.push("stations: total implausibly low");
  }
  if (belowFloor(normalisedPrices.valid.length, previous?.priceStationCount, ratio, MIN_FIRST_REFRESH_STATIONS)) {
    reasons.push("prices: total implausibly low");
  }

  if (reasons.length > 0) return { complete: false, reasons };

  const stationIds = new Set(normalisedStations.valid.map((s) => s.nodeId));
  return {
    complete: true,
    priceOrphanCount: normalisedPrices.valid.filter((p) => !stationIds.has(p.nodeId)).length,
    absentStationIds: (prev) => [...prev].filter((id) => !stationIds.has(id)),
  };
}
