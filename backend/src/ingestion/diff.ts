import type { FuelPrice, Station, StationPrices } from "./records.ts";

export interface StoredStation extends Station {
  firstSeenAt: string;
}
export interface StoredPrice {
  nodeId: string;
  feedFuelType: string;
  pencePerLitre: string;
  priceLastUpdated: string;
  priceChangeEffective: string;
}

export interface CurrentState {
  stations: ReadonlyMap<string, Station>;
  prices: ReadonlyMap<string, StoredPrice>; // key: nodeId|feedFuelType
}

export const priceKey = (nodeId: string, fuel: string) => `${nodeId}|${fuel}`;

export interface PriceWrite {
  nodeId: string;
  price: FuelPrice;
}

export interface RefreshDiff {
  stationUpserts: Station[];
  priceUpserts: PriceWrite[];
  /** Observed changes to append to history (includes first observations). */
  priceChanges: PriceWrite[];
  /** Prices the feed no longer lists for a station that is still listed. */
  priceRemovals: { nodeId: string; feedFuelType: string }[];
  skippedOutOfOrder: number;
  unchangedPrices: number;
}

const sameStation = (a: Station, b: Station) =>
  a.tradingName === b.tradingName && a.brandName === b.brandName && a.postcode === b.postcode &&
  a.latitude === b.latitude && a.longitude === b.longitude && a.temporaryClosure === b.temporaryClosure &&
  a.permanentClosure === b.permanentClosure && a.isMotorway === b.isMotorway && a.isSupermarket === b.isSupermarket &&
  JSON.stringify(a.openingHours) === JSON.stringify(b.openingHours);

/**
 * Compares a complete, validated refresh with stored state and returns only the rows that
 * changed, so a 15-minute poll of an unchanged feed writes nothing but refresh health.
 * Stations missing from the feed are untouched here; they are never deleted.
 */
export function diffRefresh(current: CurrentState, stations: readonly Station[], prices: readonly StationPrices[]): RefreshDiff {
  const diff: RefreshDiff = { stationUpserts: [], priceUpserts: [], priceChanges: [], priceRemovals: [], skippedOutOfOrder: 0, unchangedPrices: 0 };

  for (const station of stations) {
    const stored = current.stations.get(station.nodeId);
    if (!stored || !sameStation(stored, station)) diff.stationUpserts.push(station);
  }

  const knownStations = new Set([...current.stations.keys(), ...stations.map((s) => s.nodeId)]);
  const storedByStation = new Map<string, StoredPrice[]>();
  for (const p of current.prices.values()) {
    const list = storedByStation.get(p.nodeId) ?? [];
    list.push(p);
    storedByStation.set(p.nodeId, list);
  }

  for (const entry of prices) {
    if (!knownStations.has(entry.nodeId)) continue; // Orphan price: no station to attach it to.
    const listed = new Set<string>();
    for (const price of entry.prices) {
      listed.add(price.feedFuelType);
      const stored = current.prices.get(priceKey(entry.nodeId, price.feedFuelType));
      if (!stored) {
        diff.priceUpserts.push({ nodeId: entry.nodeId, price });
        diff.priceChanges.push({ nodeId: entry.nodeId, price });
        continue;
      }
      // ISO UTC strings of equal format compare chronologically.
      const newer = price.priceChangeEffective > stored.priceChangeEffective ||
        (price.priceChangeEffective === stored.priceChangeEffective && price.priceLastUpdated > stored.priceLastUpdated);
      if (price.priceChangeEffective < stored.priceChangeEffective) {
        diff.skippedOutOfOrder++;
      } else if (!newer) {
        if (price.pencePerLitre !== stored.pencePerLitre) diff.skippedOutOfOrder++;
        else diff.unchangedPrices++;
      } else {
        diff.priceUpserts.push({ nodeId: entry.nodeId, price });
        if (price.pencePerLitre !== stored.pencePerLitre || price.priceChangeEffective !== stored.priceChangeEffective) {
          diff.priceChanges.push({ nodeId: entry.nodeId, price });
        }
      }
    }
    for (const stored of storedByStation.get(entry.nodeId) ?? []) {
      if (!listed.has(stored.feedFuelType)) diff.priceRemovals.push({ nodeId: entry.nodeId, feedFuelType: stored.feedFuelType });
    }
  }
  return diff;
}
