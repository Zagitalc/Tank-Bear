# Release readiness

Status at 11 October 2026: Stages 0 to 8 are built and run locally. The London VM exists with a reserved
public IP and verified SSH; the application is not deployed or released. See `oracle-instance.md`.
This lists what must be decided or done before anyone other than the developer uses Tank Bear. "Blocker" means do
not release without it. Items marked "check" need a person's decision or an external answer, not code.

## 1. Data rights

Answered by the Fuel Finder Service Team on 6 October 2026 (see `fuel-finder-contract.md`): caching, in-service trends and
history retention are allowed; no raw redistribution or bulk export; "Fuel Finder/ICS" must be credited in the app (done).
Remaining: confirm the exact attribution wording against the onboarding terms, keep the Nearby caps in place, and test
production egress (WAF may restrict non-residential IPs; a static UK IP can be allowlisted on request).

Earlier notes, kept for history:


- **Check:** may Tank Bear cache Fuel Finder data server-side, keep price-change history, and show individual prices next
  to derived journey savings in clients? The guidelines recommend caching and say "don't redistribute raw API data";
  what that means for history retention and client display is unconfirmed. Ask the provider (contact page lists
  fuel.finder@ve3.global); no message has been sent.
- **Check:** attribution wording and any retention limits.
- The app and API never offer a raw feed or export. `/v1/stations/nearby` returns at most 50 stations per call behind an
  app key and rate limits; keep it that way. Review whether even that is acceptable once the terms are known.
- Cloudflare Workers egress was tested on 6 October 2026 and is **not reliable** (shared addresses get locked out); ingestion
  runs from a server with a static UK IP instead (`server-deploy.md`). Whether that address needs an allowlist is untested.

## 2. Hosting and operations (blocker)

- Deploy the Node API and SQLite database on the existing Oracle VM using `server-deploy.md`.
  Startup applies the repository's SQL migrations (currently 0001 to 0005).
- Prove one full refresh from the reserved UK IP before enabling the 15-minute timer; provider allowlisting remains untested.
  Alert when `last_success_at` is older than the 6-hour gate.
- Routing: select an Arm64 Valhalla image digest, build a Great Britain graph on the same VM (Northern Ireland is not
  covered), set an update policy, and measure latency and cost. Each journey search needs one baseline and up to 12 routes.
- Credentials and `API_KEYS` belong in the restricted `/etc/tankbear/tankbear.env` file; Caddy receives a separate
  hostname-only environment file. The routing engine binds only to localhost.
- Verify that no coordinates, keys or bodies are logged anywhere in the production path, including proxy errors.
- Verify SQLite backups and restores, and keep copies off the VM; price history is not reconstructable.

## 3. Access control and abuse (blocker)

- **Why the app has a key today:** the `/v1` API is public once deployed, and each search spends routing capacity and
  exposes station data. The development key (`X-Tank-Bear-Key`) plus rate limits stop casual abuse and let keys rotate.
  It is typed into Settings only because there is no other way to provision it yet; a real user should never see it.
- A key shipped in an Android app can be extracted. Before release replace the typed key with: a key injected at build time
  (not in Git) as a first step, then per-install tokens or Play Integrity attestation so a copied key is not enough.
- Tune the rate limits (10 journeys and 60 lookups per caller per minute, 1500 journeys per hour service-wide) against
  real traffic and routing cost.
- Consider a cost ceiling or kill switch for routing.

## 4. Map and tiles (blocker for public traffic)

- OpenFreeMap has no uptime guarantee. Choose MapTiler (Flex $30 a month at last read), another supported provider, or
  self-hosted PMTiles (check MapLibre Android support). The style address is one constant in `ui/MapConfig.kt`.
- Keep the OpenStreetMap attribution visible (MapLibre draws it; do not cover it).

## 5. Product accuracy

- Opening hours are usual hours only; bank holidays are not applied and "open now" is judged at search time, not arrival.
  Decide whether to estimate arrival time and add bank holidays.
- Savings are estimates. The app marks differences under 20p as within the margin; confirm that threshold.
- Only E10 and standard diesel are supported. E5, premium diesel, HVO and B10 are stored but not offered.
- Northern Ireland, islands and ferry routes: unbenchmarked; routes using ferries or tolls are excluded, not priced.
- The routing benchmark list in `docs/routing.md` (opposite carriageways, divided roads, rural access, snapping) is unchecked.
- History trends need a full week of continuous refreshes before they appear; plan the first-week experience.

## 6. Android release

- Replace the debug-only items: typed server address and key in Settings, cleartext HTTP allowance, `10.0.2.2` default.
- Package name `uk.tankbear.app` is local only; confirm or change before a store listing. Decide target SDK and toolchain
  upgrades (pinned SDK 35 baseline).
- Privacy policy and store data-safety answers: location is read once on tap; saved places stay on the device; searches
  send coordinates to the server, which stores none. Confirm the server really stores none in production.
- Accessibility pass (TalkBack, large text, contrast), including the step chart description.
- Dark map style, offline and error states, and the first-run experience (currently a developer-style Find screen).
- Release signing, ProGuard/R8 review, crash reporting decision (none today; any would need a privacy review).

## 7. UI work already known

The current screens are functional and honest but developer-styled. Expected changes: first-run onboarding replacing the
Settings key and server fields; map and list combined on one screen; Nearby results on the map; a station detail screen
with history and opening hours; saved places as chips; clearer empty, loading and failure states.

## 8. Quality gates before release

- Backend: `npm run check` green (177 tests; see [server verification](server-verification.md)). Add a deployed-environment smoke test.
- Android: unit tests, lint, five instrumentation tests green on a healthy emulator and one real device.
- A real-device run on mobile data, including permission denial and approximate location.
- Re-read `AGENTS.md`: no commit, push, PR, deployment or release without the owner's explicit instruction for that action.
