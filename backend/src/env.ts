export interface Env {
  DB: D1Database;
  // Local secrets in .dev.vars; absent means scheduled refresh does nothing.
  FUEL_FINDER_CLIENT_ID?: string;
  FUEL_FINDER_CLIENT_SECRET?: string;
  // Routing engine base URL and a label for its road graph (for example its build date).
  ROUTING_BASE_URL?: string;
  ROUTING_GRAPH_VERSION?: string;
  // Comma-separated app keys (each at least 24 characters) accepted on /v1 routes.
  API_KEYS?: string;
}
