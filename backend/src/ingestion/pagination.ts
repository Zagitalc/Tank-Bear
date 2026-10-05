/**
 * Batch pagination for /api/v1/pfs and /api/v1/pfs/fuel-prices.
 *
 * The official reference documents `batch-number` and "up to 500 forecourts"
 * but no end-of-feed signal. The rule below is observed behaviour (see
 * docs/fuel-finder-contract.md, 5 October 2026): the last real batch holds
 * fewer than 500 records and the following batch returns HTTP 404
 * "Requested batch N is not available". A failed batch is never completion.
 */
export const BATCH_SIZE = 500;
/** Far above the ~17 batches observed; stops a misbehaving feed looping forever. */
export const MAX_BATCHES = 100;

export type BatchOutcome =
  | { kind: "records"; records: readonly unknown[] }
  /** HTTP 404 whose body reports the requested batch as not available. */
  | { kind: "not-available" }
  /** Any other failure: auth, throttling, 5xx, timeout, malformed body. */
  | { kind: "error"; reason: string };

export type PaginationEnd =
  /** Primary, observed: a batch with fewer than 500 records. */
  | "short-batch"
  /**
   * Secondary, never observed: the batch after an exactly-full batch was
   * empty or "not available". Callers must apply the stricter total check.
   */
  | "end-after-full-batch";

export type PaginationResult =
  | { complete: true; end: PaginationEnd; batches: number; records: unknown[] }
  | { complete: false; reason: string; batches: number; records: unknown[] };

export async function collectBatches(
  fetchBatch: (batchNumber: number) => Promise<BatchOutcome>,
): Promise<PaginationResult> {
  const records: unknown[] = [];
  for (let batch = 1; batch <= MAX_BATCHES; batch++) {
    const outcome = await fetchBatch(batch);
    if (outcome.kind === "error") {
      return { complete: false, reason: `batch ${batch} failed: ${outcome.reason}`, batches: batch - 1, records };
    }
    if (outcome.kind === "not-available") {
      // Batch 1 missing means no data at all, not an empty national feed.
      if (batch === 1) return { complete: false, reason: "batch 1 not available", batches: 0, records };
      return { complete: true, end: "end-after-full-batch", batches: batch - 1, records };
    }
    if (outcome.records.length > BATCH_SIZE) {
      return { complete: false, reason: `batch ${batch} exceeded ${BATCH_SIZE} records`, batches: batch, records };
    }
    records.push(...outcome.records);
    if (outcome.records.length === 0) {
      if (batch === 1) return { complete: false, reason: "batch 1 was empty", batches: 1, records };
      return { complete: true, end: "end-after-full-batch", batches: batch - 1, records };
    }
    if (outcome.records.length < BATCH_SIZE) {
      return { complete: true, end: "short-batch", batches: batch, records };
    }
  }
  return { complete: false, reason: `no end signal within ${MAX_BATCHES} batches`, batches: MAX_BATCHES, records };
}
