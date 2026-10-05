# Architecture

Status: Stages 0–2 reviewed; Stage 3 authorised, contract/access gate in progress.

## Product boundary

Tank Bear answers which fuel stop has the lowest estimated fill-plus-detour
cost for a specified journey. The decision list comes first; the map explains
the selected routes. Android is first, with separate iOS and web clients later.

```text
Fuel Finder -> scheduled ingestion -> validation -> D1
                                                    |
Android / future clients -> Worker API -> candidate search
                                             |
                                      routing service
                                             |
                                      economic scoring
                                             |
                                 ranked results + geometry
```

One TypeScript Worker owns the API and scheduled handler. D1 holds stations,
current prices, price changes, and ingestion state once Stage 3 begins. A
separately hosted routing engine owns the OSM road graph. No national OSM
dataset is bundled into the app or Worker.

## Boundaries

- `api`: HTTP validation, status codes, response shaping.
- `domain`: pure calculation and ranking functions, no I/O. Exact BigInt
  fractions preserve decimal input precision without a new dependency.
- `repositories`: persistence interfaces and D1 adapters.
- `ingestion` (Stage 3): official upstream contract, validation, idempotency,
  provenance, and feed health.
- `routing` (Stage 4): provider adapter, route normalization, candidate routes.
- Android `ui`: Compose screens. Add domain/data packages, ViewModels,
  coroutines/Flow and DataStore when their first behaviours arrive.
- `contracts`: versioned API definition and shared examples. Backend economics
  remain authoritative for every client.

The HTTP surface still has only the health handler and D1 health repository.
Stage 2 exports the calculation engine as a separate local build artifact and
an offline fixture demo; it does not expose an optimisation API. There is no
cron, optimisation endpoint, business schema, Android HTTP client, or remote
infrastructure. Adding those now would bypass the staged review process.

## Chosen stack

Kotlin, Compose, Material 3; TypeScript, Workers and D1; provisional Valhalla;
MapLibre Native Android in a Compose component. Tile/geocoding providers and
routing hosting remain undecided. Do not adopt a shared cross-platform UI just
because iOS and web directories exist.

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
