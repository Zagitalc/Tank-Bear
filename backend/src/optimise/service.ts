import { CalculationInputError, rankFuelStops } from "../domain/economics.ts";
import type { Coordinates, ObservedPrice } from "../domain/types.ts";
import type { FuelRepository } from "../repositories/fuel.ts";
import { decodePolyline } from "../routing/polyline.ts";
import { MAX_CANDIDATES, routeJourney } from "../routing/journey.ts";
import type { RoutingProvider } from "../routing/types.ts";
import { openingStatus, type OpeningStatus } from "../opening/status.ts";
import { buildLine, corridorBoxes, locateOnLine } from "./geometry.ts";
import type { OptimiseRequest } from "./request.ts";
import { shortlist, type Located } from "./selection.ts";

/** Provisional policy values, not provider facts. */
export const FEED_MAX_AGE_SECONDS = 6 * 3600;
export const MAX_JOURNEY_METRES = 400_000;
export const FUEL_TRIP_RADIUS_METRES = 10_000;
export const REQUEST_BUDGET_MS = 20_000;

export interface Deps {
  repo: FuelRepository;
  provider: RoutingProvider;
  now: () => number;
}

export interface ServiceResult {
  status: number;
  body: unknown;
}

const err = (status: number, code: string, message: string): ServiceResult => ({ status, body: { error: { code, message } } });
const feedFuel = (f: "E10" | "B7") => (f === "E10" ? "E10" : "B7_STANDARD");

class TooLong extends Error {}

export async function optimiseJourney(deps: Deps, req: OptimiseRequest): Promise<ServiceResult> {
  const nowMs = deps.now();
  const feed = await deps.repo.feedStatus();
  if (!feed.lastSuccessAt) return err(503, "NO_FUEL_DATA", "Fuel price data has not been loaded yet.");
  // Staleness is about the last successful feed check, not about when a price last changed.
  if (nowMs - Date.parse(feed.lastSuccessAt) > FEED_MAX_AGE_SECONDS * 1000) {
    return err(503, "FUEL_DATA_STALE", "Fuel price data has not refreshed recently enough to recommend a stop.");
  }

  const priceById = new Map<string, { pence: string; updated: string; located: Located; opening: OpeningStatus }>();
  let inCorridor = 0;
  let closedNow = 0;
  let routing;
  try {
    routing = await routeJourney(deps.provider, {
      mode: req.mode, origin: req.origin, ...(req.destination ? { destination: req.destination } : {}),
      deadline: nowMs + REQUEST_BUDGET_MS, now: deps.now,
      stations: async (baseline) => {
        if (baseline && Number(baseline.route.distanceMetres) > MAX_JOURNEY_METRES) throw new TooLong();
        const line = baseline
          ? buildLine(decodePolyline(baseline.geometry))
          : buildLine([[req.origin.lat, req.origin.lon], [req.origin.lat, req.origin.lon]]);
        const corridor = baseline ? undefined : FUEL_TRIP_RADIUS_METRES;
        const boxes = corridorBoxes(line, (corridor ?? 3000) + 500);
        const all = await deps.repo.pricedStationsInBoxes(boxes, feedFuel(req.fuelType));
        // Stations that are closed right now by their usual hours are not offered. Unknown hours stay in,
        // flagged. Arrival may be later than now, so "open" is not a promise (see docs/routing.md).
        const at = new Date(nowMs);
        const statuses = new Map(all.map((r) => [r.station.nodeId, openingStatus(r.station.openingHours, at)]));
        const rows = all.filter((r) => statuses.get(r.station.nodeId)!.state !== "closed");
        closedNow = all.filter((r) => !rows.includes(r))
          .filter((r) => locateOnLine(line, { lat: r.station.latitude, lon: r.station.longitude }).offsetMetres <= (corridor ?? 3000)).length;
        const list = shortlist(line, rows.map((r) => ({
          stationId: r.station.nodeId, name: r.station.tradingName,
          coordinates: { lat: r.station.latitude, lon: r.station.longitude }, pencePerLitre: r.pencePerLitre,
        })), MAX_CANDIDATES, corridor);
        inCorridor = list.inCorridor;
        const updated = new Map(rows.map((r) => [r.station.nodeId, r.priceLastUpdated]));
        for (const c of list.chosen) priceById.set(c.stationId, { pence: c.pencePerLitre, updated: updated.get(c.stationId)!, located: c, opening: statuses.get(c.stationId)! });
        return list.chosen.map((c) => ({ stationId: c.stationId, coordinates: c.coordinates }));
      },
    });
  } catch (e) {
    if (e instanceof TooLong) return err(422, "JOURNEY_TOO_LONG", "Journeys over 400 km are not supported yet.");
    return err(503, "UNAVAILABLE", "Station or routing data is temporarily unavailable.");
  }

  if (routing.status === "baseline_failed") {
    return routing.reason === "no_route"
      ? err(422, "NO_ROUTE", "No driving route could be found between those points.")
      : err(502, "ROUTING_UNAVAILABLE", "The routing service did not answer.");
  }

  const baseline = routing.baseline;
  const limitExcluded: { stationId: string; reason: "over_detour_limit" }[] = [];
  const usable = routing.routed.filter((r) => {
    const extraDist = Number(r.route.distanceMetres) - Number(baseline?.route.distanceMetres ?? 0);
    const extraTime = Number(r.route.durationSeconds) - Number(baseline?.route.durationSeconds ?? 0);
    const over = (req.limits.maxExtraDistanceMetres !== undefined && extraDist > req.limits.maxExtraDistanceMetres)
      || (req.limits.maxExtraDurationSeconds !== undefined && extraTime > req.limits.maxExtraDurationSeconds);
    if (over) limitExcluded.push({ stationId: r.stationId, reason: "over_detour_limit" });
    return !over;
  });

  const fetchedAt = feed.lastSuccessAt;
  const candidates = usable.map((r) => {
    const info = priceById.get(r.stationId)!;
    const price: ObservedPrice = {
      fuelType: req.fuelType, pencePerLitre: info.pence, source: "Fuel Finder",
      sourceUpdatedAt: info.updated, fetchedAt,
    };
    return { stationId: r.stationId, name: info.located.name, coordinates: info.located.coordinates, price, route: r.route };
  });

  let result;
  try {
    const common = {
      origin: req.origin, fuelType: req.fuelType, mpgImperial: req.mpgImperial, litresToBuy: req.litresToBuy,
      routingContextId: deps.provider.contextId, now: new Date(nowMs).toISOString(),
      maxObservationAgeSeconds: FEED_MAX_AGE_SECONDS, candidates,
    };
    result = req.mode === "along_journey" && baseline && req.destination
      ? rankFuelStops({ ...common, mode: "along_journey", destination: req.destination, baselineRoute: baseline.route })
      : rankFuelStops({ ...common, mode: "fuel_trip" });
  } catch (e) {
    if (e instanceof CalculationInputError) return err(422, "ROUTE_NEEDS_REVIEW", "The base route uses a ferry or toll, so costs cannot be compared reliably.");
    throw e;
  }

  const geometry = new Map(usable.map((r) => [r.stationId, r]));
  return {
    status: 200,
    body: {
      calculationVersion: result.calculationVersion,
      mode: result.mode,
      status: result.status,
      // Honest scope: this is the best among stations routed, not a nationwide optimum.
      scope: "Best among the stations checked.",
      coverage: {
        stationsInSearchArea: inCorridor,
        closedNowExcluded: closedNow,
        stationsRouted: routing.routed.length,
        routingCalls: routing.routingCalls,
        partial: routing.status === "partial",
        notRouted: routing.unrouted,
        excludedByLimits: limitExcluded,
      },
      routing: { provider: routing.provider, graphVersion: routing.graphVersion },
      feed: { source: "Fuel Finder", lastSuccessfulRefreshAt: feed.lastSuccessAt },
      baseline: baseline ? { route: baseline.route, geometry: baseline.geometry } : null,
      reference: result.reference,
      labels: result.labels,
      candidates: result.candidates.map((c) => {
        const info = priceById.get(c.stationId)!;
        const routed = geometry.get(c.stationId)!;
        return {
          ...c,
          station: { id: c.stationId, name: info.located.name, position: info.located.coordinates as Coordinates },
          priceSource: { priceLastUpdated: info.updated, feedLastCheckedAt: fetchedAt },
          routeGeometry: routed.geometry,
          stationSnapMetres: Math.round(routed.snapMetres),
          opening: info.opening,
        };
      }),
      excluded: result.excluded,
    },
  };
}
