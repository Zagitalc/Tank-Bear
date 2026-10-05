import type { Coordinates, RouteTotals } from "../domain/types.ts";
import type { RoutedPath, RoutingProvider } from "./types.ts";

export const MAX_CANDIDATES = 12;
export const CONCURRENCY = 3;
/** Provisional: beyond this the engine likely snapped to a different road than the forecourt. */
export const MAX_SNAP_METRES = 150;

export interface StationStop {
  stationId: string;
  coordinates: Coordinates;
}

export type UnroutedReason =
  | "no_route"
  | "snap_too_far"
  | "routing_error"
  | "not_attempted_over_cap"
  | "not_attempted_deadline";

export interface RoutedStation {
  stationId: string;
  route: RouteTotals;
  geometry: string;
  snapMetres: number;
}

export type JourneyRouting =
  | { status: "baseline_failed"; reason: "no_route" | "routing_error"; routingCalls: number }
  | {
      status: "ok" | "partial";
      mode: "along_journey" | "fuel_trip";
      /** Null in fuel_trip mode, where the baseline is zero travel. */
      baseline: { route: RouteTotals; geometry: string } | null;
      routed: RoutedStation[];
      unrouted: { stationId: string; reason: UnroutedReason }[];
      routingCalls: number;
      provider: string;
      graphVersion: string;
    };

export interface JourneyOptions {
  mode: "along_journey" | "fuel_trip";
  origin: Coordinates;
  destination?: Coordinates;
  /**
   * Already shortlisted, best first: only the first MAX_CANDIDATES are routed. May be a function
   * so the shortlist can depend on the baseline route (called once, after the baseline succeeds).
   */
  stations: readonly StationStop[] | ((baseline: { route: RouteTotals; geometry: string } | null) => Promise<readonly StationStop[]>);
  maxCandidates?: number;
  /** Absolute epoch ms after which no new routes are started. */
  deadline?: number;
  now?: () => number;
}

/**
 * Routes the baseline and each shortlisted station with the same provider and context.
 * Along journey: A->B baseline, A->station->B candidates. Fuel trip: A->station->A, no baseline.
 * Failed stations are reported, never costed as zero detours, and no straight-line estimate is used.
 */
export async function routeJourney(provider: RoutingProvider, options: JourneyOptions): Promise<JourneyRouting> {
  const { mode, origin } = options;
  const now = options.now ?? Date.now;
  const maybeDestination = mode === "along_journey" ? options.destination : origin;
  if (!maybeDestination) throw new Error("along_journey requires a destination");
  const destination: Coordinates = maybeDestination;
  const cap = Math.min(options.maxCandidates ?? MAX_CANDIDATES, MAX_CANDIDATES);

  let calls = 0;
  let baseline: { route: RouteTotals; geometry: string } | null = null;
  let path0: RoutedPath | null = null;
  if (mode === "along_journey") {
    calls++;
    const outcome = await provider.route([origin, destination]);
    if (outcome.kind !== "ok") return { status: "baseline_failed", reason: outcome.kind === "no_route" ? "no_route" : "routing_error", routingCalls: calls };
    path0 = outcome.path;
    baseline = { route: outcome.path.totals, geometry: outcome.path.geometry };
  }

  const stations = typeof options.stations === "function" ? await options.stations(baseline) : options.stations;
  const attempt = stations.slice(0, cap);
  const unrouted: { stationId: string; reason: UnroutedReason }[] =
    stations.slice(cap).map((s) => ({ stationId: s.stationId, reason: "not_attempted_over_cap" }));
  const results = new Array<RoutedStation | { stationId: string; reason: UnroutedReason }>(attempt.length);
  let graph = path0?.graphVersion ?? "";
  let providerName = path0?.provider ?? "";
  let cursor = 0;

  async function worker() {
    while (cursor < attempt.length) {
      const i = cursor++;
      const s = attempt[i]!;
      if (options.deadline !== undefined && now() >= options.deadline) {
        results[i] = { stationId: s.stationId, reason: "not_attempted_deadline" };
        continue;
      }
      calls++;
      const outcome = await provider.route([origin, s.coordinates, destination]);
      if (outcome.kind === "no_route") results[i] = { stationId: s.stationId, reason: "no_route" };
      else if (outcome.kind === "error") results[i] = { stationId: s.stationId, reason: "routing_error" };
      else {
        const stationSnap = outcome.path.snapMetres[1] ?? Number.POSITIVE_INFINITY;
        const worst = Math.max(...outcome.path.snapMetres);
        if (stationSnap > MAX_SNAP_METRES || worst > MAX_SNAP_METRES * 2) {
          results[i] = { stationId: s.stationId, reason: "snap_too_far" };
        } else if (outcome.path.totals.contextId !== provider.contextId) {
          results[i] = { stationId: s.stationId, reason: "routing_error" };
        } else {
          graph = outcome.path.graphVersion;
          providerName = outcome.path.provider;
          results[i] = { stationId: s.stationId, route: outcome.path.totals, geometry: outcome.path.geometry, snapMetres: stationSnap };
        }
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, attempt.length) }, worker));

  const routed: RoutedStation[] = [];
  for (const r of results) {
    if ("route" in r) routed.push(r);
    else unrouted.push(r);
  }
  return {
    status: unrouted.length === 0 ? "ok" : "partial",
    mode, baseline, routed, unrouted, routingCalls: calls, provider: providerName, graphVersion: graph,
  };
}
