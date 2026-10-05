import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

// Start `npm run dev` first. This suite only targets the local emulator.
const base = "http://127.0.0.1:8787";
const fixture = JSON.parse(await readFile(
  new URL("../../contracts/fixtures/health-skeleton.json", import.meta.url), "utf8",
));

test("workerd health handler queries the real local D1 binding", async () => {
  const response = await fetch(`${base}/health`, { signal: AbortSignal.timeout(5000) });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(await response.json(), fixture);
});

test("workerd serves HEAD and rejects unsupported endpoints/methods", async () => {
  const head = await fetch(`${base}/health`, { method: "HEAD", signal: AbortSignal.timeout(5000) });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), "");

  const post = await fetch(`${base}/health`, { method: "POST", signal: AbortSignal.timeout(5000) });
  assert.equal(post.status, 405);
  assert.equal(post.headers.get("allow"), "GET, HEAD");

  const missing = await fetch(`${base}/v1/journeys/optimise`, {
    method: "POST", signal: AbortSignal.timeout(5000),
  });
  assert.equal(missing.status, 404);
});
