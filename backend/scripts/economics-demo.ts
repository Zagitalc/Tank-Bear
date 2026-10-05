import { readFileSync } from "node:fs";
import { URL as NodeURL } from "node:url";
import { rankFuelStops } from "../src/domain/economics.ts";
import type { EconomicsInput } from "../src/domain/types.ts";

const fixture = JSON.parse(readFileSync(
  new NodeURL("../../contracts/fixtures/economics-route-ranking.json", import.meta.url), "utf8",
)) as { description: string; input: EconomicsInput };
const result = rankFuelStops(fixture.input);
const pounds = (pence: number): string => `£${(pence / 100).toFixed(2)}`;

console.log(fixture.description);
console.log(`Reference: ${result.reference?.stationId}; ${fixture.input.litresToBuy} litres, ${fixture.input.mpgImperial} Imperial MPG.`);
console.table(result.candidates.map((candidate) => ({
  rank: candidate.rank,
  station: candidate.name,
  "pump p/L": candidate.price.pencePerLitre,
  "extra miles": candidate.detourMiles,
  "extra min": (Number(candidate.detourDurationSeconds) / 60).toFixed(0),
  "pump saving": pounds(candidate.pumpSavingPence),
  "detour fuel": pounds(candidate.detourFuelCostPence),
  "true saving": pounds(candidate.trueSavingPence),
  "fill + detour": pounds(candidate.comparisonCostPence),
  label: candidate.stationId === result.labels?.bestOverallStationId ? "Best overall"
    : candidate.stationId === result.labels?.cheapestPumpStationId ? "Cheapest pump" : "Smallest detour",
})));
console.log("Amounts are rounded independently to pennies; exact values determine ranking.");
