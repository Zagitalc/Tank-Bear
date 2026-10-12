# Release readiness

Status at 11 October 2026: Stages 0 to 8 are built and run locally. The London VM exists with a reserved
public IP and verified SSH. The API is deployed over HTTPS and scheduled Fuel Finder refreshes
are succeeding. Great Britain routing is deployed and benchmarked; the app is not released. See `oracle-instance.md`.
This lists what must be decided or done before anyone other than the developer uses Tank Bear. "Blocker" means do
not release without it. Items marked "check" need a person's decision or an external answer, not code.

## 1. Data rights

Answered by the Fuel Finder Service Team on 6 October 2026 (see `fuel-finder-contract.md`): caching, in-service trends and
history retention are allowed; no raw redistribution or bulk export; "Fuel Finder/ICS" must be credited in the app (done).
Remaining: confirm the exact attribution wording against the onboarding terms, keep the Nearby caps in place, and test
long-term production egress reliability (the first London refreshes succeeded without an allowlist change).

The API offers no raw-feed export. Keep Nearby caps and abuse limits. Oracle London
refreshes succeeded; Cloudflare shared egress was previously unreliable.

## 2. Hosting and operations (blocker)

- Done: Node API and SQLite deployed on Oracle; HTTPS and authentication verified.
  Startup applies the repository's SQL migrations (currently 0001 to 0005).
- Done: full refresh from the reserved UK IP and subsequent scheduled refreshes succeeded;
  15-minute timer enabled. Remaining: alert when `last_success_at` is older than the 6-hour gate.
- Routing deployed: pinned Arm64 Valhalla 3.9.1, Great Britain graph, and manual update
  policy. Four sample journeys each made 13 calls in 0.43–1.62 seconds; see
  `routing-deployment.md`. Remaining: concurrent load and broader route-quality tests.
  Northern Ireland is not covered.
- Credentials and `API_KEYS` belong in the restricted `/etc/tankbear/tankbear.env` file; Caddy receives a separate
  hostname-only environment file. The routing engine binds only to localhost.
- Verify that no coordinates, keys or bodies are logged anywhere in the production path, including proxy errors.
- Verify SQLite backups and restores, and keep copies off the VM; price history is not reconstructable.

## 3. Access control and abuse (public-release gate)

The user approved a keyless friend beta (`PUBLIC_APP_ACCESS=1`). The signed APK
contains no app key; developer connection fields are hidden. Existing keyed
clients remain compatible. Turning public access off disables keyless clients.

Per-caller minute/day and service-wide limits are enforced; limiter failures deny
requests. Geoapify additionally has a shared daily ceiling. Review the current
limits in `backend/src/api/guard.ts` against measured traffic and capacity before
wider release. No accounts or device identifiers have been added.

## 4. Map and tiles (blocker for public traffic)

- OpenFreeMap has no uptime guarantee. Choose MapTiler (Flex $30 a month at last read), another supported provider, or
  self-hosted PMTiles (check MapLibre Android support). The style address is one constant in `ui/MapConfig.kt`.
- Keep the OpenStreetMap attribution visible (MapLibre draws it; do not cover it).

## 5. Product accuracy

- Opening hours are usual hours only; bank holidays are not applied and "open now" is judged at search time, not arrival.
  Decide whether to estimate arrival time and add bank holidays.
- Savings are estimates. The app marks differences under 20p as within the margin; confirm that threshold.
- E10, E5, standard diesel and premium diesel are live. Other grades are not offered; never substitute a different grade.
- Northern Ireland, islands and ferry routes: unbenchmarked; routes using ferries or tolls are excluded, not priced.
- The routing benchmark list in `docs/routing.md` (opposite carriageways, divided roads, rural access, snapping) is unchecked.
- History trends need a full week of continuous refreshes before they appear; plan the first-week experience.

## 6. Android release

- Signed friend beta uses the fixed HTTPS server, hides developer connection fields and opens on manual car setup. Verify final release network/build settings.
- Package name `uk.tankbear.app` is local only; confirm or change before a store listing. Decide target SDK and toolchain
  upgrades (pinned SDK 35 baseline).
- Privacy policy and store data-safety answers: location is read once on tap; saved places stay on the device; searches
  send coordinates to the server, which stores none. Confirm the server really stores none in production.
- Accessibility pass (TalkBack, large text, contrast), including the step chart description.
- UI beta 0.2.0 includes the approved dark map and screen styling. Automated phone checks pass; full accessibility, fresh-install and failure-case acceptance remains open. See `ui-beta-verification.md`.
- Release signing, ProGuard/R8 review, crash reporting decision (none today; any would need a privacy review).

## 7. Product roadmap

See [current roadmap](product-roadmap.md). Combined map/list, location picking,
place-name/postcode search and clearer search feedback are implemented. The user
accepted the installed Geoapify flow. Registration lookup is deferred; one-way
fuel trips were deliberately removed from the UI. Accessibility and broader
failure-case testing remain.

## 8. Quality gates before public release

- Latest backend typecheck, 195 tests and build passed; live geocoding smoke checks
  passed for town, postcode, ambiguous and empty results.
- Android beta build, 24 JVM tests and lint passed. Prior device test runs and user
  acceptance do not imply exhaustive coverage of the latest build.
- Complete real-device mobile-data, permission, accessibility and failure checks.
- Geoapify search text is sent to the provider on explicit submission; document
  this alongside coordinates sent to Tank Bear in privacy/store disclosures.
- Re-read `AGENTS.md`: commit, push, deployment and release require the owner's
  explicit instruction for that action.
