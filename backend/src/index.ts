import { healthResponse } from "./api/health.ts";
import type { Env } from "./env.ts";
import { createHealthRepository } from "./repositories/health.ts";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname !== "/health") {
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
  },
} satisfies ExportedHandler<Env>;
