/** Prices in thousandths of a penny per litre, so history arithmetic is exact (feed prices have up to 3 decimals). */
export const toThousandths = (pence: string): number => Math.round(Number(pence) * 1000);
export function fromThousandths(value: number): string {
  const sign = value < 0 ? "-" : "";
  const a = Math.abs(value);
  const whole = Math.floor(a / 1000);
  const frac = String(a % 1000).padStart(3, "0").replace(/0+$/, "");
  return `${sign}${whole}${frac ? `.${frac}` : ""}`;
}

export interface ChangeRow {
  effective: string;
  pencePerLitre: string;
  observedAt: string;
}

export const REFRESH_INTERVAL_MINUTES = 15;
export const MIN_COVERAGE = 0.8;

export interface Coverage {
  windowDays: number;
  /** First complete refresh this service ever recorded, or null if none. */
  watchingSince: string | null;
  observedRefreshes: number;
  expectedRefreshes: number;
  /** observed / expected over the time actually watched, 0 to 1. */
  coverage: number;
  /** True only if the whole window was watched with few gaps. */
  sufficientForTrend: boolean;
  reason: string | null;
}

export interface HistoryResult {
  coverage: Coverage;
  /** Step points: the price from this instant until the next point. */
  points: { at: string; pencePerLitre: string }[];
  /** True if the first point is the window start, i.e. the price at the start is known. */
  startKnown: boolean;
  currentPencePerLitre: string | null;
  trend: { changePencePerLitre: string; direction: "up" | "down" | "unchanged"; sincePencePerLitre: string } | null;
}

export function assessCoverage(windowDays: number, now: Date, first: string | null, observed: number): Coverage {
  const base = { windowDays, watchingSince: first, observedRefreshes: observed };
  if (!first) {
    return { ...base, expectedRefreshes: 0, coverage: 0, sufficientForTrend: false, reason: "No complete refresh has been recorded yet." };
  }
  const windowMs = windowDays * 86_400_000;
  const watchedMs = Math.min(windowMs, Math.max(0, now.getTime() - Date.parse(first)));
  const expected = Math.max(1, Math.floor(watchedMs / (REFRESH_INTERVAL_MINUTES * 60_000)));
  const coverage = Math.min(1, observed / expected);
  const watchedDays = watchedMs / 86_400_000;
  if (watchedMs < windowMs) {
    return {
      ...base, expectedRefreshes: expected, coverage, sufficientForTrend: false,
      reason: `Prices have been watched for ${watchedDays.toFixed(1)} of the ${windowDays} days needed.`,
    };
  }
  if (coverage < MIN_COVERAGE) {
    return { ...base, expectedRefreshes: expected, coverage, sufficientForTrend: false, reason: "Too many refreshes were missed in this window to call it a trend." };
  }
  return { ...base, expectedRefreshes: expected, coverage, sufficientForTrend: true, reason: null };
}

/**
 * Builds a step series for the window from recorded price changes (oldest first), including the change
 * in effect at the window start when one is known. A trend is given only when coverage says it can be trusted.
 */
export function buildHistory(rows: readonly ChangeRow[], windowStart: Date, now: Date, coverage: Coverage, current: string | null): HistoryResult {
  const startIso = windowStart.toISOString();
  const before = [...rows].filter((r) => r.effective <= startIso).pop();
  const inside = rows.filter((r) => r.effective > startIso && r.effective <= now.toISOString());
  const points: HistoryResult["points"] = [];
  if (before) points.push({ at: startIso, pencePerLitre: before.pencePerLitre });
  for (const r of inside) points.push({ at: r.effective, pencePerLitre: r.pencePerLitre });
  const startKnown = Boolean(before);
  let trend: HistoryResult["trend"] = null;
  if (coverage.sufficientForTrend && before && current) {
    const diff = toThousandths(current) - toThousandths(before.pencePerLitre);
    trend = {
      changePencePerLitre: fromThousandths(diff),
      direction: diff > 0 ? "up" : diff < 0 ? "down" : "unchanged",
      sincePencePerLitre: before.pencePerLitre,
    };
  }
  return { coverage, points, startKnown, currentPencePerLitre: current, trend };
}

/** Median of thousandths values; for an even count the mean of the middle two, rounded half up. */
export function medianThousandths(values: readonly number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid]! : Math.floor((sorted[mid - 1]! + sorted[mid]! + 1) / 2);
}
