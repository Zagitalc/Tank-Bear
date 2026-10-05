import type { Coordinates, FuelType } from "../domain/types.ts";

export interface OptimiseRequest {
  mode: "along_journey" | "fuel_trip";
  origin: Coordinates;
  destination?: Coordinates;
  fuelType: FuelType;
  mpgImperial: string;
  litresToBuy: string;
  limits: { maxExtraDistanceMetres?: number; maxExtraDurationSeconds?: number };
}

type Obj = Record<string, unknown>;
const obj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);

function point(v: unknown, name: string): Coordinates | string {
  if (!obj(v) || typeof v.lat !== "number" || typeof v.lon !== "number") return `${name} needs numeric lat and lon.`;
  // Loose UK-and-islands box, same as the feed: the engine and data cover Great Britain first.
  if (!(v.lat >= 49 && v.lat <= 61 && v.lon >= -9 && v.lon <= 2.5)) return `${name} must be inside the United Kingdom.`;
  return { lat: v.lat, lon: v.lon };
}

function decimalIn(v: unknown, name: string, min: number, max: number): string | string[] {
  if (typeof v !== "string" || !/^\d{1,4}(?:\.\d{1,3})?$/.test(v)) return [`${name} must be a plain decimal string.`];
  const n = Number(v);
  return n >= min && n <= max ? v : [`${name} must be between ${min} and ${max}.`];
}

function limit(v: unknown, name: string, max: number): number | undefined | string {
  if (v === undefined) return undefined;
  return typeof v === "number" && Number.isSafeInteger(v) && v > 0 && v <= max ? v : `${name} must be a whole number from 1 to ${max}.`;
}

export function parseOptimiseRequest(body: unknown): { ok: true; value: OptimiseRequest } | { ok: false; message: string } {
  const fail = (message: string) => ({ ok: false as const, message });
  if (!obj(body)) return fail("Body must be a JSON object.");
  if (body.mode !== "along_journey" && body.mode !== "fuel_trip") return fail('mode must be "along_journey" or "fuel_trip".');
  const origin = point(body.origin, "origin");
  if (typeof origin === "string") return fail(origin);
  let destination: Coordinates | undefined;
  if (body.mode === "along_journey") {
    const d = point(body.destination, "destination");
    if (typeof d === "string") return fail(d);
    destination = d;
  } else if (body.destination !== undefined) {
    return fail("fuel_trip returns to the origin and takes no destination.");
  }
  if (body.fuelType !== "E10" && body.fuelType !== "B7") return fail('fuelType must be "E10" or "B7" (standard diesel).');
  if (!obj(body.vehicle)) return fail("vehicle.mpgImperial is required.");
  const mpg = decimalIn(body.vehicle.mpgImperial, "vehicle.mpgImperial", 5, 200);
  if (Array.isArray(mpg)) return fail(mpg[0]!);
  const litres = decimalIn(body.litresToBuy, "litresToBuy", 1, 500);
  if (Array.isArray(litres)) return fail(litres[0]!);
  const limits: OptimiseRequest["limits"] = {};
  if (body.limits !== undefined) {
    if (!obj(body.limits)) return fail("limits must be an object.");
    const dist = limit(body.limits.maxExtraDistanceMetres, "limits.maxExtraDistanceMetres", 200_000);
    const dur = limit(body.limits.maxExtraDurationSeconds, "limits.maxExtraDurationSeconds", 14_400);
    if (typeof dist === "string") return fail(dist);
    if (typeof dur === "string") return fail(dur);
    if (dist !== undefined) limits.maxExtraDistanceMetres = dist;
    if (dur !== undefined) limits.maxExtraDurationSeconds = dur;
  }
  const value: OptimiseRequest = { mode: body.mode, origin, fuelType: body.fuelType, mpgImperial: mpg, litresToBuy: litres, limits };
  if (destination) value.destination = destination;
  return { ok: true, value };
}
