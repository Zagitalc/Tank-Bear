# Product roadmap after live deployment

Updated 11 October 2026 following the first phone trial. The server, scheduled
refreshes and Great Britain routing are live. This is the remaining staged work;
registration lookup is now requested for a later stage, after the manual-car flow.

## 1. Clearer interactive map

Prepared locally in this stage:

- Petrol-pump symbols replace station dots; a car symbol marks the entered start.
- Tap pumps or use Previous/Next to select a station; Fit route brings the selected
  routed geometry back into view after panning.
- Selected station panel shows driving fuel cost, fill cost and rounded total.
- Along Journey labels the **extra detour fuel** relative to the direct baseline.
- Fuel Trip labels the whole **there-and-back driving fuel** cost.
- These are backend calculations from the searched MPG, pump price and actual
  routes. No economics are recomputed from straight-line distances on Android.
- Totals are rounded independently; the panel does not assert exact equality of
  separately rounded component prices. Grey baseline text is shown only when a
  baseline exists. Attribution stays inside the unobscured map area.

Build, 19 JVM unit tests and lint passed. This update has not yet been installed
or visually checked on the phone; no ADB device was connected at verification.
Existing live phone tests describe the earlier deployed app, not this new UI.

Combined map/list stage prepared locally:

- The Map tab now shares its space with a draggable, scrollable results panel;
  More results/More map buttons provide an alternative to dragging. The map is
  resized above the panel so its attribution remains visible.
- Pump taps select and scroll to the matching card. Card taps select the pump
  and fit the route. Nearby stations are included, with fill costs explicitly
  excluding driving and straight-line distances labelled as such.
- Persistent sorting controls offer backend-ranked best total, numeric pump
  price and least driving time for routes; Nearby offers closest/cheapest.
  Open now filters both map and list, excluding unknown opening states (and
  temporary closures in Nearby). Backend ranks and costs are preserved.
- Result cards retain price-update age, opening status, history and cost
  explanations. Search entry and the existing list remain available on Find.
- Android build, 20 JVM tests and lint passed; a phone visual/interaction check
  is pending because no ADB device is connected. This stage is not installed,
  committed or deployed. One-way routing and car lookup remain future work.

Location-selection usability stage prepared locally:

- Find offers Choose start/destination on map and Use my location. Raw coordinates
  are behind an optional section. The map picker uses a centre marker and an
  explicit Use this location confirmation, with Cancel leaving inputs unchanged.
- Search stays fixed above navigation on Find and Map. Input changes show Update
  results; previous routes and prices are hidden until refreshed, including when
  inputs change while a request is in flight. Search identity stays in memory.
- Map pump and start-car symbols are enlarged by 20%.
- Place-name/postcode search still needs a chosen geocoding provider. E5/premium
  diesel remain the next fuel-grade stage, not options in this UI update.
- Build, 21 JVM tests and lint passed. Updated instrumentation tests compile but
  could not run because the phone is disconnected. Installation/visual checks
  for this usability update are pending; no commit or push has been made.

Remaining map/list work:

- Validate the combined view on a phone, including dragging, pump/card selection,
  filters, sorting, large text and landscape.
- Show price markers and a selected station detail with fill cost, driving fuel,
  total cost, opening status, price age and feed health kept distinct.
- Add explicit one-way and return-trip options for a dedicated fuel trip.
  Extend backend contracts to expose costs and distances for routed legs; never
  divide a return route in half or double an outbound route. One-way streets and
  different return paths make those shortcuts inaccurate.
- Keep Along Journey's recommendation based on incremental detour cost, while any
  full start-to-station cost is separately labelled. Changing car inputs or trip
  endpoints must invalidate/recalculate the old recommendation and map consistently.
- Add accessible map-independent controls, recenter/start controls, large-text and
  landscape checks, and replace long-press-only discovery with clearer guidance.

## 2. Registration lookup and vehicle confirmation

Intended flow: enter registration, review the matched vehicle, confirm supported
fuel and estimated Imperial MPG, then save locally. Keep manual entry/override.
A lookup estimate is a starting point; a driver's measured MPG may be more accurate.
Show its source, test cycle if supplied, and that traffic, temperature and driving
style change actual consumption. Never claim it is measured MPG for that car.

Research checked 11 October 2026:

- [DVLA VES specification](https://developer-portal.driver-vehicle-licensing.api.gov.uk/apis/vehicle-enquiry-service/v1.2.0-vehicle-enquiry-service.html)
  provides fuel type and basic vehicle details, but no MPG field.
- [DVLA VES guide](https://developer-portal.driver-vehicle-licensing.api.gov.uk/apis/vehicle-enquiry-service/vehicle-enquiry-service-description.html)
  currently says new registrations are closed during upgrades.
- [UK Vehicle Data](https://ukvehicledata.co.uk/dvla-data-api) publishes a technical
  data example with fuel type and combined/urban/extra-urban consumption. This is
  a candidate to evaluate, not a selected or purchased supplier. Confirm package
  coverage, Imperial units, WLTP/NEDC handling, exact vehicle matching, missing-data
  behaviour, pricing and caching/display rights before implementing the adapter.

Implementation gates:

- Confirm a provider and spending limit before a paid subscription or lookups.
  Obtain credentials privately on the server; no supplier key in Android.
- Allow only explicitly identified supported petrol/diesel vehicles. Show an
  unsupported-vehicle message for electric/other fuels; unknown or ambiguous
  results must not silently become petrol or diesel. Hybrid/PHEV records require
  an explicit supported consumption policy; do not use blended electric MPG.
- Fuel type alone does not establish E10 compatibility. Confirm supported grade;
  Tank Bear currently offers E10 and standard diesel B7; E5 and premium diesel
  are planned in the fuel-grade stage below.
- Missing MPG offers manual entry, not an invented efficiency estimate. Distinguish
  unsupported fuel from lookup failure and missing data.
- Send registration only for the user's requested lookup, avoid registration logs,
  return only needed vehicle fields, and document supplier processing/retention.
- Test recognised petrol/diesel, unsupported propulsion, missing MPG, ambiguous
  derivative, bad registration, service failure and user overrides.

## 3. E5 petrol and premium diesel

E5 petrol and premium diesel are now implemented locally alongside E10 petrol
and standard diesel. The live server and installed APK have not been updated
for this stage yet.

- The shared API grade mapping preserves B7 as standard diesel and adds E5 and
  B7_PREMIUM. Feed codes are verified in `fuel-finder-contract.md`; ingestion
  already retains these grades and the database keys prices/history by grade.
- Android's Car selector exposes all four choices. Saved preferences and request
  serialization use distinct codes, with no automatic change to MPG.
- Nearby, history, routed selection and economics use the requested grade only.
  Missing prices stay missing; no standard/premium substitution is permitted.
- Backend typecheck, 185 tests and build passed; Android build, lint and 22 JVM
  tests passed. Tests cover numeric costs, separate history, missing-grade
  handling, all four API choices and preserved standard-diesel mapping.
- Deploy the backward-compatible backend before installing/sharing this APK.
  Verify live searches/history for both added grades after deployment. No schema
  migration or graph rebuild is required. No deployment, commit or push was made
  for this stage.

Acceptance requirements:

- Verify the official Fuel Finder grade identifiers and available price data
  before extending ingestion, API contracts and Android fuel selection. Preserve
  the source's grade distinctions rather than inferring them from station branding.
- Use the selected grade consistently for station availability, map/list prices,
  fill and routed driving costs, rankings and price history. Never substitute a
  standard-grade price when the requested grade is missing.
- Keep vehicle fuel type and chosen grade separate in registration lookup and
  manual setup; do not infer grade compatibility from petrol/diesel alone.
- Keep MPG editable and do not assume an efficiency improvement for premium fuel.
- Test all four choices, missing grade prices, grade-specific history and
  recalculation when the user changes grade.

## 4. Reliability and accuracy before public release

Continue the 15-minute refreshes; seven complete days of collection are required
for meaningful week-long history. Validate continuity rather than relying only on
the date. Add stale-feed alerting, off-VM backups and a restore rehearsal. Verify
proxy error logs do not retain coordinates or keys. Test concurrent routing load,
forecourt access, divided roads and snapping; Great Britain excludes Northern Ireland.

## 5. Android release

Remove server/key setup from normal onboarding; choose production access control
and abuse limits. Decide tile-provider capacity, finish accessibility, offline/error
states, and test mobile data and location-permission cases. Complete privacy wording,
package ID, target SDK, signing and store listing. No accounts, analytics or
background location are part of this roadmap.

See [release readiness](release-readiness.md) for detailed release gates. These
stages should be reviewed separately; this map patch changes no running server.

## 11 October mode-switch / legacy-server regression

The user installed the four-grade APK before the backend deployment. A read-only
check of the live server confirmed its request validator still accepts E10/B7
only, explaining E5 rejection in Fuel Trip as well as Along Journey. Deployment
approval is pending; do not describe E5 as live yet.

Android now clears previous searches and validation errors on mode changes,
resets map selection mode, and propagates coroutine cancellation. Known old-server
grade rejections have a specific server-update message rather than blaming valid
coordinates/MPG. Generic validation failures no longer direct users to alter valid
car details. Build/lint and 23 JVM tests passed; the APK was installed on the phone.
The initial device test passed state assertions but could not inspect a Compose
hierarchy for visual assertions; screen-level verification remains outstanding.
The isolated device state-recovery regression subsequently passed (1 test).

## E5 / premium diesel deployment — 11 October 2026

Deployed after explicit user approval. Saved pre-update code at
`/var/tmp/tankbear-before-fuel-grades.tgz` on the VM, copied the tested backend,
and restarted `tankbear-api`. API and refresh timer remain active. The immediate
startup probe preceded the listener becoming ready; subsequent HTTPS status was
200/ok and every functional check below passed.

Public Reading/Oxford samples (not user journeys), over authenticated HTTPS:

| Grade | Fuel Trip candidates / ms | Along Journey candidates / ms | Nearby priced |
| --- | --- | --- | --- |
| E5 | 12 / 304 | 11 / 448 | 20 |
| Premium diesel | 12 / 152 | 12 / 280 | 15 |
| E10 | 12 / 117 | not repeated | 20 |
| Standard diesel | 12 / 77 | not repeated | 20 |

All four grade-specific history calls returned 200 and one recorded price point;
this is not a week-long trend. Nearby fill objects matched the requested feed
grade. An unauthenticated request returned 401. These are single warm-service
checks, not concurrency or cold-start benchmarks. The database, graph, secrets
and timer configuration were not replaced. Earlier notes that deployment was
pending are historical and superseded by this record. Code remains uncommitted.

## One-way / return fuel-trip stage — prepared locally

- Find now offers One way to the station and There and back from here as distinct
  fuel-trip choices. Along Journey and Nearby retain their existing meanings.
- The backward-compatible `fuel_trip` mode remains a return trip. New
  `fuel_trip_one_way` sends exactly origin → station to the routing provider.
  Both cost the full routed journey against zero travel; no route multiplier,
  halving or doubling is used. Separate outbound/return-leg breakdowns within a
  return result are not included in this stage.
- The response mode controls the list/map cost labels. Mode switches invalidate
  previous searches through the existing recovery logic. Old-server rejection
  explains that one-way support needs a server update.
- Backend typecheck, 187 tests and build passed. Android build, lint and 24 unit
  tests passed. Asymmetric-route tests verify different waypoint lists and costs,
  and Android tests verify mode serialization and result interpretation.
- This stage is not deployed, installed, committed or pushed. Deploy the backend
  before using the new APK; live route checks and phone interaction checks remain.

## One-way deployment — 11 October 2026

Deployed with explicit user approval. Saved rollback code at
`/var/tmp/tankbear-before-one-way.tgz`, copied the backend and restarted the API.
API and refresh timer are active; HTTPS status returned 200/ok.

Public Reading sample, authenticated HTTPS: one-way and return both returned
12 candidates for every grade. One-way/return elapsed milliseconds were E10
277/246, E5 101/131, standard diesel 63/72 and premium diesel 88/103. Response
modes and null fuel-trip baselines were checked. Reading → Oxford Along Journey
also passed with a baseline; an unauthenticated request returned 401. These are
single smoke checks with potentially warm route caches, not load benchmarks.
No user journey was used. No graph rebuild or database migration was needed.
The user's installed APK already exposes the new option. Earlier notes about
pending deployment are superseded; changes remain uncommitted and unpushed.

## Friend-beta stage and one-way withdrawal

User review withdrew the one-way UI option: return fuel trips and Along Journey
are the supported choices, with Nearby for un-routed prices. The backend keeps
one-way compatibility for installed clients. A separately signed, non-debuggable
friend APK now opens on car setup, uses the fixed live hostname, and omits developer
connection fields. No secret is bundled. See [friend-beta.md](friend-beta.md).
The opt-in keyless server mode is prepared and tested but not enabled or deployed;
explicit approval and a fresh-install/live test remain. No commit or push made.

## Friend-beta activation — 11 October 2026

User explicitly approved keyless beta access. Saved rollback code in
`/var/tmp/tankbear-before-public-beta.tgz`, deployed, set PUBLIC_APP_ACCESS=1,
and restarted the API. API and refresh timer active. Live keyless HTTPS checks
from the Mac passed status, Nearby, history and return routes for all four grades,
plus Along Journey, using public Reading/Oxford samples. System curl verified TLS;
the local Python installation lacked a working CA trust store. No phone connected,
so a fresh-install device check remains outstanding. Signed beta APK is ready
at `apps/android/app/build/outputs/apk/beta/app-beta.apk`. No commit/push performed.
