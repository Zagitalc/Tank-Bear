export interface RateLimiter {
  /** Counts one hit in the current window and returns the new count. */
  hit(bucket: string, windowStart: number): Promise<number>;
  purgeBefore(windowStart: number): Promise<void>;
}

export function createRateLimiter(db: D1Database): RateLimiter {
  return {
    async hit(bucket, windowStart) {
      const row = await db.prepare(
        `INSERT INTO rate_limits (bucket, window, count) VALUES (?1, ?2, 1)
         ON CONFLICT(bucket, window) DO UPDATE SET count = count + 1 RETURNING count`,
      ).bind(bucket, windowStart).first<{ count: number }>();
      if (!row) throw new Error("rate limit counter unavailable");
      return row.count;
    },
    async purgeBefore(windowStart) {
      await db.prepare("DELETE FROM rate_limits WHERE window < ?1").bind(windowStart).run();
    },
  };
}
