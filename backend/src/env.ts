export interface Env {
  DB: D1Database;
  // Local secrets in .dev.vars; absent means scheduled refresh does nothing.
  FUEL_FINDER_CLIENT_ID?: string;
  FUEL_FINDER_CLIENT_SECRET?: string;
}
