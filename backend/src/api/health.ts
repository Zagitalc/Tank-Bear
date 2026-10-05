import type { HealthRepository } from "../repositories/health.ts";

export async function healthResponse(repository: HealthRepository): Promise<Response> {
  const databaseReady = await repository.isAvailable();
  return Response.json(
    {
      service: "tank-bear",
      status: databaseReady ? "ok" : "degraded",
      stage: "skeleton",
      optimisationAvailable: false,
      dependencies: {
        database: databaseReady ? "ok" : "unavailable",
        fuelData: "not_configured",
        routing: "not_configured",
      },
    },
    {
      status: databaseReady ? 200 : 503,
      headers: { "Cache-Control": "no-store" },
    },
  );
}
