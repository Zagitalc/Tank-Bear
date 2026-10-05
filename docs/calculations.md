# Calculation specification

Status: Stage 2 implemented for review in `backend/src/domain/economics.ts`.
No upstream feed, router, optimisation endpoint or Android integration exists.

Run `npm run demo:economics` for an offline fixture comparison and `npm run check`
for the calculation and existing health tests.

## Units

1 Imperial gallon = 4.54609 litres. 1 mile = 1609.344 metres.
All MPG is Imperial, never US MPG. Routes use metres and seconds internally.
Prices are pence/litre, preserving decimal source precision. Inputs such as
`"169.9"` must not be rounded to whole pence per litre.

The engine parses bounded plain decimal strings into BigInt fractions. All
conversion, costs, subtraction and ranking remain exact; division is not rounded
before money is calculated. No third-party arithmetic dependency is needed.
Scientific notation, commas, numeric JS values and more than 9 fractional/12
whole digits are rejected. Leading zeroes are accepted and normalized.

Monetary results are integer pence, rounded half away from zero (0.5p -> 1p,
-0.5p -> -1p). Unsafe integer-pence totals are excluded. Non-money result strings
are rounded to at most 9 decimal places as transport values, never fed back into
the calculation. A client should display sensible miles/minutes/litres rather
than that full transport precision.

## Along My Journey

For candidate i:

```text
detour metres = distance(A -> i -> B) - distance(A -> B)
detour seconds = duration(A -> i -> B) - duration(A -> B)
detour miles = detour metres / 1609.344
detour litres = detour miles / mpgImperial * 4.54609
detour cost pence = detour litres * candidate price pence/litre
fill cost pence = litresToBuy * candidate price pence/litre
comparison cost = fill cost + detour cost
```

Valuing consumed fuel at the candidate's price is an explicit replacement-cost
assumption, not knowledge of the price paid for fuel already in the tank.

Rank ascending exact comparison cost; tie-break by extra driving time then a
stable station ID using deterministic code-unit order. Input arrays and records
are not mutated. The cheapest-pump label uses exact price, then economic order;
smallest detour means least added driving time, then distance, then station ID.
Money results are estimates based on the MPG and route model.

## Reference and savings

The reference is a real, named eligible station, by default the smallest added
driving time among stations successfully checked. Do not call it the nearest
station in Britain or use a national average implicitly.

```text
pump saving(i) = fill cost(reference) - fill cost(i)
true saving(i) = comparison cost(reference) - comparison cost(i)
              = pump saving(i) - detour cost(i) + detour cost(reference)
```

If the reference has zero detour cost, this reduces to pump saving minus
candidate detour cost. Otherwise the card must show the incremental detour-cost
difference or a reference-detour credit so its visible arithmetic reconciles.
Savings may be negative. An equal-cost reference has zero saving by definition.

Example with a zero-detour reference: 170p/L versus 165p/L, 30 litres, 80p
candidate detour cost => 150p pump saving => 70p true saving.

### Rounded line items

Rounded components may differ by a penny from a rounded exact subtotal or
difference. Explicit adjustments preserve reconciliation:

```text
comparisonCostPence = fillCostPence + detourFuelCostPence
                    + costRoundingAdjustmentPence
trueSavingPence = pumpSavingPence - detourFuelCostPence
                + referenceDetourFuelCostPence + savingRoundingAdjustmentPence
```

`worseThanBestPence` is also rounded from the exact difference, not subtracted
from independently rounded display totals. For the route fixture this is 18p,
although the displayed costs £51.28 and £51.11 subtract to 17p. Stage 6 must
explain penny rounding when it matters rather than promise inconsistent sums.

`cashComparison` describes the exact sign. A saving smaller than half a penny
can have integer output zero while still ranking lower; clients must treat
sub-penny differences as negligible, not advertise a meaningful saving.

## Mode boundaries

- Fuel Trip: baseline distance/time are zero; the entire A -> station -> A
  journey is attributable to refuelling. Compare both stations' return trips.
- Along My Journey: only the difference from A -> B is attributable.
- Nearby: proximity and fill cost do not imply a return-trip or journey saving.
- Fill now/later and optional time valuation are deferred.

For now, any negative route distance or duration difference is excluded with
`negative_route_difference`; both signed differences remain in the output.
There is no implicit tolerance or zero clamp. This is a conservative temporary
policy, not a claim that every negative difference is invalid: a fastest
baseline can be longer than a slower station route. Stage 4 must revisit this
using real routing cases. Fuel Trip rejects a supplied destination/baseline so
journey and return-trip models cannot be accidentally mixed.

## Validation and provenance

The engine accepts normalized domain records, not Fuel Finder payloads or a
final HTTP schema. `B7` means standard diesel internally; official upstream enum
mapping remains a Stage 3 gate. MPG/litres must be positive; coordinates must be
finite and inside WGS84 bounds. Supported geography is checked later by routing.

The caller supplies `now` and `maxObservationAgeSeconds`; no production freshness
threshold is invented. Fixtures use 900 seconds for repeatable tests. A price
is stale when its last successful upstream observation exceeds that threshold.
An old price-change timestamp alone does not exclude a recently observed price.
Source update/observation dates must be valid ISO timestamps with timezones;
future dates and updates after their observation are excluded. Reading a cache
must not advance `fetchedAt`.

Source, update and observation timestamps survive into results. Missing/invalid
prices, wrong grade, stale observations, missing/invalid routes, different route
contexts, unpriced ferries/tolls and duplicate station IDs are returned as
explicit exclusions. Duplicate IDs exclude the entire group rather than choosing
an arbitrary observation. An unavailable explicit reference throws a clear
input error instead of silently changing the comparison.

The caller must make route context IDs truthful (engine, graph, profile,
restrictions and time assumptions) and pass actually eligible stations.
Opening/access/membership filtering, snapped endpoint validation and detour
limits belong in the later routing/candidate pipeline. Identical context strings
alone are not evidence that a provider computed comparable road routes.

No eligible candidates means empty results with null reference/labels, not
invented savings. All result values are JSON-compatible; BigInt stays internal.

## Required tests

Imperial conversion, litres/mile, route subtraction, zero detour/difference,
cheap pump with costly detour, expensive pump with no detour, negative savings,
break-even, fill-size effects, tiny values, rounding ties, non-zero reference
detour, separate modes, invalid/non-finite MPG/volume/coordinates, missing prices
and stale-feed eligibility. No comparison should fabricate an unavailable price.
