import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { URL as NodeURL } from "node:url";
import test from "node:test";
import worker from "../src/index.ts";
import type { Env } from "../src/env.ts";

function database(first: () => Promise<unknown>): Env {
  // Only the D1 surface the health repository actually uses is stubbed.
  return {
    DB: {
      prepare(sql: string) {
        assert.equal(sql, "SELECT 1 AS ok");
        return { first };
      },
    } as unknown as D1Database,
  };
}

const healthy = () => database(async () => ({ ok: 1 }));
const request = (path = "/health", method = "GET") =>
  new Request(`https://tank-bear.test${path}`, { method });

test("healthy database does not claim fuel or optimisation is available", async () => {
  const response = await worker.fetch(request(), healthy());
  const fixture = JSON.parse(await readFile(
    new NodeURL("../../contracts/fixtures/health-skeleton.json", import.meta.url), "utf8",
  ));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.match(response.headers.get("content-type") ?? "", /application\/json/);
  assert.deepEqual(await response.json(), fixture);
});

test("database failure is 503 and does not leak the provider error", async () => {
  const response = await worker.fetch(request(), database(async () => {
    throw new Error("private database URL and secret");
  }));
  assert.equal(response.status, 503);
  const body = await response.text();
  assert.doesNotMatch(body, /private|secret/);
  assert.equal(JSON.parse(body).dependencies.database, "unavailable");
});

test("empty database probe is unavailable", async () => {
  const response = await worker.fetch(request(), database(async () => null));
  assert.equal(response.status, 503);
});

test("HEAD preserves readiness status and has no response body", async () => {
  for (const [env, status] of [[healthy(), 200], [database(async () => null), 503]] as const) {
    const response = await worker.fetch(request("/health", "HEAD"), env);
    assert.equal(response.status, status);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal(await response.text(), "");
  }
});

test("unsupported methods and unknown routes do not access the database", async () => {
  const env = { get DB(): D1Database { throw new Error("Unexpected database access"); } };
  const method = await worker.fetch(request("/health", "POST"), env);
  assert.equal(method.status, 405);
  assert.equal(method.headers.get("allow"), "GET, HEAD");
  const missing = await worker.fetch(request("/v1/prices", "POST"), env);
  assert.equal(missing.status, 404);
});
