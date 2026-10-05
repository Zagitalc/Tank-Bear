-- One row per complete refresh. History claims need to know when the feed was actually observed,
-- so a gap in polling is visible rather than mistaken for an unchanged price.
CREATE TABLE refresh_log (
  at TEXT PRIMARY KEY,
  station_count INTEGER NOT NULL,
  priced_count INTEGER NOT NULL
);
CREATE INDEX price_changes_lookup ON price_changes (node_id, feed_fuel_type, price_change_effective);
