-- Set when a station is missing from a complete, validated refresh. Fuel Finder confirmed (6 October 2026)
-- that confirmed closures are removed from the feed at the next update, so a delisted station is not offered.
-- Rows and price history are kept; the column clears if the station reappears.
ALTER TABLE stations ADD COLUMN delisted_at TEXT;
