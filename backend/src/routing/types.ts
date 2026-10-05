import type { Coordinates, RouteTotals } from "../domain/types.ts";

/** One routed path through ordered stops, with everything needed to audit it. */
export interface RoutedPath {
  totals: RouteTotals;
  /** Encoded polyline with 6-digit precision, all legs joined. */
  geometry: string;
  /** Metres between each requested stop and where the engine placed it on the road network. */
  snapMetres: number[];
  provider: string;
  graphVersion: string;
}

export type RouteOutcome =
  | { kind: "ok"; path: RoutedPath }
  /** The engine answered: these stops cannot be connected. Never a zero-cost detour. */
  | { kind: "no_route" }
  /** Timeout, throttling, 5xx or a response that did not match the documented shape. */
  | { kind: "error"; reason: string };

/** Port for any engine (Valhalla first). Stops are routed in order, each as a real stop. */
export interface RoutingProvider {
  readonly contextId: string;
  route(stops: readonly Coordinates[], signal?: AbortSignal): Promise<RouteOutcome>;
}
