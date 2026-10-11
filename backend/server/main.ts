// Runs the Tank Bear API on a normal server: Node, a SQLite file, and the same handlers as the Worker.
// Environment: DATABASE_PATH, API_KEYS, ROUTING_BASE_URL, ROUTING_GRAPH_VERSION, PORT, HOST, TRUST_PROXY.
import { createServer } from "node:http";
import worker from "../src/index.ts";
import type { Env } from "../src/env.ts";
import { createNodeHandler } from "./http.ts";
import { openSqliteD1 } from "./sqlite-d1.ts";

const e = process.env;
const databasePath = e.DATABASE_PATH ?? "./tankbear.sqlite";
const host = e.HOST ?? "127.0.0.1";
const port = Number(e.PORT ?? 8787);

const { db, close } = openSqliteD1(databasePath);
const env: Env = {
  DB: db,
  ...(e.API_KEYS ? { API_KEYS: e.API_KEYS } : {}),
  ...(e.ROUTING_BASE_URL ? { ROUTING_BASE_URL: e.ROUTING_BASE_URL } : {}),
  ...(e.ROUTING_GRAPH_VERSION ? { ROUTING_GRAPH_VERSION: e.ROUTING_GRAPH_VERSION } : {}),
};
if (!e.API_KEYS) console.warn("API_KEYS is not set: /v1 routes will answer 503 until it is.");

const server = createServer(createNodeHandler(worker, env, {
  trustProxy: e.TRUST_PROXY === "1",
  maxBodyBytes: 16 * 1024,
  log: (line) => console.log(line),
}));
server.requestTimeout = 60_000;
server.headersTimeout = 15_000;
server.listen(port, host, () => console.log(`tank-bear api listening on ${host}:${port}`));

function stop() {
  server.close(() => { close(); process.exit(0); });
  setTimeout(() => process.exit(1), 10_000).unref();
}
process.on("SIGTERM", stop);
process.on("SIGINT", stop);
