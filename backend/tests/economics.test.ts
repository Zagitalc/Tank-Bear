import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { URL as NodeURL } from "node:url";
import {
  CalculationInputError, imperialGallonsToLitres, litresPerMile,
  rankFuelStops, validateCoordinates,
} from "../src/domain/economics.ts";
import type { Candidate, EconomicsInput, ObservedPrice, RankedCandidate, RouteTotals } from "../src/domain/types.ts";

type Journey = Extract<EconomicsInput, { mode: "along_journey" }>;
type ValidCandidate = Candidate & { price: ObservedPrice; route: RouteTotals };
const fixture = JSON.parse(readFileSync(
  new NodeURL("../../contracts/fixtures/economics-cash-saving.json", import.meta.url), "utf8",
)) as {
  input: Journey;
  expected: { order: string[]; stationId: string; pumpSavingPence: number; detourFuelCostPence: number; trueSavingPence: number };
};

function station(id: string, price = "170", distance = "10000", duration = "1000"): ValidCandidate {
  return {
    stationId: id, name: `${id} fixture`, coordinates: { lat: 51.455, lon: -0.965 },
    price: {
      fuelType: "E10", pencePerLitre: price, source: "Synthetic fixture",
      sourceUpdatedAt: "2026-10-05T11:45:00Z", fetchedAt: "2026-10-05T11:55:00Z",
    },
    route: {
      distanceMetres: distance, durationSeconds: duration,
      contextId: "synthetic-auto-v1", usesFerry: false, usesToll: false,
    },
  };
}

function journey(overrides: Partial<Journey> = {}): Journey {
  return { ...structuredClone(fixture.input), ...overrides };
}

function row(input: EconomicsInput, id: string): RankedCandidate {
  const result = rankFuelStops(input).candidates.find((candidate) => candidate.stationId === id);
  assert.ok(result, `Missing ranked candidate ${id}`);
  return result;
}

test("Imperial gallons and MPG use 4.54609 litres per gallon", () => {
  assert.equal(imperialGallonsToLitres("1"), "4.54609");
  assert.equal(imperialGallonsToLitres("2.5"), "11.365225");
  assert.equal(imperialGallonsToLitres("0"), "0");
  assert.equal(litresPerMile("45"), "0.101024222");
  assert.equal(litresPerMile("4.54609"), "1");
});

test("fixture gives 150p pump saving minus 80p detour fuel = 70p true saving", () => {
  const result = rankFuelStops(fixture.input);
  assert.deepEqual(result.candidates.map((candidate) => candidate.stationId), fixture.expected.order);
  const cheaper = row(fixture.input, fixture.expected.stationId);
  assert.equal(cheaper.pumpSavingPence, fixture.expected.pumpSavingPence);
  assert.equal(cheaper.detourFuelCostPence, fixture.expected.detourFuelCostPence);
  assert.equal(cheaper.trueSavingPence, fixture.expected.trueSavingPence);
  assert.equal(cheaper.detourDistanceMetres, "780.288");
  assert.equal(cheaper.detourDurationSeconds, "60");
  assert.equal(cheaper.cashComparison, "lower_cost");
  assert.equal(result.calculationVersion, "1");
  assert.doesNotThrow(() => JSON.stringify(result));
});

test("actual additional route miles determine the winner, rather than cheapest pump", () => {
  const example = JSON.parse(readFileSync(
    new NodeURL("../../contracts/fixtures/economics-route-ranking.json", import.meta.url), "utf8",
  )) as { input: Journey; expected: Record<string, string | number> };
  const input = example.input;
  const result = rankFuelStops(input);
  assert.deepEqual(result.labels, {
    bestOverallStationId: "near-route", cheapestPumpStationId: "cheap-pump", smallestDetourStationId: "reference",
  });
  const near = row(input, "near-route");
  const cheap = row(input, "cheap-pump");
  assert.equal(near.detourMiles, "0.8");
  assert.equal(cheap.detourMiles, "7.2");
  assert.equal(near.detourFuelCostPence, 14);
  assert.equal(cheap.detourFuelCostPence, 121);
  assert.equal(near.comparisonCostPence, example.expected.nearRouteComparisonCostPence);
  assert.equal(cheap.comparisonCostPence, example.expected.cheapestPumpComparisonCostPence);
  assert.equal(cheap.worseThanBestPence, example.expected.cheapestPumpWorseThanBestPence);
});

test("zero detour has zero fuel cost and preserves the full pump saving", () => {
  const input = journey({ candidates: [station("reference"), station("cheaper", "165")] });
  const cheaper = row(input, "cheaper");
  assert.equal(cheaper.detourFuelLitres, "0");
  assert.equal(cheaper.detourFuelCostPence, 0);
  // Explicit reference avoids default tie selection depending on stable station ID.
  assert.equal(row({ ...input, referenceStationId: "reference" }, "cheaper").trueSavingPence, 150);
});

test("equal pump prices with a detour give a negative saving", () => {
  const input = journey({ candidates: [station("reference"), station("detour", "170", "11609.344", "1060")] });
  const detour = row(input, "detour");
  assert.equal(detour.pumpSavingPence, 0);
  assert.equal(detour.detourFuelCostPence, 170);
  assert.equal(detour.trueSavingPence, -170);
});

test("a cheaper pump with an expensive detour loses to the reference", () => {
  const input = journey({ candidates: [station("reference"), station("cheaper", "165", "13218.688", "1120")] });
  assert.equal(rankFuelStops(input).labels?.bestOverallStationId, "reference");
  assert.equal(row(input, "cheaper").trueSavingPence, -180);
  assert.equal(row(input, "cheaper").cashComparison, "higher_cost");
});

test("a more expensive zero-detour station has negative pump and true savings", () => {
  const input = journey({ referenceStationId: "reference", candidates: [station("reference"), station("expensive", "175")] });
  assert.equal(row(input, "expensive").trueSavingPence, -150);
  assert.equal(row(input, "expensive").detourFuelCostPence, 0);
});

test("exact break-even is a tie, resolved by driving time", () => {
  const input = journey({ candidates: [station("reference"), station("break-even", "165", "11463.04", "1060")] });
  assert.equal(row(input, "break-even").detourFuelCostPence, 150);
  assert.equal(row(input, "break-even").trueSavingPence, 0);
  assert.equal(row(input, "break-even").cashComparison, "equal_cost");
  assert.equal(rankFuelStops(input).labels?.bestOverallStationId, "reference");
});

test("fill amount changes whether a fixed detour is worthwhile", () => {
  const small = journey({ litresToBuy: "10" });
  const large = journey({ litresToBuy: "30" });
  assert.equal(row(small, "cheaper").trueSavingPence, -30);
  assert.equal(row(large, "cheaper").trueSavingPence, 70);
  assert.equal(rankFuelStops(small).labels?.bestOverallStationId, "reference");
  assert.equal(rankFuelStops(large).labels?.bestOverallStationId, "cheaper");
});

test("reference detour cost is credited, so visible comparison arithmetic is correct", () => {
  const input = journey({
    referenceStationId: "reference",
    candidates: [station("reference", "170", "11609.344", "1040"), station("cheaper", "165", "11609.344", "1060")],
  });
  const cheaper = row(input, "cheaper");
  assert.equal(cheaper.pumpSavingPence, 150);
  assert.equal(cheaper.referenceDetourFuelCostPence, 170);
  assert.equal(cheaper.detourFuelCostPence, 165);
  assert.equal(cheaper.trueSavingPence, 155);
  assert.equal(row(input, "reference").trueSavingPence, 0);
});

test("Fuel Trip attributes the entire return route to fuel buying", () => {
  const { destination: _destination, baselineRoute: _baseline, ...common } = journey();
  const input: EconomicsInput = {
    ...common, mode: "fuel_trip",
    candidates: [station("reference", "170", "1609.344", "180"), station("cheaper", "165", "3218.688", "300")],
  };
  const cheaper = row(input, "cheaper");
  assert.equal(cheaper.detourDistanceMetres, "3218.688");
  assert.equal(cheaper.detourDurationSeconds, "300");
  assert.equal(cheaper.detourFuelLitres, "2");
  assert.equal(cheaper.trueSavingPence, -10); // 150 - 330 + 170.
  assert.equal(rankFuelStops(input).labels?.bestOverallStationId, "reference");
});

test("Fuel Trip cannot accidentally reuse a journey baseline; Nearby has no routed score", () => {
  assert.throws(() => rankFuelStops({ ...journey(), mode: "fuel_trip" } as unknown as EconomicsInput), CalculationInputError);
  assert.throws(() => rankFuelStops({ ...journey(), mode: "nearby" } as unknown as EconomicsInput), CalculationInputError);
});

test("rank uses unrounded prices even when all displayed fill totals round to zero", () => {
  const input = journey({ litresToBuy: "0.001", candidates: [station("a", "170"), station("z", "169.9")] });
  const result = rankFuelStops(input);
  assert.equal(result.candidates[0]?.stationId, "z");
  assert.equal(result.candidates[0]?.comparisonCostPence, 0);
  assert.equal(result.candidates[1]?.comparisonCostPence, 0);
});

test("money rounds half away from zero, including a negative half-penny saving", () => {
  const input = journey({ litresToBuy: "0.5", referenceStationId: "reference", candidates: [station("reference", "2"), station("higher", "3")] });
  assert.equal(row(input, "higher").fillCostPence, 2);
  assert.equal(row(input, "higher").trueSavingPence, -1);
});

test("explicit penny adjustments reconcile rounded line items with exact totals", () => {
  const input = journey({ litresToBuy: "1", candidates: [station("reference", "1"), station("fractional", "0.4", "11609.344", "1060")] });
  const fractional = row(input, "fractional");
  assert.equal(fractional.fillCostPence, 0);
  assert.equal(fractional.detourFuelCostPence, 0);
  assert.equal(fractional.comparisonCostPence, 1);
  assert.equal(fractional.costRoundingAdjustmentPence, 1);
  assert.equal(fractional.pumpSavingPence, 1);
  assert.equal(fractional.trueSavingPence, 0);
  assert.equal(fractional.savingRoundingAdjustmentPence, -1);
  assert.equal(fractional.cashComparison, "lower_cost");
  for (const item of rankFuelStops(input).candidates) {
    assert.equal(item.fillCostPence + item.detourFuelCostPence + item.costRoundingAdjustmentPence, item.comparisonCostPence);
    assert.equal(item.pumpSavingPence - item.detourFuelCostPence + item.referenceDetourFuelCostPence + item.savingRoundingAdjustmentPence, item.trueSavingPence);
  }
});

test("default reference is minimum added driving time, independently of economic rank", () => {
  const result = rankFuelStops(journey());
  assert.deepEqual(result.reference, { stationId: "reference", selection: "smallest_added_driving_time" });
  assert.equal(result.candidates[0]?.stationId, "cheaper");
});

test("equal costs use driving time and stable station ID regardless of input order", () => {
  const a = station("a", "170", "11609.344", "1010");
  const z = station("z", "170", "11609.344", "1010");
  const faster = station("fast", "170", "11609.344", "1005");
  const permutations = [[z, a, faster], [faster, a, z], [a, faster, z]];
  const expected = rankFuelStops(journey({ candidates: permutations[0]! }));
  for (const candidates of permutations) {
    const result = rankFuelStops(journey({ candidates }));
    assert.deepEqual(result, expected);
    assert.deepEqual(result.candidates.map((item) => item.stationId), ["fast", "a", "z"]);
  }
});

test("input objects and order are preserved, including source metadata and decimal prices", () => {
  const input = journey({ candidates: [station("reference"), station("price", "0165.9000")] });
  const original = structuredClone(input);
  const result = rankFuelStops(input);
  assert.deepEqual(input, original);
  const item = result.candidates.find((candidate) => candidate.stationId === "price")!;
  assert.equal(item.price.pencePerLitre, "165.9");
  assert.equal(item.price.source, "Synthetic fixture");
  item.price.source = "modified output";
  assert.deepEqual(input, original);
});

test("an old unchanged price remains eligible when recently observed", () => {
  const candidate = station("old-unchanged");
  candidate.price.sourceUpdatedAt = "2026-01-01T00:00:00Z";
  assert.equal(rankFuelStops(journey({ candidates: [candidate] })).status, "ranked");
});

for (const [name, change, reason] of [
  ["missing price", (c: Candidate) => { c.price = null; }, "missing_price"],
  ["stale fetched data", (c: ValidCandidate) => { c.price.sourceUpdatedAt = "2026-10-05T11:00:00Z"; c.price.fetchedAt = "2026-10-05T11:44:59.999Z"; }, "stale_observation"],
  ["wrong grade", (c: ValidCandidate) => { c.price.fuelType = "B7"; }, "wrong_fuel_type"],
  ["zero price", (c: ValidCandidate) => { c.price.pencePerLitre = "0"; }, "invalid_price"],
  ["negative price", (c: ValidCandidate) => { c.price.pencePerLitre = "-1"; }, "invalid_price"],
  ["invalid price", (c: ValidCandidate) => { c.price.pencePerLitre = "NaN"; }, "invalid_price"],
  ["missing source", (c: ValidCandidate) => { c.price.source = ""; }, "invalid_price"],
  ["timezone missing", (c: ValidCandidate) => { c.price.sourceUpdatedAt = "2026-10-05T11:45:00"; }, "invalid_price_timestamp"],
  ["invalid calendar date", (c: ValidCandidate) => { c.price.sourceUpdatedAt = "2026-02-30T11:45:00Z"; }, "invalid_price_timestamp"],
  ["update after observation", (c: ValidCandidate) => { c.price.sourceUpdatedAt = "2026-10-05T11:56:00Z"; }, "invalid_price_timestamp"],
  ["future price", (c: ValidCandidate) => { c.price.sourceUpdatedAt = "2026-10-05T12:00:01Z"; }, "future_price_timestamp"],
  ["future observation", (c: ValidCandidate) => { c.price.fetchedAt = "2026-10-05T12:00:01Z"; }, "future_price_timestamp"],
  ["no route", (c: Candidate) => { c.route = null; }, "no_route"],
  ["invalid route distance", (c: ValidCandidate) => { c.route.distanceMetres = "-1"; }, "invalid_route"],
  ["invalid route duration", (c: ValidCandidate) => { c.route.durationSeconds = "Infinity"; }, "invalid_route"],
  ["different graph/profile", (c: ValidCandidate) => { c.route.contextId = "different"; }, "incompatible_route"],
  ["ferry", (c: ValidCandidate) => { c.route.usesFerry = true; }, "unpriced_ferry_or_toll"],
  ["toll", (c: ValidCandidate) => { c.route.usesToll = true; }, "unpriced_ferry_or_toll"],
  ["invalid station coordinates", (c: ValidCandidate) => { c.coordinates.lat = 91; }, "invalid_coordinates"],
  ["empty station ID", (c: ValidCandidate) => { c.stationId = ""; }, "invalid_station"],
] as const) {
  test(`${name} is excluded without replacing its values or losing other candidates`, () => {
    const invalid = station("invalid");
    change(invalid);
    const result = rankFuelStops(journey({ candidates: [station("reference"), invalid] }));
    assert.deepEqual(result.candidates.map((item) => item.stationId), ["reference"]);
    assert.deepEqual(result.excluded, [{ stationId: invalid.stationId, reason }]);
  });
}

test("freshness boundary and explicit timezone offsets are handled precisely", () => {
  const candidate = station("boundary");
  candidate.price.sourceUpdatedAt = "2026-10-05T12:30:00+01:00";
  candidate.price.fetchedAt = "2026-10-05T12:45:00+01:00";
  assert.equal(rankFuelStops(journey({ candidates: [candidate] })).status, "ranked");
});

test("duplicates are excluded rather than choosing an arbitrary conflicting price", () => {
  const result = rankFuelStops(journey({ candidates: [station("same", "165"), station("same", "170"), station("other")] }));
  assert.deepEqual(result.excluded, [{ stationId: "same", reason: "duplicate_station" }]);
  assert.equal(result.candidates.length, 1);
});

test("negative route differences are preserved and held for review, even if tiny", () => {
  const result = rankFuelStops(journey({ candidates: [station("shorter", "165", "9999.999999999"), station("faster", "165", "10000", "999")] }));
  assert.equal(result.status, "no_candidates");
  assert.deepEqual(result.excluded, [
    { stationId: "faster", reason: "negative_route_difference", detourDistanceMetres: "0", detourDurationSeconds: "-1" },
    { stationId: "shorter", reason: "negative_route_difference", detourDistanceMetres: "-0.000000001", detourDurationSeconds: "0" },
  ]);
});

test("same origin and destination with zero baseline does not divide by route distance", () => {
  const input = journey({
    destination: fixture.input.origin,
    baselineRoute: { ...fixture.input.baselineRoute, distanceMetres: "0", durationSeconds: "0" },
    candidates: [station("stop", "170", "1609.344", "60")],
  });
  assert.equal(row(input, "stop").detourFuelLitres, "1");
});

test("money too large for integer-pence output is excluded safely", () => {
  const result = rankFuelStops(journey({ litresToBuy: "999999999999", candidates: [station("huge", "999999999999")] }));
  assert.deepEqual(result.excluded, [{ stationId: "huge", reason: "money_out_of_range" }]);
  assert.equal(result.status, "no_candidates");
});

test("empty results have no invented reference, labels or savings", () => {
  const result = rankFuelStops(journey({ candidates: [] }));
  assert.equal(result.status, "no_candidates");
  assert.equal(result.reference, null);
  assert.equal(result.labels, null);
  assert.deepEqual(result.candidates, []);
});

test("an unavailable explicit reference fails instead of silently changing the comparison", () => {
  assert.throws(() => rankFuelStops(journey({ referenceStationId: "missing" })), /referenceStationId/);
  assert.throws(() => rankFuelStops(journey({ referenceStationId: "reference", candidates: [{ ...station("reference"), price: null }] })), /referenceStationId/);
});

test("standard diesel uses the same economics and must match the requested grade", () => {
  const input = journey({ fuelType: "B7" });
  for (const candidate of input.candidates) candidate.price!.fuelType = "B7";
  assert.equal(row(input, "cheaper").trueSavingPence, 70);
});

for (const value of ["0", "-1", "NaN", "Infinity", "", "1e2", "45,5", "0.0000000001", 45 as unknown as string]) {
  test(`invalid MPG ${JSON.stringify(value)} is rejected`, () => {
    assert.throws(() => rankFuelStops(journey({ mpgImperial: value })), CalculationInputError);
    assert.throws(() => litresPerMile(value), CalculationInputError);
  });
}

for (const value of ["0", "-1", "Infinity", "NaN", ""]) {
  test(`invalid litres ${JSON.stringify(value)} is rejected`, () => {
    assert.throws(() => rankFuelStops(journey({ litresToBuy: value })), CalculationInputError);
  });
}

test("invalid origin/destination coordinates and request policies fail clearly", () => {
  for (const point of [{ lat: 91, lon: 0 }, { lat: -91, lon: 0 }, { lat: 0, lon: 181 }, { lat: 0, lon: -181 }, { lat: NaN, lon: 0 }, { lat: 0, lon: Infinity }]) {
    assert.throws(() => rankFuelStops(journey({ origin: point })), CalculationInputError);
    assert.throws(() => rankFuelStops(journey({ destination: point })), CalculationInputError);
  }
  assert.doesNotThrow(() => validateCoordinates({ lat: 90, lon: -180 }));
  for (const age of [0, -1, NaN, Infinity, 0.5, Number.MAX_SAFE_INTEGER]) {
    assert.throws(() => rankFuelStops(journey({ maxObservationAgeSeconds: age })), CalculationInputError);
  }
  assert.throws(() => rankFuelStops(journey({ now: "bad" })), CalculationInputError);
  assert.throws(() => rankFuelStops(journey({ fuelType: "unknown" as "E10" })), CalculationInputError);
});

test("incompatible, invalid or unpriced baseline routes cannot produce a comparison", () => {
  for (const route of [
    { ...fixture.input.baselineRoute, contextId: "different" },
    { ...fixture.input.baselineRoute, distanceMetres: "-1" },
    { ...fixture.input.baselineRoute, usesFerry: true },
    { ...fixture.input.baselineRoute, usesToll: true },
    null as unknown as RouteTotals,
    undefined as unknown as RouteTotals,
  ]) assert.throws(() => rankFuelStops(journey({ baselineRoute: route })), CalculationInputError);
});
