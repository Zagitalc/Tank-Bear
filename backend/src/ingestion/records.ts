import type { FuelType } from "../domain/types.ts";
import type { DayHours, WeekHours } from "../opening/status.ts";

/** Feed fuel codes observed on 5 October 2026. Unknown codes are quarantined. */
export const FEED_FUEL_TYPES = ["E10", "E5", "B7_STANDARD", "B7_PREMIUM", "HVO", "B10"] as const;
export type FeedFuelType = (typeof FEED_FUEL_TYPES)[number];

/** Only grades Tank Bear's economics supports are mapped; others are kept, not guessed. */
export function toDomainFuel(feed: FeedFuelType): FuelType | null {
  if (feed === "E10") return "E10";
  if (feed === "B7_STANDARD") return "B7";
  return null;
}

export interface Station {
  nodeId: string;
  tradingName: string;
  brandName: string | null;
  postcode: string | null;
  latitude: number;
  longitude: number;
  temporaryClosure: boolean;
  permanentClosure: boolean;
  isMotorway: boolean;
  isSupermarket: boolean;
  /** Usual weekly hours, or null if absent or unreadable (never a reason to quarantine a station). */
  openingHours: WeekHours | null;
}

export interface FuelPrice {
  feedFuelType: FeedFuelType;
  fuel: FuelType | null;
  /** Pence per litre as an exact decimal string; the feed sends a JSON number. */
  pencePerLitre: string;
  priceLastUpdated: string;
  priceChangeEffective: string;
}

export interface StationPrices {
  nodeId: string;
  prices: FuelPrice[];
}

export interface Quarantined {
  index: number;
  nodeId: string | null;
  reason: string;
}

export interface Normalised<T> {
  valid: T[];
  quarantined: Quarantined[];
  duplicateNodeIds: string[];
}

/** Allowed clock skew for "future" feed timestamps. Provisional. */
export const FUTURE_TOLERANCE_MS = 5 * 60 * 1000;
/** Plausible pence-per-litre window; observed 100.9 to 299.9. Provisional. */
export const MIN_PENCE = 50;
export const MAX_PENCE = 500;

type Obj = Record<string, unknown>;
const isObj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);
const isNodeId = (v: unknown): v is string => typeof v === "string" && /^[0-9a-f]{64}$/.test(v);

function validTimestamp(value: unknown, now: number): string | null {
  // UTC RFC 3339 with trailing Z, per the fields guide.
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value)) return null;
  const t = Date.parse(value);
  return Number.isNaN(t) || t > now + FUTURE_TOLERANCE_MS ? null : value;
}

function optionalString(v: unknown): string | null {
  return typeof v === "string" && v.trim() !== "" ? v.trim() : null;
}

const DAY_KEYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"] as const;

function readHours(raw: unknown): WeekHours | null {
  const days = (raw as { usual_days?: Record<string, unknown> } | null)?.usual_days;
  if (!isObj(days)) return null;
  const week: DayHours[] = [];
  for (const key of DAY_KEYS) {
    const d = days[key];
    if (!isObj(d) || typeof d.open !== "string" || typeof d.close !== "string") return null;
    if (!/^\d{2}:\d{2}(?::\d{2})?$/.test(d.open) || !/^\d{2}:\d{2}(?::\d{2})?$/.test(d.close)) return null;
    week.push({ open: d.open.slice(0, 5), close: d.close.slice(0, 5), is24Hours: d.is_24_hours === true });
  }
  return week;
}

function normaliseStation(raw: unknown): Station | string {
  if (!isObj(raw)) return "record is not an object";
  if (!isNodeId(raw.node_id)) return "missing or malformed node_id";
  if (typeof raw.trading_name !== "string" || raw.trading_name.trim() === "") return "missing trading_name";
  const loc = raw.location;
  if (!isObj(loc)) return "missing location";
  const { latitude, longitude } = loc;
  if (typeof latitude !== "number" || typeof longitude !== "number") return "non-numeric coordinates";
  // Loose UK-and-islands box; precise CRS is unconfirmed (assumed WGS84).
  if (!(latitude >= 49 && latitude <= 61 && longitude >= -9 && longitude <= 2.5)) return "coordinates outside UK";
  const flag = (v: unknown) => v === true;
  return {
    nodeId: raw.node_id,
    tradingName: raw.trading_name.trim(),
    brandName: optionalString(raw.brand_name),
    postcode: optionalString(loc.postcode),
    latitude,
    longitude,
    temporaryClosure: flag(raw.temporary_closure),
    permanentClosure: flag(raw.permanent_closure),
    isMotorway: flag(raw.is_motorway_service_station),
    isSupermarket: flag(raw.is_supermarket_service_station),
    openingHours: readHours(raw.opening_times),
  };
}

function normalisePrice(raw: unknown, now: number): FuelPrice | string {
  if (!isObj(raw)) return "price is not an object";
  const code = raw.fuel_type;
  if (!(FEED_FUEL_TYPES as readonly unknown[]).includes(code)) return `unknown fuel_type ${String(code).slice(0, 20)}`;
  const price = raw.price;
  if (typeof price !== "number" || !Number.isFinite(price)) return "non-numeric price";
  const text = String(price);
  if (!/^\d{1,3}(?:\.\d{1,3})?$/.test(text)) return "price outside supported format";
  if (price < MIN_PENCE || price > MAX_PENCE) return "price outside plausible range";
  const updated = validTimestamp(raw.price_last_updated, now);
  const effective = validTimestamp(raw.price_change_effective_timestamp, now);
  if (!updated || !effective) return "invalid or future timestamp";
  const feedFuelType = code as FeedFuelType;
  return { feedFuelType, fuel: toDomainFuel(feedFuelType), pencePerLitre: text, priceLastUpdated: updated, priceChangeEffective: effective };
}

function run<T>(raws: readonly unknown[], parse: (raw: unknown) => T | string, key: (v: T) => string): Normalised<T> {
  const valid: T[] = [];
  const quarantined: Quarantined[] = [];
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  raws.forEach((raw, index) => {
    const result = parse(raw);
    const nodeId = isObj(raw) && typeof raw.node_id === "string" ? raw.node_id.slice(0, 64) : null;
    if (typeof result === "string") {
      quarantined.push({ index, nodeId, reason: result });
      return;
    }
    const id = key(result);
    if (seen.has(id)) {
      duplicates.add(id);
      return; // First occurrence wins; the caller treats any duplicate as an unsafe snapshot.
    }
    seen.add(id);
    valid.push(result);
  });
  return { valid, quarantined, duplicateNodeIds: [...duplicates] };
}

export function normaliseStations(raws: readonly unknown[]): Normalised<Station> {
  return run(raws, normaliseStation, (s) => s.nodeId);
}

/** A bad price entry is quarantined individually; the station's good prices are kept. */
export function normalisePrices(raws: readonly unknown[], now: number): Normalised<StationPrices> {
  const extra: Quarantined[] = [];
  const out = run<StationPrices>(
    raws,
    (raw) => {
      if (!isObj(raw)) return "record is not an object";
      if (!isNodeId(raw.node_id)) return "missing or malformed node_id";
      if (!Array.isArray(raw.fuel_prices)) return "fuel_prices is not an array";
      const prices: FuelPrice[] = [];
      const seenFuel = new Set<string>();
      for (const entry of raw.fuel_prices) {
        const p = normalisePrice(entry, now);
        if (typeof p === "string") {
          extra.push({ index: -1, nodeId: raw.node_id, reason: p });
        } else if (seenFuel.has(p.feedFuelType)) {
          extra.push({ index: -1, nodeId: raw.node_id, reason: `duplicate ${p.feedFuelType} price` });
        } else {
          seenFuel.add(p.feedFuelType);
          prices.push(p);
        }
      }
      return { nodeId: raw.node_id, prices };
    },
    (s) => s.nodeId,
  );
  return { ...out, quarantined: [...out.quarantined, ...extra] };
}
