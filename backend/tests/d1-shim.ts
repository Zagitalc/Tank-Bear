import type { DatabaseSync } from "node:sqlite";
import { openSqliteD1 } from "../server/sqlite-d1.ts";

/** In-memory D1 for tests: the real server adapter with every migration applied. */
export function sqliteD1(): { db: D1Database; sql: DatabaseSync } {
  const { db, sql } = openSqliteD1(":memory:");
  return { db, sql };
}
