import type { CurrentState, RefreshDiff, StoredPrice } from "../ingestion/diff.ts";
import { priceKey } from "../ingestion/diff.ts";
import type { Station } from "../ingestion/records.ts";

export const FEED = "fuel-finder-national";
const CHUNK = 50; // statements per D1 batch

export interface RefreshOutcome {
  at: string;
  status: "complete" | "rejected" | "failed";
  reasons: string[];
  stationCount?: number;
  pricedStationCount?: number;
}

export interface FuelRepository {
  /** Takes the single refresh lease; false means another run is active. */
  acquireLease(now: string, leaseUntil: string): Promise<boolean>;
  releaseLease(): Promise<void>;
  loadCurrent(): Promise<CurrentState>;
  previousCounts(): Promise<{ stationCount: number; priceStationCount: number } | null>;
  /** Applies a validated diff and records success in one ordered set of batches. */
  applyComplete(diff: RefreshDiff, outcome: RefreshOutcome, observedAt: string): Promise<void>;
  /** Records a failed or rejected attempt. Current data is not touched. */
  recordFailure(outcome: RefreshOutcome): Promise<void>;
  nearby(query: NearbyQuery): Promise<NearbyRow[]>;
  feedStatus(): Promise<FeedStatus>;
}

export interface NearbyQuery {
  lat: number;
  lon: number;
  radiusMetres: number;
  limit: number;
}
export interface NearbyRow {
  station: Station;
  distanceMetres: number;
  prices: { feedFuelType: string; pencePerLitre: string; priceLastUpdated: string; priceChangeEffective: string; observedAt: string }[];
}
export interface FeedStatus {
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastStatus: string | null;
}

const flag = (v: unknown) => (v ? 1 : 0);

export function createFuelRepository(db: D1Database): FuelRepository {
  async function runChunks(statements: D1PreparedStatement[]) {
    for (let i = 0; i < statements.length; i += CHUNK) await db.batch(statements.slice(i, i + CHUNK));
  }

  const stateUpdate = (o: RefreshOutcome, success: boolean) =>
    db.prepare(
      `UPDATE ingestion_state SET last_attempt_at = ?1, last_status = ?2, last_failure_reasons = ?3,
         last_success_at = CASE WHEN ?4 THEN ?1 ELSE last_success_at END,
         station_count = CASE WHEN ?4 THEN ?5 ELSE station_count END,
         priced_station_count = CASE WHEN ?4 THEN ?6 ELSE priced_station_count END
       WHERE feed = ?7`,
    ).bind(o.at, o.status, o.reasons.length ? JSON.stringify(o.reasons.slice(0, 10).map((r) => r.slice(0, 200))) : null,
      success ? 1 : 0, o.stationCount ?? null, o.pricedStationCount ?? null, FEED);

  return {
    async acquireLease(now, leaseUntil) {
      await db.prepare("INSERT OR IGNORE INTO ingestion_state (feed) VALUES (?1)").bind(FEED).run();
      const r = await db.prepare(
        "UPDATE ingestion_state SET lease_until = ?1 WHERE feed = ?2 AND (lease_until IS NULL OR lease_until < ?3)",
      ).bind(leaseUntil, FEED, now).run();
      return (r.meta.changes ?? 0) > 0;
    },
    async releaseLease() {
      await db.prepare("UPDATE ingestion_state SET lease_until = NULL WHERE feed = ?1").bind(FEED).run();
    },
    async loadCurrent() {
      const stations = new Map<string, Station>();
      const rows = await db.prepare("SELECT * FROM stations").all<Record<string, unknown>>();
      for (const r of rows.results) stations.set(String(r.node_id), rowToStation(r));
      const prices = new Map<string, StoredPrice>();
      const pr = await db.prepare("SELECT * FROM current_prices").all<Record<string, string>>();
      for (const r of pr.results) {
        prices.set(priceKey(r.node_id!, r.feed_fuel_type!), {
          nodeId: r.node_id!, feedFuelType: r.feed_fuel_type!, pencePerLitre: r.pence_per_litre!,
          priceLastUpdated: r.price_last_updated!, priceChangeEffective: r.price_change_effective!,
        });
      }
      return { stations, prices };
    },
    async previousCounts() {
      const r = await db.prepare("SELECT station_count, priced_station_count FROM ingestion_state WHERE feed = ?1 AND last_success_at IS NOT NULL")
        .bind(FEED).first<{ station_count: number; priced_station_count: number }>();
      return r ? { stationCount: r.station_count, priceStationCount: r.priced_station_count } : null;
    },
    async applyComplete(diff, outcome, observedAt) {
      const st: D1PreparedStatement[] = [];
      for (const s of diff.stationUpserts) {
        st.push(db.prepare(
          `INSERT INTO stations (node_id, trading_name, brand_name, postcode, latitude, longitude, temporary_closure,
             permanent_closure, is_motorway, is_supermarket, first_seen_at, updated_at)
           VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?11)
           ON CONFLICT(node_id) DO UPDATE SET trading_name=?2, brand_name=?3, postcode=?4, latitude=?5, longitude=?6,
             temporary_closure=?7, permanent_closure=?8, is_motorway=?9, is_supermarket=?10, updated_at=?11`,
        ).bind(s.nodeId, s.tradingName, s.brandName, s.postcode, s.latitude, s.longitude, flag(s.temporaryClosure),
          flag(s.permanentClosure), flag(s.isMotorway), flag(s.isSupermarket), observedAt));
      }
      for (const { nodeId, price: p } of diff.priceChanges) {
        st.push(db.prepare(
          `INSERT OR IGNORE INTO price_changes (node_id, feed_fuel_type, pence_per_litre, price_last_updated, price_change_effective, observed_at)
           VALUES (?1,?2,?3,?4,?5,?6)`,
        ).bind(nodeId, p.feedFuelType, p.pencePerLitre, p.priceLastUpdated, p.priceChangeEffective, observedAt));
      }
      for (const { nodeId, price: p } of diff.priceUpserts) {
        st.push(db.prepare(
          `INSERT INTO current_prices (node_id, feed_fuel_type, pence_per_litre, price_last_updated, price_change_effective, observed_at)
           VALUES (?1,?2,?3,?4,?5,?6)
           ON CONFLICT(node_id, feed_fuel_type) DO UPDATE SET pence_per_litre=?3, price_last_updated=?4, price_change_effective=?5, observed_at=?6`,
        ).bind(nodeId, p.feedFuelType, p.pencePerLitre, p.priceLastUpdated, p.priceChangeEffective, observedAt));
      }
      for (const r of diff.priceRemovals) {
        st.push(db.prepare("DELETE FROM current_prices WHERE node_id = ?1 AND feed_fuel_type = ?2").bind(r.nodeId, r.feedFuelType));
      }
      // Health is written last: a crash part-way leaves no false "success" behind, and
      // every statement above is idempotent so the next run repairs it.
      await runChunks(st);
      await stateUpdate(outcome, true).run();
    },
    async recordFailure(outcome) {
      await db.prepare("INSERT OR IGNORE INTO ingestion_state (feed) VALUES (?1)").bind(FEED).run();
      await stateUpdate(outcome, false).run();
    },
    async nearby({ lat, lon, radiusMetres, limit }) {
      const dLat = radiusMetres / 111_320;
      const dLon = radiusMetres / (111_320 * Math.max(Math.cos((lat * Math.PI) / 180), 0.01));
      const rows = await db.prepare(
        `SELECT * FROM stations WHERE permanent_closure = 0 AND latitude BETWEEN ?1 AND ?2 AND longitude BETWEEN ?3 AND ?4`,
      ).bind(lat - dLat, lat + dLat, lon - dLon, lon + dLon).all<Record<string, unknown>>();
      const hits = rows.results
        .map((r) => ({ station: rowToStation(r), distanceMetres: haversine(lat, lon, Number(r.latitude), Number(r.longitude)) }))
        .filter((h) => h.distanceMetres <= radiusMetres)
        .sort((a, b) => a.distanceMetres - b.distanceMetres || (a.station.nodeId < b.station.nodeId ? -1 : 1))
        .slice(0, limit);
      const out: NearbyRow[] = [];
      for (const h of hits) {
        const p = await db.prepare(
          "SELECT feed_fuel_type, pence_per_litre, price_last_updated, price_change_effective, observed_at FROM current_prices WHERE node_id = ?1 ORDER BY feed_fuel_type",
        ).bind(h.station.nodeId).all<Record<string, string>>();
        out.push({
          ...h,
          prices: p.results.map((r) => ({
            feedFuelType: r.feed_fuel_type!, pencePerLitre: r.pence_per_litre!, priceLastUpdated: r.price_last_updated!,
            priceChangeEffective: r.price_change_effective!, observedAt: r.observed_at!,
          })),
        });
      }
      return out;
    },
    async feedStatus() {
      const r = await db.prepare("SELECT last_attempt_at, last_success_at, last_status FROM ingestion_state WHERE feed = ?1")
        .bind(FEED).first<Record<string, string | null>>();
      return { lastAttemptAt: r?.last_attempt_at ?? null, lastSuccessAt: r?.last_success_at ?? null, lastStatus: r?.last_status ?? null };
    },
  };
}

function rowToStation(r: Record<string, unknown>): Station {
  return {
    nodeId: String(r.node_id), tradingName: String(r.trading_name),
    brandName: (r.brand_name as string | null) ?? null, postcode: (r.postcode as string | null) ?? null,
    latitude: Number(r.latitude), longitude: Number(r.longitude),
    temporaryClosure: Boolean(r.temporary_closure), permanentClosure: Boolean(r.permanent_closure),
    isMotorway: Boolean(r.is_motorway), isSupermarket: Boolean(r.is_supermarket),
  };
}

export function haversine(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const rad = Math.PI / 180;
  const a = Math.sin(((lat2 - lat1) * rad) / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(((lon2 - lon1) * rad) / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
}
