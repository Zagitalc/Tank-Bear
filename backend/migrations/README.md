# Migrations

`0001_fuel_finder.sql` creates `stations`, `current_prices`, `price_changes` and
`ingestion_state`. Apply locally with `npx wrangler d1 migrations apply
tank-bear-local --local` from `backend/`. No remote database exists and nothing
here has been applied remotely.

- Current rows are written only when a validated complete refresh changes them;
  an unchanged poll writes just `ingestion_state`.
- `price_changes` records first observations and later changes, never one row per
  poll. `ingestion_state.last_success_at` records when the feed was last checked.
- Stations absent from a feed are never deleted. A price the feed stops listing
  for a still-listed station leaves `current_prices` but stays in history.

Later migrations: `0002_rate_limits.sql` (request counters), `0003_opening_hours.sql` (weekly and bank-holiday hours as
JSON), `0004_refresh_log.sql` (one row per complete refresh, for history coverage), `0005_delisting.sql` (stations missing
from a complete refresh are hidden, never deleted).
