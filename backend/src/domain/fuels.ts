import type { FuelType } from "./types.ts";

/** Verified Fuel Finder codes; retain the existing B7 API alias for standard diesel. */
export const FEED_FUEL = { E10: "E10", E5: "E5", B7: "B7_STANDARD", B7_PREMIUM: "B7_PREMIUM" } as const;
export function isFuelType(value: unknown): value is FuelType {
  return typeof value === "string" && Object.hasOwn(FEED_FUEL, value);
}
