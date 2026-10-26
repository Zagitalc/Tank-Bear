# Tank Bear roadmap

Updated 11 October 2026 after the user accepted place-name/postcode search on the
phone. This is the current plan, replacing the earlier chronological stage notes.
Deployment details remain in the linked operational documents. Completed work
below does not imply all public-release checks have passed.

## Current status

Tank Bear is a working Android friend beta backed by the Oracle London server.
It is not a Play Store release. The API, Great Britain routing and 15-minute
Fuel Finder refreshes are live at https://tankbear.duckdns.org.

| Area | Delivered |
| --- | --- |
| Hosting | Node/SQLite API over HTTPS; private Valhalla routing; scheduled refreshes from the UK VM |
| Fuel grades | E10, E5, standard diesel and premium diesel, with separate prices/history and no grade substitution |
| Search modes | Along Journey uses actual extra detour cost; Fuel Trip uses the actual return journey; Nearby explicitly excludes driving costs |
| Map and list | Combined view, linked pump/card selection, sorting, Open now filter, fit route, larger pump/car symbols and fill-plus-driving cost explanations |
| Location entry | Foreground location on request, saved places, explicit map picker and optional coordinates |
| Place search | Geoapify name/postcode lookup for start/destination, selectable results and map confirmation; credentials stay server-side |
| Search feedback | Find-only results scrolling, missing-location/car correction actions, retry and empty-result guidance |
| Friend installation | Signed Tank Bear Beta APK, fixed HTTPS server, no typed key or server address; manual car setup |

The user reviewed the installed Geoapify flow and reported it working. Live API
checks passed for a town, postcode, ambiguous name and no match. Latest checks:
195 backend tests plus typecheck/build; 24 Android JVM tests, beta build and lint.
These do not replace the remaining device, accessibility and reliability checks.

The one-way fuel-trip option was deliberately removed at the user's request.
It is not planned for reintroduction; backend support remains for older clients.

## 1. Next: reliability and recovery

Prepare and review this stage before changing live operations:

- Add stale-feed and failed-refresh alerts, based on actual refresh timestamps.
  Confirm delivery and recovery notifications with a controlled failure test.
- Verify the existing on-VM SQLite backups. Choose an off-VM destination,
  retention and access policy, then automate copies.
- Restore a backup into an isolated database and verify station data and price
  history without overwriting production. Document a repeatable recovery process.
- Check production application/proxy error paths for accidental coordinates,
  registration/address queries, keys or response-body logging.
- Verify seven complete days of refresh continuity before presenting week-long
  trends. Collection began 11 October; 18 October is only the earliest candidate,
  not proof that sufficient uninterrupted history exists.

Acceptance: a delivered test alert, a verified off-VM backup, a successful isolated
restore, and documented refresh continuity. No monitoring service or new backup
location has been selected yet.

## 2. Route accuracy and capacity

- Test forecourt entrances, opposite carriageways, divided roads, rural access
  and snapping using documented public examples.
- Measure concurrent searches and cold/warm response times while refreshes run.
  Existing single-request benchmarks are not load-test evidence.
- Confirm clear behaviour for unsupported areas and excluded ferry/toll routes.
  The current graph covers Great Britain, not Northern Ireland.
- Review opening-hours wording: Open now is evaluated at search time, not arrival;
  ordinary opening hours do not guarantee bank-holiday hours.
- Keep costs based on actual routed distances and the selected fuel/MPG.

Acceptance: recorded route cases, latency/resource measurements, and actionable
failure messages without invented routes or savings.

## 3. Friend-beta polish and broader phone testing

- Test fresh installation through a shared APK, upgrade preserving settings,
  mobile data, denied/approximate location, offline use and server/provider errors.
- Check TalkBack, larger text, landscape, map/list controls and attribution.
- Verify Find auto-scroll, missing destination recovery, empty filters, and place
  selection/cancel across mode changes. User acceptance covers the tried flow,
  not every edge case.
- Increment versionCode for newly distributed builds and preserve the signing key
  in a private backup. Decide a simple APK download/update process; no automatic
  update channel or public release has been created.

Acceptance: a repeatable phone checklist and a clearly versioned, signed APK.

## 4. Public-release preparation

- Review tile-provider terms/capacity and Geoapify quota use; retain map selection
  when place search is unavailable. Keep required attribution visible.
- Tune public API abuse limits against measured load. The friend beta is explicitly
  keyless and rate-limited; an APK is not access control. No secret belongs in it.
- Complete privacy wording, including Geoapify query processing, and store
  data-safety declarations. No accounts, analytics or background location planned.
- Confirm package ID, target SDK, signing/recovery, release build configuration
  and store assets before any Play Store submission.

Acceptance: the outstanding gates in [release readiness](release-readiness.md)
are satisfied and the user explicitly authorizes release.

## Deferred: registration lookup and estimated MPG

Deferred by the user on 11 October 2026. Keep manual fuel-grade and Imperial MPG
entry. Do not start signup, purchasing or integration unless this feature resumes.

When resumed, select a supplier and explicit budget first. DVLA VES lacks MPG and
currently reports new registrations closed. UK Vehicle Data is a candidate, not
an approved supplier: its published entry PAYGO tier is £50 credit with 15p UK
vehicle lookups excluding VAT. Confirm consumption coverage, Imperial units,
WLTP/NEDC provenance, matching quality and retention/display rights before use.
Its trial must not power the public friend beta.

Required behaviour: user confirms the vehicle, fuel grade and editable estimated
MPG; reject unsupported propulsion, distinguish unknown/missing data from failure,
and never infer E10 compatibility from petrol alone. Hybrid/PHEV consumption needs
an explicit policy. No fabricated MPG and no registration logs.

Sources checked 11 October 2026:
- [DVLA VES](https://developer-portal.driver-vehicle-licensing.api.gov.uk/apis/vehicle-enquiry-service/vehicle-enquiry-service-description.html)
- [UK Vehicle Data fields/pricing](https://www.ukvehicledata.co.uk/dvla-data-api)
- [UK Vehicle Data usage terms](https://www.ukvehicledata.co.uk/policies/WebsiteTerms)

## Operational references and delivery rules

- [Server deployment](server-deploy.md), [routing deployment](routing-deployment.md)
  and [Oracle instance](oracle-instance.md)
- [Friend beta](friend-beta.md) and [release readiness](release-readiness.md)
- Geoapify rollback code: `/var/tmp/tankbear-before-geocoding.tgz` on the VM.
  The key remains in the restricted server environment file.

Work in small stages and stop for review. Updating this roadmap does not authorize
commit, push, deployment, purchases, messages to providers or publication; each
requires the user's applicable explicit instruction.
