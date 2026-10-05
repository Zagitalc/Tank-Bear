-- Usual weekly opening hours as compact JSON (Monday first): [{"o":"06:00","c":"22:00","h":0}, ...].
-- Null when the feed gave none or they could not be read. Bank-holiday hours are not stored.
ALTER TABLE stations ADD COLUMN opening_hours TEXT;
