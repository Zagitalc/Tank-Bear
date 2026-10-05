import type { BatchOutcome } from "./pagination.ts";

export const FUEL_FINDER_BASE_URL = "https://www.fuel-finder.service.gov.uk";
/** Live quota is 100 requests/minute with one concurrent request; stay near 80. */
export const MIN_REQUEST_GAP_MS = 750;
const MAX_ATTEMPTS = 3;
/**
 * Fuel Finder answers HTTP 403 to requests with no User-Agent (observed 5 October 2026 with curl
 * and from a local Worker, whose fetch sends none). Always identify the client explicitly.
 */
export const USER_AGENT = "TankBear/0.1 (+https://github.com/Zagitalc/Tank-Bear)";

export interface FuelFinderCredentials {
  clientId: string;
  clientSecret: string;
}

export interface ClientDeps {
  fetch: typeof fetch;
  sleep: (ms: number) => Promise<void>;
  now: () => number;
  baseUrl?: string;
}

export class FuelFinderAuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FuelFinderAuthError";
  }
}

const NOT_AVAILABLE = /Requested batch \d+ is not available/;

/** Sequential client: one request at a time, spaced under the documented quota. */
export function createFuelFinderClient(credentials: FuelFinderCredentials, deps: ClientDeps) {
  const base = deps.baseUrl ?? FUEL_FINDER_BASE_URL;
  let token: { value: string; expiresAt: number } | null = null;
  let lastRequestAt = Number.NEGATIVE_INFINITY;

  async function paced(url: string, init: RequestInit): Promise<Response> {
    const wait = lastRequestAt + MIN_REQUEST_GAP_MS - deps.now();
    if (wait > 0) await deps.sleep(wait);
    lastRequestAt = deps.now();
    return deps.fetch(url, init);
  }

  async function accessToken(): Promise<string> {
    // Reuse until a minute before expiry; tokens and secrets are never logged.
    if (token && token.expiresAt - 60_000 > deps.now()) return token.value;
    const response = await paced(`${base}/api/v1/oauth/generate_access_token`, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json", "user-agent": USER_AGENT },
      body: JSON.stringify({ client_id: credentials.clientId, client_secret: credentials.clientSecret }),
    });
    if (!response.ok) throw new FuelFinderAuthError(`token request failed with HTTP ${response.status}`);
    const body: unknown = await response.json().catch(() => null);
    const data = (body as { data?: { access_token?: unknown; expires_in?: unknown } } | null)?.data;
    if (typeof data?.access_token !== "string" || typeof data.expires_in !== "number") {
      throw new FuelFinderAuthError("token response did not match the documented envelope");
    }
    token = { value: data.access_token, expiresAt: deps.now() + data.expires_in * 1000 };
    return token.value;
  }

  async function fetchBatch(path: "/api/v1/pfs" | "/api/v1/pfs/fuel-prices", batch: number): Promise<BatchOutcome> {
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      let response: Response;
      try {
        const bearer = await accessToken();
        response = await paced(`${base}${path}?batch-number=${batch}`, {
          headers: { authorization: `Bearer ${bearer}`, accept: "application/json", "user-agent": USER_AGENT },
        });
      } catch (error) {
        if (error instanceof FuelFinderAuthError) return { kind: "error", reason: error.message };
        if (attempt === MAX_ATTEMPTS) return { kind: "error", reason: "network failure" };
        await deps.sleep(1000 * 2 ** attempt);
        continue;
      }
      if (response.status === 200) {
        const body: unknown = await response.json().catch(() => undefined);
        return Array.isArray(body) ? { kind: "records", records: body } : { kind: "error", reason: "response was not an array" };
      }
      if (response.status === 404) {
        const text = await response.text().catch(() => "");
        return NOT_AVAILABLE.test(text) ? { kind: "not-available" } : { kind: "error", reason: "HTTP 404" };
      }
      if (response.status === 401) token = null; // Expired or revoked; renew once on retry.
      // 429 means another request overlapped or the quota was hit: stop, never hammer.
      const retryable = response.status === 401 || response.status >= 500;
      if (!retryable || attempt === MAX_ATTEMPTS) return { kind: "error", reason: `HTTP ${response.status}` };
      await deps.sleep(1000 * 2 ** attempt);
    }
    return { kind: "error", reason: "retries exhausted" };
  }

  return {
    fetchStationBatch: (batch: number) => fetchBatch("/api/v1/pfs", batch),
    fetchPriceBatch: (batch: number) => fetchBatch("/api/v1/pfs/fuel-prices", batch),
  };
}
