import { guard } from "./api/guard.ts";
import { healthResponse } from "./api/health.ts";
import { nearbyResponse } from "./api/nearby.ts";
import { optimiseResponse, routingProvider } from "./api/optimise.ts";
import type { Env } from "./env.ts";
import { createFuelFinderClient } from "./ingestion/client.ts";
import { runRefresh } from "./ingestion/run.ts";
import { createFuelRepository } from "./repositories/fuel.ts";
import { createHealthRepository } from "./repositories/health.ts";
import { createRateLimiter } from "./repositories/ratelimit.ts";

const SECURITY_HEADERS = { "X-Content-Type-Options": "nosniff" };

function withSecurityHeaders(response: Response): Response {
  const out = new Response(response.body, response);
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) out.headers.set(k, v);
  return out;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return withSecurityHeaders(await route(request, env));
  },
  async scheduled(_controller, env): Promise<void> {
    // Counters older than two hours are useless; failure here must not block the refresh.
    await createRateLimiter(env.DB).purgeBefore(Math.floor(Date.now() / 1000) - 7200).catch(() => undefined);
    if (!env.FUEL_FINDER_CLIENT_ID || !env.FUEL_FINDER_CLIENT_SECRET) return;
    const client = createFuelFinderClient(
      { clientId: env.FUEL_FINDER_CLIENT_ID, clientSecret: env.FUEL_FINDER_CLIENT_SECRET },
      { fetch: (input, init) => fetch(input, init), sleep: (ms) => new Promise((r) => setTimeout(r, ms)), now: Date.now },
    );
    const result = await runRefresh(client, createFuelRepository(env.DB));
    // Counts and safe reasons only; never feed bodies or credentials.
    console.log(JSON.stringify({ fuelFinderRefresh: result }));
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/v1/stations/nearby" && (request.method === "GET" || request.method === "HEAD")) {
      const denied = await guard(request, env, createRateLimiter(env.DB), "nearby");
      if (denied) return denied;
      const response = await nearbyResponse(url, createFuelRepository(env.DB));
      return request.method === "HEAD"
        ? new Response(null, { status: response.status, headers: response.headers })
        : response;
    }
    if (url.pathname === "/v1/journeys/optimise") {
      if (request.method !== "POST") {
        return Response.json(
          { error: { code: "METHOD_NOT_ALLOWED", message: "Use POST." } },
          { status: 405, headers: { Allow: "POST", "Cache-Control": "no-store" } },
        );
      }
      const denied = await guard(request, env, createRateLimiter(env.DB), "optimise");
      if (denied) return denied;
      return optimiseResponse(request, createFuelRepository(env.DB), routingProvider(env));
    }
    if (url.pathname !== "/health") {
      return Response.json(
        { error: { code: "NOT_FOUND", message: "Endpoint not found." } },
        { status: 404, headers: { "Cache-Control": "no-store" } },
      );
    }

    if (request.method !== "GET" && request.method !== "HEAD") {
      return Response.json(
        { error: { code: "METHOD_NOT_ALLOWED", message: "Use GET or HEAD." } },
        { status: 405, headers: { Allow: "GET, HEAD", "Cache-Control": "no-store" } },
      );
    }

    const response = await healthResponse(createHealthRepository(env.DB));
    return request.method === "HEAD"
      ? new Response(null, { status: response.status, headers: response.headers })
      : response;
}
