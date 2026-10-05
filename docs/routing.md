# Routing method

Status: Stage 4 adapter built and fixture-tested. No routing engine is hosted, no
live route has been requested, and nothing is exposed over HTTP.

Preferred engine: [Valhalla](https://valhalla.github.io/valhalla/api/route/api-reference/),
subject to a UK access/quality benchmark and a hosting decision. OSRM is the
simple car-routing alternative; OpenRouteService and GraphHopper remain hosted
options subject to quotas, costs and terms. Public demo servers are not the
production architecture.

## Comparable routes

Calculate baseline A -> B and candidate A -> station -> B using the same engine,
graph version, vehicle costing, restrictions and departure-time assumptions.
A station must be a real stop, with all legs summed. Return distance, duration,
geometry, provider, calculation time and graph version when available.

Check snapping and access: a forecourt beside a motorway is not necessarily
reachable from that carriageway. Unknown or excessive snapping, no route,
unexpected ferries/tolls and major detours require explicit exclusion/warnings.
No straight-line fallback may be labelled a routed economic result.

Do not assume fastest-route distance differences cannot be negative. Investigate
material anomalies and agree tolerance/handling before economic integration.
Driving time does not initially include queues or time spent buying fuel.

## Candidate search and budget

1. Validate input and calculate baseline geometry.
2. Query indexed cells overlapping a proposed 3 km corridor and endpoint areas.
3. Precisely filter distance to the polyline, grade, data quality and access.
4. Deduplicate by source identity, preserving separate carriageway forecourts.
5. Shortlist a mix of cheap pumps, small geometric diversions, stations spread
   along the route, endpoint choices and the named reference.
6. Route 8–12 candidates, initially capped at 12 with concurrency 3.
7. Enforce actual distance/time constraints, score and rank successes.

Cold budget: 1 + N routing calls, maximum 13 under the proposed cap. A 3–6 second
response is a target to benchmark, not measured performance. Return explicit
partial coverage if some candidates fail; do not imply a global optimum.

Later, remaining-range filtering must use routed origin-to-station distance.
MPG and tank capacity alone do not establish how much fuel is currently left.

## Cache

Begin with a small bounded short-lived route cache. Key exact endpoints, ordered
stops, profile, restrictions and graph/version/time assumptions. Coarse rounding
can move an origin across a divided road and invalidate detour comparisons.
Cache routes independently of prices and recompute economics using current
observations. Do not persist a user's journey history by default.

## Benchmark cases

Urban/rural routes, opposite motorway services, divided roads, duplicate source
records, Northern Ireland, coastal/island coverage, unexpected ferries, no route,
unreachable stations, huge detours and nearly identical origin/destination.

## Stage 4 implementation (`backend/src/routing/`)

- `types.ts`: `RoutingProvider` port. Outcomes are `ok`, `no_route` (engine says the
  stops cannot be connected) or `error` (timeout, throttling, 5xx, unexpected shape).
- `valhalla.ts`: POST `/route`, `costing: auto`, kilometres, every stop a `break` so
  the station is a real stop with its own leg. Distance becomes whole metres, time whole
  seconds, and ferry/toll flags are required. The request and response mapping follows
  the published route reference. On 5 October 2026 `npm run smoke:routing` ran it against a
  local Valhalla 3.9.0 (the container from the Spirited project, graph dated 2 October, labelled
  `spirited-2026-10-02`): Reading to Oxford baseline 42.5 km / 59 min, a near-route station
  +1.2 km, an off-route one +42 km, and a point at sea returned HTTP 400 code 171 (now mapped to
  `no_route`). This is a smoke check on three stations, not a benchmark; the difficult cases
  below are unchecked. The graph version comes
  from configuration because the route response does not carry one.
- Snapping: the route response does not report snap distance, so the adapter compares
  each requested stop with where the decoded geometry starts or ends. Stations more than
  150 m from the road path (or any stop beyond 300 m) are reported `snap_too_far`. These
  thresholds are provisional and cannot detect a snap to the wrong carriageway that is
  close in distance.
- `journey.ts`: along journey routes A to B once, then A to station to B per station;
  fuel trip routes A to station to A with no baseline. Hard cap 12 stations (13 calls),
  concurrency 3, optional deadline. A baseline failure stops everything. Stations that
  fail, cannot be reached, snap badly or return a different routing context are listed
  in `unrouted` and never costed as zero detours; status is `partial`. No straight-line
  fallback exists.
- `cache.ts`: bounded, short-lived, in memory, keyed by exact full-precision stops and
  routing context. Errors are not cached. Nothing is persisted.
- Ferry and toll flags pass through to the economics engine, which already excludes such
  routes. Negative route differences remain the economics engine's strict hold-for-review
  rule; routing makes no clamping decision.

Not done: candidate shortlisting from D1 (Stage 5), hosting and benchmarking a real
engine, the difficult-case benchmark list above, and the optimisation endpoint.
