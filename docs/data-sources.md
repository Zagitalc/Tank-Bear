# Data sources

Status: Stage 3 official contract research started. No upstream data connected.

## UK Government Fuel Finder

Primary planned source: [official access guidance](https://www.gov.uk/guidance/access-the-latest-fuel-prices-and-forecourt-data-via-api-or-email).
It confirms prices by fuel type, forecourt details, amenities/opening hours,
update timestamps, authenticated OAuth client-credentials API access and
twice-daily CSV availability. Onboarding requires GOV.UK One Login.

The [official developer portal](https://www.developer.fuel-finder.service.gov.uk/fuel-finder/public-api)
returned HTTP 403 to the web research tool, but opened in the local browser on
5 October 2026. Official endpoint samples, fields guide, authentication and
developer guidelines were inspected. See [Stage 3 contract findings](fuel-finder-contract.md)
for verified details, contradictory examples and outstanding gates. Live credentials
are configured locally and authentication/first-batch smoke checks passed. The pagination end rule is recorded as observed
evidence (not official) and the adapter is built on it, with guards. Retail website scraping is not planned.

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

## Map tiles (development)

The Android map uses OpenFreeMap (no key, commercial use allowed, attribution required, no SLA as stated
on its site on 5 October 2026) through MapLibre. Spirited uses the same pairing. MapTiler Flex ($30 a month)
or self-hosted PMTiles are the candidate release sources; neither is chosen. Public OSM tile servers
and Esri tiles are not used.
