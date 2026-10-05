-- Fixed-window request counters. Buckets hold a truncated hash of the caller, never an IP address,
-- coordinates or a request body, and are purged by the scheduled job after two hours.
CREATE TABLE rate_limits (
  bucket TEXT NOT NULL,
  window INTEGER NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY (bucket, window)
);
