import type { RoutingProvider } from "./types.ts";

export interface CacheOptions {
  maxEntries: number;
  ttlMs: number;
  now?: () => number;
}

/**
 * Short-lived, bounded cache of successful and definitive no-route answers. The key is the
 * exact ordered stops at full precision plus the routing context: rounding an endpoint can
 * move it across a divided road. Errors are never cached. Nothing is persisted.
 */
export function withRouteCache(inner: RoutingProvider, { maxEntries, ttlMs, now = Date.now }: CacheOptions): RoutingProvider {
  const entries = new Map<string, { at: number; value: Awaited<ReturnType<RoutingProvider["route"]>> }>();
  return {
    contextId: inner.contextId,
    async route(stops, signal) {
      const key = `${inner.contextId}|${stops.map((s) => `${s.lat},${s.lon}`).join(";")}`;
      const hit = entries.get(key);
      if (hit && now() - hit.at < ttlMs) {
        entries.delete(key);
        entries.set(key, hit); // Refresh recency.
        return hit.value;
      }
      entries.delete(key);
      const value = await inner.route(stops, signal);
      if (value.kind !== "error") {
        entries.set(key, { at: now(), value });
        while (entries.size > maxEntries) entries.delete(entries.keys().next().value!);
      }
      return value;
    },
  };
}
