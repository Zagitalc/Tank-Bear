# Architecture

Status: Oracle server setup selected. The London VM exists and SSH has been verified
(see [instance record](oracle-instance.md)). The Node/SQLite server kit has local
verification; application deployment and real-server application checks remain pending. See [server deployment](server-deploy.md)
for setup and outstanding checks.

## Product boundary

Tank Bear answers which fuel stop has the lowest estimated fill-plus-detour
cost for a specified journey. The decision list comes first; the map explains
the selected routes. Android is first, with separate iOS and web clients later.

```text
Oracle VM (static UK public IP)
  systemd timer (15 min) -> Fuel Finder ingestion -> validation -> SQLite
                                                                    |
  Android / future clients -> HTTPS / Caddy -> Node API -> candidate search
                                                |
                                       Valhalla (Docker)
                                                |
                                         economic scoring
                                                |
                                    ranked results + geometry
```

The selected deployment runs the TypeScript API on Node 24 and stores stations,
current prices, price changes and ingestion state in a local SQLite database.
A separate refresh process uses the same database, triggered by a systemd timer.
Caddy terminates HTTPS and proxies to the API on `127.0.0.1:8787`. Valhalla runs
in Docker on the same VM, owns the road graph, and is reachable only on
`127.0.0.1:8002`. The graph build configuration targets Great Britain; this is not a claim
of complete UK routing coverage. No national OSM dataset is bundled into Android.

The existing Worker entry point and D1-shaped repository interfaces remain
reusable code and local tooling, not the selected production hosting. The Node
host invokes the same HTTP handlers through a SQLite compatibility adapter and
reuses the SQL migrations. SQLite uses WAL mode and a lock timeout; concurrent
API/refresh load still needs real-server verification.

The hosting decision follows the recorded Fuel Finder failures from shared
Cloudflare egress addresses. The Oracle plan provides a dedicated static UK
address, but upstream acceptance of that address remains unverified. See
[hosting decision](hosting.md) and [Oracle setup](oracle-setup.md).

## Boundaries

- `api`: HTTP validation, status codes, response shaping.
- `domain`: pure calculation and ranking functions, no I/O. Exact BigInt
  fractions preserve decimal input precision without a new dependency.
- `repositories`: SQL persistence through the existing D1-compatible interface.
- `backend/server`: Node HTTP host, SQLite adapter/migrations and refresh entry
  point.
- `deploy/oracle`: Caddy configuration, systemd services/timers, backup script
  and Valhalla Docker configuration.
- `ingestion` (Stage 3): official upstream contract, validation, idempotency,
  provenance, and feed health.
- `routing` (Stage 4): provider adapter, route normalization, candidate routes.
- Android `ui`: Compose screens. Add domain/data packages, ViewModels,
  coroutines/Flow and DataStore when their first behaviours arrive.
- `contracts`: versioned API definition and shared examples. Backend economics
  remain authoritative for every client.

The current HTTP handlers include `/health`, `/status`, nearby station search,
station price history and journey optimisation. The Node host reuses their
authentication, rate limiting, validation and backend-owned ranking. Their
presence in the working tree does not establish production readiness; deployment,
upstream access and full graph validation remain review gates.

Fuel Finder credentials stay server-side. Logs must exclude precise journeys
and secrets; the Caddy configuration intentionally enables no access log.
The server kit includes daily SQLite backups, but copies on the same VM do not
protect against losing that VM. Off-machine backup and restore verification
remain operational requirements. The single-VM setup is a single point of failure.

## Chosen stack

Kotlin, Compose, Material 3; TypeScript on Node 24 with SQLite; Oracle VM with
Caddy and systemd; Valhalla in Docker; MapLibre Native Android in a Compose
component. Android currently uses OpenFreeMap. Its lack of an uptime guarantee
means a supported or self-hosted tile fallback must be selected and verified
before release; automatic failover is not claimed. See [data sources](data-sources.md)
and [release readiness](release-readiness.md). Geocoding remains an open choice.
Do not adopt a shared cross-platform UI just because iOS and web directories exist.

The Stage 1 Android toolchain is a compatible pinned SDK 35 baseline, not a
claim to use the latest releases. Upgrade SDK/AGP together before distribution
when current store requirements are reviewed. See `android-setup.md`.

## Mini Reading reference

Inspected the [repository](https://github.com/Zagitalc/mini-reading-3d) and local
`server/providers/fuel.ts`, `shared/fuel-compare.ts`, `shared/feed-policy.ts`,
`worker/poll.ts`, `worker/store.ts`, and feed/Cloudflare documentation.

Reuse concepts: validated adapters, provenance, source/fetch timestamps,
persistent polling leases, backoff, safe feed errors, last-good-data handling.
Do not reuse its straight-line road factor, third-party fuel mirror as the
primary source, local geographic bounds, or unrelated map layers. Tank Bear
has its own code and storage configuration.

## Stages and acceptance gates

1. Skeleton (reviewed): Android build, navigation, backend health, local
   configuration, tests and setup documentation.
2. Economics (reviewed): pure, precisely specified calculations and
   ranking with fixtures. See `calculations.md` for rounding and exclusion rules.
3. Ingestion: verify official contract and terms, then current/history storage,
   validation, refresh health, and nearby queries.
4. Routing: baseline and one candidate first; then filtered candidates and
   failure handling, with difficult access cases checked.
5. Optimisation API: approved request/response schema, bounded candidate search,
   scoring, caching, latency and call-budget verification.
6. Android journey UI: manual vehicle, journey inputs, ranked cards and route map.
7. Usability: foreground location, saved places, opening information and distinct
   Fuel Trip/Nearby behaviours.
8. Price history: coverage-aware trends and local comparisons using observations
   collected from Stage 3.

Each stage reports changed files, test/build results, limitations and the next
review decision. No commit/push/PR/deployment is implied by stage approval.
