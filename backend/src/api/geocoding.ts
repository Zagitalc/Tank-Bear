/** Geoapify forward geocoding. Never log queries, provider URLs, keys or responses. */
export async function geocodingResponse(request: Request, key: string | undefined, fetcher: typeof fetch = fetch): Promise<Response> {
  const headers = { "Cache-Control": "no-store" };
  const fail = (status: number, code: string) => Response.json({ error: { code } }, { status, headers });
  let query: unknown;
  try {
    const text = await request.text();
    if (text.length > 2048) return fail(400, "INVALID_REQUEST");
    query = (JSON.parse(text) as { query?: unknown })?.query;
  } catch { return fail(400, "INVALID_REQUEST"); }
  if (typeof query !== "string" || query.trim().length < 3 || query.length > 200 || /[\u0000-\u001f]/.test(query)) return fail(400, "INVALID_REQUEST");
  if (!key?.trim()) return fail(503, "GEOCODING_NOT_CONFIGURED");
  const url = new URL("https://api.geoapify.com/v1/geocode/search");
  url.search = new URLSearchParams({ text: query.trim(), filter: "countrycode:gb", format: "json", lang: "en", limit: "5", apiKey: key }).toString();
  try {
    const response = await fetcher(url.toString(), { signal: AbortSignal.timeout(8000), redirect: "error" });
    if (!response.ok) return fail(503, "GEOCODING_UNAVAILABLE");
    const body = await response.json() as { results?: unknown };
    if (!Array.isArray(body.results)) return fail(503, "GEOCODING_UNAVAILABLE");
    const places = body.results.slice(0, 5).flatMap((r) => {
      if (!r || typeof r !== "object" || r.country_code !== "gb" || typeof r.formatted !== "string" ||
          !r.formatted.trim() || r.formatted.length > 500 || typeof r.lat !== "number" || typeof r.lon !== "number" ||
          !Number.isFinite(r.lat) || !Number.isFinite(r.lon) || r.lat < 49 || r.lat > 61 || r.lon < -9 || r.lon > 2.5) return [];
      return [{ label: r.formatted, lat: r.lat, lon: r.lon }];
    });
    return Response.json({ places }, { headers });
  } catch { return fail(503, "GEOCODING_UNAVAILABLE"); }
}
