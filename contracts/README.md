# Shared contracts

The implemented HTTP contract defines only health.
`fixtures/health-skeleton.json` is checked by the backend tests.

Stage 2 adds normalized domain fixtures (not HTTP or official feed schemas):

- `economics-cash-saving.json`: exactly 150p pump saving minus 80p detour fuel.
- `economics-route-ranking.json`: cheapest pump loses on routed overall cost.

Both are exercised by backend tests. `npm run demo:economics` runs the second
fixture locally. 

Future Android, iOS, and web clients consume the same versioned API and economic
fixtures. They do not independently implement the authoritative ranking engine.

## Stage 5: `POST /v1/journeys/optimise` (implemented, unversioned draft)

Request: `mode` (`along_journey` or `fuel_trip`), `origin`, `destination` (journeys
only), `fuelType` (`E10` or `B7`), `vehicle.mpgImperial` and `litresToBuy` as decimal
strings, optional `limits.maxExtraDistanceMetres` / `maxExtraDurationSeconds`. Coordinates
must be inside the UK. Response: `scope` ("Best among the stations checked."), `coverage`
(stations in the search area, routed, not routed with reasons, limit exclusions, partial
flag), baseline route, named `reference`, `labels`, ranked `candidates` with the economics
fields, price provenance, route geometry and snap distance, and `excluded` with reasons.
Errors use `{error:{code,message}}`: 400 invalid, 413/415 body, 422 `NO_ROUTE` /
`JOURNEY_TOO_LONG` / `ROUTE_NEEDS_REVIEW`, 502 `ROUTING_UNAVAILABLE`, 503 `NO_FUEL_DATA` /
`FUEL_DATA_STALE` / `ROUTING_NOT_CONFIGURED`. Responses are `no-store`. No OpenAPI file,
generated client or shared response fixture exists yet; the behaviour is covered by
`backend/tests/optimise.test.ts`.
