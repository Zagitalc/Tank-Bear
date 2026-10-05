-- Fuel Finder current state, observed price changes and refresh health.
-- Prices are pence per litre kept as exact decimal text; compare as numbers only in queries.

CREATE TABLE stations (
  node_id TEXT PRIMARY KEY,
  trading_name TEXT NOT NULL,
  brand_name TEXT,
  postcode TEXT,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  temporary_closure INTEGER NOT NULL,
  permanent_closure INTEGER NOT NULL,
  is_motorway INTEGER NOT NULL,
  is_supermarket INTEGER NOT NULL,
  first_seen_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX stations_position ON stations (latitude, longitude);

CREATE TABLE current_prices (
  node_id TEXT NOT NULL,
  feed_fuel_type TEXT NOT NULL,
  pence_per_litre TEXT NOT NULL,
  price_last_updated TEXT NOT NULL,
  price_change_effective TEXT NOT NULL,
  -- When this value was first observed by a complete refresh. Unchanged prices are
  -- not rewritten on later polls; ingestion_state records when the feed was last checked.
  observed_at TEXT NOT NULL,
  PRIMARY KEY (node_id, feed_fuel_type)
);

CREATE TABLE price_changes (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  node_id TEXT NOT NULL,
  feed_fuel_type TEXT NOT NULL,
  pence_per_litre TEXT NOT NULL,
  price_last_updated TEXT NOT NULL,
  price_change_effective TEXT NOT NULL,
  observed_at TEXT NOT NULL,
  UNIQUE (node_id, feed_fuel_type, price_change_effective, pence_per_litre)
);

CREATE TABLE ingestion_state (
  feed TEXT PRIMARY KEY,
  lease_until TEXT,
  last_attempt_at TEXT,
  last_success_at TEXT,
  last_status TEXT,
  -- Safe, bounded reason text only: never upstream bodies, tokens or credentials.
  last_failure_reasons TEXT,
  station_count INTEGER,
  priced_station_count INTEGER
);
