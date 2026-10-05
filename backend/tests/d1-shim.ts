import { readFileSync } from "node:fs";
import { URL as NodeURL } from "node:url";
import { DatabaseSync } from "node:sqlite";

/** Minimal D1 surface over node:sqlite, running the real migration SQL. */
export function sqliteD1(): { db: D1Database; sql: DatabaseSync } {
  const sql = new DatabaseSync(":memory:");
  sql.exec(readFileSync(new NodeURL("../migrations/0001_fuel_finder.sql", import.meta.url), "utf8"));
  const prepare = (query: string) => {
    let params: unknown[] = [];
    const stmt: Record<string, unknown> = {
      bind(...values: unknown[]) { params = values; return stmt; },
      async run() {
        const r = sql.prepare(query).run(...(params as never[]));
        return { success: true, meta: { changes: Number(r.changes) } };
      },
      async all() { return { results: sql.prepare(query).all(...(params as never[])) }; },
      async first() { return sql.prepare(query).get(...(params as never[])) ?? null; },
    };
    return stmt;
  };
  const db = {
    prepare,
    async batch(statements: Array<{ run(): Promise<unknown> }>) {
      sql.exec("BEGIN");
      try {
        const out = [];
        for (const s of statements) out.push(await s.run());
        sql.exec("COMMIT");
        return out;
      } catch (e) { sql.exec("ROLLBACK"); throw e; }
    },
  } as unknown as D1Database;
  return { db, sql };
}
