import {
  add, CalculationInputError, compare, decimal, decimalText, divide,
  multiply, nonNegative, pence, positive, subtract, ZERO,
} from "./numbers.ts";
import type { Fraction } from "./numbers.ts";
import type {
  Candidate, Coordinates, EconomicsInput, EconomicsResult, ExcludedCandidate,
  ExclusionReason, ObservedPrice, RankedCandidate, RouteTotals,
} from "./types.ts";

export { CalculationInputError } from "./numbers.ts";
export type { EconomicsInput, EconomicsResult } from "./types.ts";

const LITRES_PER_IMPERIAL_GALLON = decimal("4.54609", "constant");
const METRES_PER_MILE = decimal("1609.344", "constant");

export function imperialGallonsToLitres(gallons: string): string {
  return decimalText(multiply(nonNegative(gallons, "gallons"), LITRES_PER_IMPERIAL_GALLON));
}

export function litresPerMile(mpgImperial: string): string {
  return decimalText(divide(LITRES_PER_IMPERIAL_GALLON, positive(mpgImperial, "mpgImperial")));
}

export function validateCoordinates(point: Coordinates, field = "coordinates"): void {
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)
    || point.lat < -90 || point.lat > 90 || point.lon < -180 || point.lon > 180) {
    throw new CalculationInputError(field, "must be finite WGS84 latitude/longitude");
  }
}

function identifier(value: string, field: string): void {
  if (typeof value !== "string" || !value.trim() || value.length > 256) {
    throw new CalculationInputError(field, "must be a non-empty identifier or label");
  }
}

function timestamp(value: string): number {
  if (typeof value !== "string") throw new CalculationInputError("timestamp", "must be ISO 8601");
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,3})?(Z|[+-]\d{2}:\d{2})$/.exec(value);
  const parsed = Date.parse(value);
  if (!match || !Number.isFinite(parsed)) throw new CalculationInputError("timestamp", "must include a timezone");
  const [, date = "", hour = "", minute = "", second = "", zone = ""] = match;
  const calendarDate = new Date(`${date}T00:00:00Z`);
  if (!Number.isFinite(calendarDate.getTime()) || calendarDate.toISOString().slice(0, 10) !== date
    || Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59
    || (zone !== "Z" && (Number(zone.slice(1, 3)) > 23 || Number(zone.slice(4)) > 59))) {
    throw new CalculationInputError("timestamp", "must be a valid date and time");
  }
  return parsed;
}

function routeValues(route: RouteTotals): { distance: Fraction; duration: Fraction } {
  if (!route || typeof route !== "object") {
    throw new CalculationInputError("route", "a successful route is required");
  }
  identifier(route.contextId, "route.contextId");
  if (typeof route.usesFerry !== "boolean" || typeof route.usesToll !== "boolean") {
    throw new CalculationInputError("route", "ferry/toll flags must be explicit");
  }
  return {
    distance: nonNegative(route.distanceMetres, "route.distanceMetres"),
    duration: nonNegative(route.durationSeconds, "route.durationSeconds"),
  };
}

function priceFailure(price: ObservedPrice | null, input: EconomicsInput, now: number): ExclusionReason | null {
  if (!price) return "missing_price";
  if (price.fuelType !== input.fuelType) return "wrong_fuel_type";
  try {
    positive(price.pencePerLitre, "price.pencePerLitre");
    identifier(price.source, "price.source");
  } catch { return "invalid_price"; }
  let updated: number;
  let fetched: number;
  try {
    updated = timestamp(price.sourceUpdatedAt);
    fetched = timestamp(price.fetchedAt);
  } catch { return "invalid_price_timestamp"; }
  if (updated > now || fetched > now) return "future_price_timestamp";
  if (updated > fetched) return "invalid_price_timestamp";
  if (now - fetched > input.maxObservationAgeSeconds * 1000) return "stale_observation";
  return null;
}

interface Scored {
  candidate: Candidate & { price: ObservedPrice; route: RouteTotals };
  price: Fraction;
  distance: Fraction;
  duration: Fraction;
  miles: Fraction;
  fuel: Fraction;
  fillCost: Fraction;
  detourCost: Fraction;
  totalCost: Fraction;
}

const stationOrder = (a: Scored, b: Scored): number =>
  a.candidate.stationId < b.candidate.stationId ? -1 : a.candidate.stationId > b.candidate.stationId ? 1 : 0;
const detourOrder = (a: Scored, b: Scored): number =>
  compare(a.duration, b.duration) || compare(a.distance, b.distance) || stationOrder(a, b);
const costOrder = (a: Scored, b: Scored): number =>
  compare(a.totalCost, b.totalCost) || compare(a.duration, b.duration) || stationOrder(a, b);
const pumpOrder = (a: Scored, b: Scored): number =>
  compare(a.price, b.price) || costOrder(a, b);

function display(candidate: Scored, reference: Scored, best: Scored, rank: number): RankedCandidate {
  const fillCostPence = pence(candidate.fillCost);
  const detourFuelCostPence = pence(candidate.detourCost);
  const comparisonCostPence = pence(candidate.totalCost);
  const pumpSavingPence = pence(subtract(reference.fillCost, candidate.fillCost));
  const referenceDetourFuelCostPence = pence(reference.detourCost);
  const saving = subtract(reference.totalCost, candidate.totalCost);
  const trueSavingPence = pence(saving);
  return {
    stationId: candidate.candidate.stationId,
    name: candidate.candidate.name,
    rank,
    price: { ...candidate.candidate.price, pencePerLitre: decimalText(candidate.price) },
    route: { ...candidate.candidate.route },
    detourDistanceMetres: decimalText(candidate.distance),
    detourDurationSeconds: decimalText(candidate.duration),
    detourMiles: decimalText(candidate.miles),
    detourFuelLitres: decimalText(candidate.fuel),
    fillCostPence,
    detourFuelCostPence,
    comparisonCostPence,
    costRoundingAdjustmentPence: comparisonCostPence - fillCostPence - detourFuelCostPence,
    pumpSavingPence,
    referenceDetourFuelCostPence,
    trueSavingPence,
    savingRoundingAdjustmentPence: trueSavingPence - (pumpSavingPence - detourFuelCostPence + referenceDetourFuelCostPence),
    worseThanBestPence: pence(subtract(candidate.totalCost, best.totalCost)),
    cashComparison: compare(saving, ZERO) > 0 ? "lower_cost" : compare(saving, ZERO) < 0 ? "higher_cost" : "equal_cost",
  };
}

/** Pure engine: supplied routes/prices only; no I/O, clock reads or mutation. */
export function rankFuelStops(input: EconomicsInput): EconomicsResult {
  if (input.mode !== "along_journey" && input.mode !== "fuel_trip") {
    throw new CalculationInputError("mode", "only along_journey and fuel_trip have routed economics");
  }
  validateCoordinates(input.origin, "origin");
  if (input.fuelType !== "E10" && input.fuelType !== "B7") {
    throw new CalculationInputError("fuelType", "must be E10 or standard diesel B7");
  }
  identifier(input.routingContextId, "routingContextId");
  const mpg = positive(input.mpgImperial, "mpgImperial");
  const litres = positive(input.litresToBuy, "litresToBuy");
  const now = timestamp(input.now);
  if (!Number.isSafeInteger(input.maxObservationAgeSeconds) || input.maxObservationAgeSeconds <= 0
    || input.maxObservationAgeSeconds > Number.MAX_SAFE_INTEGER / 1000) {
    throw new CalculationInputError("maxObservationAgeSeconds", "must be positive safe whole seconds");
  }
  if (!Array.isArray(input.candidates)) throw new CalculationInputError("candidates", "must be an array");
  if (input.referenceStationId !== undefined) identifier(input.referenceStationId, "referenceStationId");

  let baseline = { distance: ZERO, duration: ZERO };
  if (input.mode === "along_journey") {
    validateCoordinates(input.destination, "destination");
    baseline = routeValues(input.baselineRoute);
    if (input.baselineRoute.contextId !== input.routingContextId) {
      throw new CalculationInputError("baselineRoute", "routing context must match");
    }
    if (input.baselineRoute.usesFerry || input.baselineRoute.usesToll) {
      throw new CalculationInputError("baselineRoute", "unpriced ferry/toll routes require review");
    }
  } else if (input.baselineRoute !== undefined || input.destination !== undefined) {
    throw new CalculationInputError("fuel_trip", "uses a zero baseline and an entire return route");
  }

  const excluded: ExcludedCandidate[] = [];
  const scored: Scored[] = [];
  const counts = new Map<string, number>();
  for (const candidate of input.candidates) counts.set(candidate.stationId, (counts.get(candidate.stationId) ?? 0) + 1);
  const seen = new Set<string>();

  for (const candidate of input.candidates) {
    if (seen.has(candidate.stationId)) continue;
    seen.add(candidate.stationId);
    const exclude = (reason: ExclusionReason) => excluded.push({ stationId: candidate.stationId, reason });
    if ((counts.get(candidate.stationId) ?? 0) > 1) { exclude("duplicate_station"); continue; }
    try {
      identifier(candidate.stationId, "stationId");
      identifier(candidate.name, "name");
    } catch { exclude("invalid_station"); continue; }
    try { validateCoordinates(candidate.coordinates); } catch { exclude("invalid_coordinates"); continue; }
    const failure = priceFailure(candidate.price, input, now);
    if (failure) { exclude(failure); continue; }
    if (!candidate.route) { exclude("no_route"); continue; }
    let totals: ReturnType<typeof routeValues>;
    try { totals = routeValues(candidate.route); } catch { exclude("invalid_route"); continue; }
    if (candidate.route.contextId !== input.routingContextId) { exclude("incompatible_route"); continue; }
    if (candidate.route.usesFerry || candidate.route.usesToll) { exclude("unpriced_ferry_or_toll"); continue; }
    const distance = subtract(totals.distance, baseline.distance);
    const duration = subtract(totals.duration, baseline.duration);
    if (compare(distance, ZERO) < 0 || compare(duration, ZERO) < 0) {
      excluded.push({
        stationId: candidate.stationId, reason: "negative_route_difference",
        detourDistanceMetres: decimalText(distance), detourDurationSeconds: decimalText(duration),
      });
      continue;
    }
    // priceFailure has validated this price; keep the narrowed value for scoring.
    const observedPrice = candidate.price;
    if (!observedPrice) { exclude("missing_price"); continue; }
    const price = positive(observedPrice.pencePerLitre, "price.pencePerLitre");
    const miles = divide(distance, METRES_PER_MILE);
    const fuel = divide(multiply(miles, LITRES_PER_IMPERIAL_GALLON), mpg);
    const fillCost = multiply(litres, price);
    const detourCost = multiply(fuel, price);
    const totalCost = add(fillCost, detourCost);
    try {
      pence(fillCost); pence(detourCost); pence(totalCost);
    } catch { exclude("money_out_of_range"); continue; }
    scored.push({
      candidate: { ...candidate, price: observedPrice, route: candidate.route },
      price, distance, duration, miles, fuel, fillCost, detourCost, totalCost,
    });
  }

  const byDetour = [...scored].sort(detourOrder);
  const reference = input.referenceStationId !== undefined
    ? scored.find((row) => row.candidate.stationId === input.referenceStationId)
    : byDetour[0];
  if (input.referenceStationId !== undefined && !reference) {
    throw new CalculationInputError("referenceStationId", "selected station is missing or ineligible");
  }
  const ordered = [...scored].sort(costOrder);
  const best = ordered[0];
  const cheapest = [...scored].sort(pumpOrder)[0];
  const smallest = byDetour[0];
  excluded.sort((a, b) => a.stationId < b.stationId ? -1 : a.stationId > b.stationId ? 1 : 0);
  return {
    calculationVersion: "1",
    mode: input.mode,
    status: best ? "ranked" : "no_candidates",
    reference: reference ? {
      stationId: reference.candidate.stationId,
      selection: input.referenceStationId !== undefined ? "explicit" : "smallest_added_driving_time",
    } : null,
    labels: best && cheapest && smallest ? {
      bestOverallStationId: best.candidate.stationId,
      cheapestPumpStationId: cheapest.candidate.stationId,
      smallestDetourStationId: smallest.candidate.stationId,
    } : null,
    candidates: best && reference ? ordered.map((row, index) => display(row, reference, best, index + 1)) : [],
    excluded,
  };
}
