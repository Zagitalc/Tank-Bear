import type { Coordinates } from "../domain/types.ts";
import { locateOnLine, type RouteLine } from "./geometry.ts";

export interface PricedStation {
  stationId: string;
  name: string;
  coordinates: Coordinates;
  pencePerLitre: string;
}

export interface Located extends PricedStation {
  offsetMetres: number;
  fraction: number;
}

export const CORRIDOR_METRES = 3000;
export const SEGMENTS = 5;
const CHEAPEST = 4;
const CLOSEST = 3;

const price = (s: Located) => Number(s.pencePerLitre);
const byPrice = (a: Located, b: Located) => price(a) - price(b) || a.offsetMetres - b.offsetMetres || (a.stationId < b.stationId ? -1 : 1);
const byOffset = (a: Located, b: Located) => a.offsetMetres - b.offsetMetres || price(a) - price(b) || (a.stationId < b.stationId ? -1 : 1);

export interface Shortlist {
  /** Routing order: diverse picks first. */
  chosen: Located[];
  inCorridor: number;
}

/**
 * Picks a diverse shortlist from stations inside the corridor. Selecting only the cheapest pumps
 * could discard the best overall stop, so it mixes cheap pumps, stations nearest the route and the
 * cheapest in each fifth of the journey, then tops up by price. Deterministic for equal inputs.
 */
export function shortlist(line: RouteLine, stations: readonly PricedStation[], cap: number, corridorMetres = CORRIDOR_METRES): Shortlist {
  const located: Located[] = [];
  const seen = new Set<string>();
  for (const s of stations) {
    if (seen.has(s.stationId)) continue; // One source identity once; opposite carriageways have different ids.
    seen.add(s.stationId);
    const where = locateOnLine(line, s.coordinates);
    if (where.offsetMetres <= corridorMetres) located.push({ ...s, ...where });
  }
  const chosen: Located[] = [];
  const picked = new Set<string>();
  const take = (list: Located[], n: number) => {
    for (const s of list) {
      if (n <= 0 || chosen.length >= cap) return;
      if (picked.has(s.stationId)) continue;
      picked.add(s.stationId);
      chosen.push(s);
      n--;
    }
  };
  take([...located].sort(byPrice), CHEAPEST);
  take([...located].sort(byOffset), CLOSEST);
  for (let seg = 0; seg < SEGMENTS; seg++) {
    const inSeg = located.filter((s) => Math.min(Math.floor(s.fraction * SEGMENTS), SEGMENTS - 1) === seg);
    take(inSeg.sort(byPrice), 1);
  }
  take([...located].sort(byPrice), cap);
  return { chosen, inCorridor: located.length };
}
