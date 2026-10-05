# Data sources

Status: research only. No upstream data is connected in Stage 1.

## UK Government Fuel Finder

Primary planned source: [official access guidance](https://www.gov.uk/guidance/access-the-latest-fuel-prices-and-forecourt-data-via-api-or-email).
It confirms prices by fuel type, forecourt details, amenities/opening hours,
update timestamps, authenticated OAuth client-credentials API access and
twice-daily CSV availability. Onboarding requires GOV.UK One Login.

The [official developer portal](https://www.developer.fuel-finder.service.gov.uk/public-api)
returned HTTP 403 during Stage 0 research. Exact response fields, coordinate
semantics, grades, price precision, timezone, paging, deltas, deletions, quotas,
licensing/attribution, history retention and redistribution remain unverified.
No unofficial schema is treated as authoritative. Do not create the live adapter
until these details are verified. Retail website scraping is not planned.

## Provenance and freshness

Preserve station/source ID, price, fuel grade, source update time, successful
observation/fetch time, and feed health. An old price-change timestamp is not
proof that a successfully observed unchanged price is stale.

Ingestion must quarantine malformed records, reject invalid coordinates and
future timestamps according to documented tolerances, avoid duplicates and
out-of-order overwrites, and keep the last good snapshot through upstream
failures. Incomplete pagination must not delete missing stations or advance a
cursor as if a refresh completed.

Capture initial prices and subsequent changes from Stage 3. Do not insert an
unchanged historical price at every poll. Retain coverage/observation metadata
so gaps are distinguishable from unchanged prices. Seven-day trends cannot be
claimed before enough observations exist.

## Maps and routing

OSM-derived map data requires source attribution and applicable data-licence
compliance, independent of the application's MIT licence. MapLibre is a
renderer; tile service terms and costs must be selected separately.

See [MapLibre Android](https://maplibre.org/maplibre-native/android/examples/),
[OSM tile policy](https://operations.osmfoundation.org/policies/tiles/) and
[Nominatim policy](https://operations.osmfoundation.org/policies/nominatim/).
Do not use public Nominatim for client autocomplete or treat public OSM tiles
as a guaranteed production service.

Vehicle values are user-entered in the MVP. No registration lookup or invented
tank capacity/MPG is included.
