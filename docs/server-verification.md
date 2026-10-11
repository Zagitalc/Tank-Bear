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

## Limits and next stage

The VM and SSH are verified separately in [oracle-instance.md](oracle-instance.md).
No application deployment or upstream Fuel Finder request was performed in this stage.
Shell syntax and Compose validation do not prove successful Ubuntu installation,
systemd hardening, HTTPS certificates, backup/restore behaviour or operation under load.
The Valhalla image still needs a tested Arm64 digest before deployment, followed by
the Great Britain graph build and real route measurements. Proxy error logs also need
verification for sensitive request data in the deployed environment.

The next stage needs a hostname and server-side credentials, one successful refresh
from the reserved UK address, and only then the continuous refresh timer. History
collection has not started.
