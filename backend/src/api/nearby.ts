import type { FuelRepository, NearbyRow } from "../repositories/fuel.ts";

export const MAX_RADIUS_METRES = 25_000;
export const DEFAULT_RADIUS_METRES = 5_000;
export const MAX_LIMIT = 50;
export const DEFAULT_LIMIT = 20;

const headers = { "Cache-Control": "no-store" };
const bad = (message: string) => Response.json({ error: { code: "INVALID_REQUEST", message } }, { status: 400, headers });

function number(params: URLSearchParams, name: string): number | null {
  const v = params.get(name);
  return v !== null && /^-?\d{1,3}(?:\.\d{1,7})?$/.test(v) ? Number(v) : null;
}
function int(params: URLSearchParams, name: string, fallback: number): number | null {
  const v = params.get(name);
  return v === null ? fallback : /^\d{1,6}$/.test(v) ? Number(v) : null;
}

export function shapeNearby(rows: NearbyRow[]) {
  return rows.map((r) => ({
    id: r.station.nodeId,
    name: r.station.tradingName,
    brand: r.station.brandName,
    postcode: r.station.postcode,
    position: { lat: r.station.latitude, lon: r.station.longitude },
    distanceMetres: Math.round(r.distanceMetres),
    // Closure is reported, not hidden: a feed entry is not proof the station is open.
    temporaryClosure: r.station.temporaryClosure,
    isMotorway: r.station.isMotorway,
    prices: r.prices.map((p) => ({
      feedFuelType: p.feedFuelType,
      pencePerLitre: p.pencePerLitre,
      priceLastUpdated: p.priceLastUpdated,
      priceChangeEffective: p.priceChangeEffective,
      firstObservedAt: p.observedAt,
    })),
  }));
}

export async function nearbyResponse(url: URL, repo: FuelRepository): Promise<Response> {
  const lat = number(url.searchParams, "lat");
  const lon = number(url.searchParams, "lon");
  if (lat === null || lon === null || lat < 49 || lat > 61 || lon < -9 || lon > 2.5) {
    return bad("lat and lon are required decimal degrees inside the United Kingdom.");
  }
  const radius = int(url.searchParams, "radiusMetres", DEFAULT_RADIUS_METRES);
  const limit = int(url.searchParams, "limit", DEFAULT_LIMIT);
  if (radius === null || radius < 100 || radius > MAX_RADIUS_METRES) return bad(`radiusMetres must be 100 to ${MAX_RADIUS_METRES}.`);
  if (limit === null || limit < 1 || limit > MAX_LIMIT) return bad(`limit must be 1 to ${MAX_LIMIT}.`);
  try {
    const [rows, status] = await Promise.all([repo.nearby({ lat, lon, radiusMetres: radius, limit }), repo.feedStatus()]);
    return Response.json(
      {
        // Feed health is separate from any price's own age.
        feed: { source: "Fuel Finder", lastCheckedAt: status.lastAttemptAt, lastSuccessfulRefreshAt: status.lastSuccessAt, lastStatus: status.lastStatus },
        stations: shapeNearby(rows),
      },
      { headers },
    );
  } catch {
    return Response.json({ error: { code: "UNAVAILABLE", message: "Station data is temporarily unavailable." } }, { status: 503, headers });
  }
}
