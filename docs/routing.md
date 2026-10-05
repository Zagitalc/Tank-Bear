# Routing method

Status: planned for Stage 4. No routes are requested in Stage 1.

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
