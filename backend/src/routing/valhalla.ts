import type { Coordinates } from "../domain/types.ts";
import { haversine } from "../repositories/fuel.ts";
import { decodePolyline, encodePolyline } from "./polyline.ts";
import type { RouteOutcome, RoutingProvider } from "./types.ts";

export interface ValhallaConfig {
  baseUrl: string;
  /** Identifies the loaded road graph (for example its build date); part of the route context. */
  graphVersion: string;
  fetch: typeof fetch;
  timeoutMs?: number;
}

const MAX_STOPS = 4;

/**
 * Valhalla `/route` adapter. Request and response shapes follow the published route API
 * reference (trip.summary.length in kilometres, time in seconds, has_toll/has_ferry,
 * legs[].shape as 6-digit polyline). Checked against a local Valhalla 3.9.0 on 5 October 2026 (see docs/routing.md).
 */
export function createValhallaProvider(config: ValhallaConfig): RoutingProvider {
  const contextId = `valhalla|${config.graphVersion}|auto|km`;
  return {
    contextId,
    async route(stops, signal) {
      if (stops.length < 2 || stops.length > MAX_STOPS) return { kind: "error", reason: "unsupported number of stops" };
      const body = {
        // Every stop is a `break`: a genuine stop with its own leg, not a pass-through.
        locations: stops.map((s) => ({ lat: s.lat, lon: s.lon, type: "break" })),
        costing: "auto",
        units: "kilometers",
        directions_type: "none",
      };
      let response: Response;
      try {
        const timeout = AbortSignal.timeout(config.timeoutMs ?? 8000);
        response = await config.fetch(`${config.baseUrl.replace(/\/$/, "")}/route`, {
          method: "POST",
          headers: { "content-type": "application/json", accept: "application/json" },
          body: JSON.stringify(body),
          signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
        });
      } catch {
        return { kind: "error", reason: "request failed or timed out" };
      }
      const json: unknown = await response.json().catch(() => undefined);
      const code = (json as { error_code?: unknown } | undefined)?.error_code;
      // 442: no path; 171: no road edge near a stop (observed for a point at sea);
      // 170: stops in unconnected regions. All mean "cannot be connected", not a fault.
      if (response.status === 400 && (code === 442 || code === 171 || code === 170)) return { kind: "no_route" };
      if (!response.ok) return { kind: "error", reason: `HTTP ${response.status}` };
      return parseTrip(json, stops, contextId, config.graphVersion);
    },
  };
}

type Obj = Record<string, unknown>;
const obj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);

export function parseTrip(json: unknown, stops: readonly Coordinates[], contextId: string, graphVersion: string): RouteOutcome {
  const bad = (reason: string): RouteOutcome => ({ kind: "error", reason });
  const trip = obj(json) ? json.trip : undefined;
  if (!obj(trip) || !obj(trip.summary) || !Array.isArray(trip.legs)) return bad("response did not match the route format");
  if (trip.status !== undefined && trip.status !== 0) return bad("engine reported a non-zero status");
  if (trip.units !== undefined && trip.units !== "kilometers") return bad("unexpected distance units");
  const { length, time, has_toll, has_ferry } = trip.summary;
  if (typeof length !== "number" || typeof time !== "number" || !(length >= 0) || !(time >= 0)
    || !Number.isFinite(length) || !Number.isFinite(time)
    || typeof has_toll !== "boolean" || typeof has_ferry !== "boolean") return bad("summary missing distance, time or ferry/toll flags");
  if (trip.legs.length !== stops.length - 1) return bad("leg count does not match stops");

  const points: [number, number][] = [];
  const snap: number[] = [];
  try {
    trip.legs.forEach((leg, i) => {
      if (!obj(leg) || typeof leg.shape !== "string") throw new Error("leg shape");
      const shape = decodePolyline(leg.shape);
      const first = shape[0];
      const last = shape[shape.length - 1];
      if (!first || !last) throw new Error("empty shape");
      const from = stops[i]!;
      const to = stops[i + 1]!;
      // Where the path begins/ends on the road network versus where we asked.
      snap[i] = Math.max(snap[i] ?? 0, haversine(from.lat, from.lon, first[0], first[1]));
      snap[i + 1] = haversine(to.lat, to.lon, last[0], last[1]);
      points.push(...(i === 0 ? shape : shape.slice(1)));
    });
  } catch {
    return bad("route geometry could not be decoded");
  }
  return {
    kind: "ok",
    path: {
      totals: {
        // Kilometres to whole metres; Valhalla reports three decimals.
        distanceMetres: String(Math.round(length * 1000)),
        durationSeconds: String(Math.round(time)),
        contextId,
        usesFerry: has_ferry,
        usesToll: has_toll,
      },
      geometry: encodePolyline(points),
      snapMetres: snap,
      provider: "valhalla",
      graphVersion,
    },
  };
}
