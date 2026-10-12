import assert from "node:assert/strict";
import test from "node:test";
import { geocodingResponse } from "../src/api/geocoding.ts";
import { guard } from "../src/api/guard.ts";
import worker from "../src/index.ts";
import { sqliteD1 } from "./d1-shim.ts";

const request = (query: unknown) => new Request("https://test/v1/places/search", { method: "POST", body: JSON.stringify({ query }) });
const place = { formatted: "Oxford, UK", country_code: "gb", lat: 51.75, lon: -1.25 };
test("geocoding validates input before contacting provider and fails closed without key", async () => {
  const never = (() => { throw new Error("must not call provider"); }) as typeof fetch;
  for (const q of [null, 42, "", "ab", "x".repeat(201), "Oxford\n"]) assert.equal((await geocodingResponse(request(q), "key", never)).status, 400);
  assert.equal((await geocodingResponse(request("Oxford"), undefined, never)).status, 503);
});
test("geocoding fixes provider, UK filter and limit; returns only safe fields and valid UK coordinates", async () => {
  const fake = (async (input: string) => {
    const url = new URL(input);
    assert.equal(url.origin, "https://api.geoapify.com");
    assert.equal(url.searchParams.get("filter"), "countrycode:gb");
    assert.equal(url.searchParams.get("limit"), "5");
    assert.equal(url.searchParams.get("text"), "Oxford & London");
    return Response.json({ results: [place, { ...place, country_code: "us" }, { ...place, lat: 900 }, { ...place, lon: "oops" }] });
  }) as typeof fetch;
  const response = await geocodingResponse(request(" Oxford & London "), "secret", fake);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(await response.json(), { places: [{ label: place.formatted, lat: place.lat, lon: place.lon }] });
});
test("empty, malformed and failed provider responses are distinguished without leaking error bodies", async () => {
  const empty = await geocodingResponse(request("Oxford"), "key", (async () => Response.json({ results: [] })) as typeof fetch);
  assert.deepEqual(await empty.json(), { places: [] });
  for (const response of [Response.json({}), new Response("provider secret", { status: 429 }), Response.json(null)]) {
    const out = await geocodingResponse(request("Oxford"), "key", (async () => response) as typeof fetch);
    assert.equal(out.status, 503);
    assert.equal((await out.text()).includes("secret"), false);
  }
});
test("geocoding gate enforces shared daily budget and fails closed", async () => {
  const limiter = { hit: async (bucket: string) => bucket === "geocoding:all:day" ? 2501 : 1, purgeBefore: async () => {} };
  assert.equal((await guard(request("Oxford"), { PUBLIC_APP_ACCESS: "1" }, limiter, "geocoding"))?.status, 429);
  const broken = { ...limiter, hit: async () => { throw new Error("offline"); } };
  assert.equal((await guard(request("Oxford"), { PUBLIC_APP_ACCESS: "1" }, broken, "geocoding"))?.status, 503);
});
test("place endpoint requires POST, normal auth and server configuration", async () => {
  const { db } = sqliteD1();
  assert.equal((await worker.fetch(new Request("https://test/v1/places/search"), { DB: db })).status, 405);
  assert.equal((await worker.fetch(request("Oxford"), { DB: db })).status, 503);
  const out = await worker.fetch(request("Oxford"), { DB: db, PUBLIC_APP_ACCESS: "1" });
  assert.equal(out.status, 503);
  assert.equal((await out.json() as any).error.code, "GEOCODING_NOT_CONFIGURED");
});
