# Server verification — 11 October 2026

Scope: the Node/SQLite host, refresh entry point, Oracle deployment kit and hosting
documentation prepared on branch `server/oracle-deployment`.

## Local checks

- Node `v24.19.0` on the developer's Mac.
- `npm run check`: TypeScript check, all 177 tests and esbuild build passed.
- `npm run test:runtime`: both checks passed against a running local Wrangler emulator.
- `bash -n deploy/oracle/install.sh deploy/oracle/push.sh deploy/oracle/backup.sh`: passed.
- `docker compose -f deploy/oracle/valhalla/docker-compose.yml config --quiet`: passed.
- `git diff --check`: passed.

HTTP tests require local loopback access. The first sandboxed attempt could not
bind `127.0.0.1`; the full suite passed after enabling local networking. Runtime
checks require `npm run dev` first. Their old unsupported-route assertion targeted
the now-implemented journey endpoint; it now checks `/v1/unknown`.

Server tests exercise migrations, transaction rollback, persistence/WAL, concurrent
migration startup in four processes, HTTP handlers, authentication/rate-limit identity,
request-body limits, log redaction, refresh success/failure and last-good-data retention.
The end-to-end journey test uses a fake routing server for integration only; it does
not validate road routes, route quality or production latency.

## Corrections made during review

- Migrations acquire a write lock before checking whether a migration already ran,
  avoiding duplicate application when API and refresh processes start together.
- HTTP logs use fixed route labels and redact unknown paths and station identifiers.
- Caddy receives a separate hostname-only environment file rather than application
  keys and Fuel Finder credentials.
- The first Caddy package installation is held stopped until configuration is ready;
  the installer documentation now explicitly notes that Docker starts.
- SQLite files are ignored by Git. The deployment runbook proves a refresh before
  enabling the timer and leaves routing for the subsequent stage.

## Oracle deployment checks — 11 October 2026, 12:05 UTC

- Deployed code from `1c38864`; installed Node 24.19.0, Caddy, Docker and SQLite tools.
- Minimal Ubuntu needed `rsync` installed before `push.sh`; `nano` was installed
  for private credential entry. The installer completed successfully.
- API and Caddy are active and enabled at boot; API listens on `127.0.0.1:8787`.
- Oracle ingress rules now allow TCP 80 and 443, alongside existing SSH/ICMP rules.
- `http://tankbear.duckdns.org/status` returns 308 to HTTPS.
- `https://tankbear.duckdns.org/status` returns 200 using normal certificate
  validation, with `status: degraded`, `fuelData: never_loaded` and null refresh timestamps.
- An unauthenticated `/v1/stations/nearby` request returns 401.
- Caddy obtained a Let's Encrypt certificate after the cloud firewall opened.
  Initial certificate attempts before that timed out; Caddy was stopped until
  ingress was approved and applied, then started successfully.
- App key generated privately on the VM. Fuel Finder client ID and secret are
  absent. Environment files are root-owned, mode 0640, with separate service groups.
- Refresh and backup timers are disabled. No upstream refresh has been attempted.

## Live refresh and scheduling — 11 October 2026, 12:12 UTC

- Owner entered Fuel Finder credentials privately; presence was checked without
  displaying values. One refresh ran from the London VM and completed successfully
  in approximately 34 seconds, without an allowlist change.
- Summary: 8,131 stations, 8,127 priced stations, 26,490 price writes, no delistings.
- Public HTTPS status reports `status: ok`, `fuelData: ok`, and last successful
  refresh `2026-10-11T12:11:36.125Z` (the run start timestamp).
- Enabled `tankbear-refresh.timer` (every 15 minutes, up to 30 seconds jitter) and
  `tankbear-backup.timer` (03:30 UTC daily). First scheduled refresh is due 12:15 UTC.
- First backup service succeeded, writing `tankbear-20261011T121225Z.sqlite`;
  read-only SQLite `PRAGMA integrity_check` returned `ok`.

## Limits and next stage

History collection has started; week-long trends require a full week of continuous
refreshes. Scheduled refreshes at 12:15, 12:30 and 12:45 UTC succeeded, including during the graph build. Off-machine backup and
restore rehearsal, concurrent production load and proxy error-log redaction remain
unverified. Valhalla is now pinned, deployed and measured; see
[routing deployment](routing-deployment.md) for build memory, latency, restart and phone checks.
