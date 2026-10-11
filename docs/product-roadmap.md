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

Next map/list stage:

- Combine map and a scrollable station list/bottom sheet, with selection synchronized
  both ways. Include Nearby results, prices, filters and sorting.
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
  Tank Bear currently offers E10 and standard diesel B7, not E5/premium grades.
- Missing MPG offers manual entry, not an invented efficiency estimate. Distinguish
  unsupported fuel from lookup failure and missing data.
- Send registration only for the user's requested lookup, avoid registration logs,
  return only needed vehicle fields, and document supplier processing/retention.
- Test recognised petrol/diesel, unsupported propulsion, missing MPG, ambiguous
  derivative, bad registration, service failure and user overrides.

## 3. Reliability and accuracy before public release

Continue the 15-minute refreshes; seven complete days of collection are required
for meaningful week-long history. Validate continuity rather than relying only on
the date. Add stale-feed alerting, off-VM backups and a restore rehearsal. Verify
proxy error logs do not retain coordinates or keys. Test concurrent routing load,
forecourt access, divided roads and snapping; Great Britain excludes Northern Ireland.

## 4. Android release

Remove server/key setup from normal onboarding; choose production access control
and abuse limits. Decide tile-provider capacity, finish accessibility, offline/error
states, and test mobile data and location-permission cases. Complete privacy wording,
package ID, target SDK, signing and store listing. No accounts, analytics or
background location are part of this roadmap.

See [release readiness](release-readiness.md) for detailed release gates. These
stages should be reviewed separately; this map patch changes no running server.
