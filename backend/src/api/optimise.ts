import type { Env } from "../env.ts";
import { optimiseJourney } from "../optimise/service.ts";
import { parseOptimiseRequest } from "../optimise/request.ts";
import type { FuelRepository } from "../repositories/fuel.ts";
import { withRouteCache } from "../routing/cache.ts";
import type { RoutingProvider } from "../routing/types.ts";
import { createValhallaProvider } from "../routing/valhalla.ts";

const headers = { "Cache-Control": "no-store" };
const MAX_BODY_BYTES = 4096;
const error = (status: number, code: string, message: string) =>
  Response.json({ error: { code, message } }, { status, headers });

const providers = new Map<string, RoutingProvider>();
/** One cached provider per isolate and routing config. Returns null when routing is not configured. */
export function routingProvider(env: Pick<Env, "ROUTING_BASE_URL" | "ROUTING_GRAPH_VERSION">): RoutingProvider | null {
  if (!env.ROUTING_BASE_URL || !env.ROUTING_GRAPH_VERSION) return null;
  const key = `${env.ROUTING_BASE_URL}|${env.ROUTING_GRAPH_VERSION}`;
  let provider = providers.get(key);
  if (!provider) {
    provider = withRouteCache(
      createValhallaProvider({ baseUrl: env.ROUTING_BASE_URL, graphVersion: env.ROUTING_GRAPH_VERSION, fetch: (i, init) => fetch(i, init) }),
      { maxEntries: 200, ttlMs: 5 * 60_000 },
    );
    providers.set(key, provider);
  }
  return provider;
}

export async function optimiseResponse(
  request: Request,
  repo: FuelRepository,
  provider: RoutingProvider | null,
  now: () => number = Date.now,
): Promise<Response> {
  if (!provider) return error(503, "ROUTING_NOT_CONFIGURED", "Routing is not configured.");
  if (!(request.headers.get("content-type") ?? "").includes("application/json")) {
    return error(415, "UNSUPPORTED_MEDIA_TYPE", "Send application/json.");
  }
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return error(413, "TOO_LARGE", "Request body is too large.");
  let body: unknown;
  try { body = JSON.parse(text); } catch { return error(400, "INVALID_REQUEST", "Body is not valid JSON."); }
  const parsed = parseOptimiseRequest(body);
  if (!parsed.ok) return error(400, "INVALID_REQUEST", parsed.message);
  try {
    const result = await optimiseJourney({ repo, provider, now }, parsed.value);
    return Response.json(result.body, { status: result.status, headers });
  } catch {
    // Raw errors can carry URLs or coordinates; return nothing specific.
    return error(503, "UNAVAILABLE", "The service is temporarily unavailable.");
  }
}
