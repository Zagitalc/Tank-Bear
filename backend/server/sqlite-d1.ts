import { mkdirSync, readFileSync, readdirSync } from "node:fs";
import { dirname } from "node:path";
import { URL as NodeURL } from "node:url";
import { DatabaseSync } from "node:sqlite";

/**
 * A D1-compatible database on a local SQLite file, so the same handlers that run on Cloudflare run on a server.
 * Only the D1 surface the application uses is provided: prepare/bind/run/all/first and batch (one transaction).
 * SQLite is synchronous; the async wrappers only match D1's shape.
 */
export interface SqliteD1 {
  db: D1Database;
  sql: DatabaseSync;
  close(): void;
}

const MIGRATIONS = new NodeURL("../migrations/", import.meta.url);

export function openSqliteD1(path: string, options: { migrate?: boolean } = {}): SqliteD1 {
  if (path !== ":memory:") mkdirSync(dirname(path), { recursive: true });
  const sql = new DatabaseSync(path);
  // WAL lets the API read while the refresh job writes; the timeout rides out short write locks.
  if (path !== ":memory:") sql.exec("PRAGMA journal_mode = WAL");
  sql.exec("PRAGMA busy_timeout = 10000");
  sql.exec("PRAGMA foreign_keys = ON");
  if (options.migrate !== false) applyMigrations(sql);

  const prepare = (query: string) => {
    let params: unknown[] = [];
    const run = () => sql.prepare(query).run(...(params as never[]));
    const stmt = {
      bind(...values: unknown[]) { params = values; return stmt; },
      async run() {
        const r = run();
        return { success: true, meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
      },
      // SQLite returns rows with no prototype; D1 returns plain objects, so copy them.
      async all() { return { results: sql.prepare(query).all(...(params as never[])).map((r) => ({ ...r })), success: true }; },
      async first(column?: string) {
        const row = sql.prepare(query).get(...(params as never[])) as Record<string, unknown> | undefined;
        if (!row) return null;
        return column === undefined ? { ...row } : row[column] ?? null;
      },
      // Used by batch() so a failure part-way rolls the whole set back.
      _runSync: run,
    };
    return stmt;
  };

  const db = {
    prepare,
    async batch(statements: Array<{ _runSync?: () => unknown }>) {
      sql.exec("BEGIN IMMEDIATE");
      try {
        const out = statements.map((s) => {
          if (!s._runSync) throw new Error("unsupported statement in batch");
          const r = s._runSync() as { changes: number | bigint };
          return { success: true, meta: { changes: Number(r.changes) } };
        });
        sql.exec("COMMIT");
        return out;
      } catch (e) {
        sql.exec("ROLLBACK");
        throw e;
      }
    },
  } as unknown as D1Database;

  return { db, sql, close: () => sql.close() };
}

/** Applies each migration file once, in name order, remembering what ran. Safe to call on every start. */
export function applyMigrations(sql: DatabaseSync, dir: NodeURL = MIGRATIONS): string[] {
  sql.exec("CREATE TABLE IF NOT EXISTS _migrations (name TEXT PRIMARY KEY, applied_at TEXT NOT NULL)");
  const applied: string[] = [];
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".sql")).sort()) {
    // API and refresh processes can start together. Check after taking the writer
    // lock so the second process sees migrations committed by the first.
    sql.exec("BEGIN IMMEDIATE");
    try {
      if (sql.prepare("SELECT name FROM _migrations WHERE name = ?").get(file)) {
        sql.exec("COMMIT");
        continue;
      }
      sql.exec(readFileSync(new NodeURL(file, dir), "utf8"));
      sql.prepare("INSERT INTO _migrations (name, applied_at) VALUES (?, ?)").run(file, new Date().toISOString());
      sql.exec("COMMIT");
      applied.push(file);
    } catch (e) {
      sql.exec("ROLLBACK");
      throw new Error(`migration ${file} failed`, { cause: e });
    }
  }
  return applied;
}
