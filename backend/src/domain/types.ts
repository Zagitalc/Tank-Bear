/** These are normalized domain values, not the unverified Fuel Finder schema. */
export type FuelType = "E10" | "B7";

export interface Coordinates {
  lat: number;
  lon: number;
}

export interface RouteTotals {
  distanceMetres: string;
  durationSeconds: string;
  /** Identifies the engine, graph, profile, restrictions and time assumptions. */
  contextId: string;
  usesFerry: boolean;
  usesToll: boolean;
}

export interface ObservedPrice {
  fuelType: FuelType;
  pencePerLitre: string;
  source: string;
  sourceUpdatedAt: string;
  /** Last successful validated upstream observation, not a client cache read. */
  fetchedAt: string;
}

export interface Candidate {
  stationId: string;
  name: string;
  coordinates: Coordinates;
  price: ObservedPrice | null;
  route: RouteTotals | null;
}

interface CommonInput {
  origin: Coordinates;
  fuelType: FuelType;
  mpgImperial: string;
  litresToBuy: string;
  routingContextId: string;
  /** Supplied clock and freshness policy make the engine deterministic. */
  now: string;
  maxObservationAgeSeconds: number;
  candidates: readonly Candidate[];
  referenceStationId?: string;
}

export type EconomicsInput = CommonInput & (
  | { mode: "along_journey"; destination: Coordinates; baselineRoute: RouteTotals }
  | { mode: "fuel_trip"; destination?: never; baselineRoute?: never }
);

export type ExclusionReason =
  | "duplicate_station"
  | "invalid_station"
  | "invalid_coordinates"
  | "missing_price"
  | "wrong_fuel_type"
  | "invalid_price"
  | "invalid_price_timestamp"
  | "future_price_timestamp"
  | "stale_observation"
  | "no_route"
  | "invalid_route"
  | "incompatible_route"
  | "unpriced_ferry_or_toll"
  | "negative_route_difference"
  | "money_out_of_range";

export interface ExcludedCandidate {
  stationId: string;
  reason: ExclusionReason;
  /** Preserve unexpected signed differences for later routing review. */
  detourDistanceMetres?: string;
  detourDurationSeconds?: string;
}

export interface RankedCandidate {
  stationId: string;
  name: string;
  rank: number;
  price: ObservedPrice;
  route: RouteTotals;
  detourDistanceMetres: string;
  detourDurationSeconds: string;
  detourMiles: string;
  detourFuelLitres: string;
  fillCostPence: number;
  detourFuelCostPence: number;
  comparisonCostPence: number;
  costRoundingAdjustmentPence: number;
  pumpSavingPence: number;
  referenceDetourFuelCostPence: number;
  trueSavingPence: number;
  savingRoundingAdjustmentPence: number;
  worseThanBestPence: number;
  cashComparison: "lower_cost" | "equal_cost" | "higher_cost";
}

export interface EconomicsResult {
  calculationVersion: "1";
  mode: EconomicsInput["mode"];
  status: "ranked" | "no_candidates";
  reference: {
    stationId: string;
    selection: "explicit" | "smallest_added_driving_time";
  } | null;
  labels: {
    bestOverallStationId: string;
    cheapestPumpStationId: string;
    smallestDetourStationId: string;
  } | null;
  candidates: RankedCandidate[];
  excluded: ExcludedCandidate[];
}
