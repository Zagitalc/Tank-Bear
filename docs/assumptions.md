# Approved assumptions and open decisions

The user approved Stage 0, reviewed the Stage 1 Android shell and Stage 2
economics demo, and authorised Stage 3. Stage 3 contract research has started;
live integration awaits credentials and resolution of documentation gaps.

## Approved direction

- Android first; reserved iOS/web folders; backend-owned ranking.
- MapLibre Native and provisional Valhalla behind an adapter.
- Imperial MPG, litres, exact decimal prices and rounded pence for presentation.
- Compare fill cost plus routed incremental fuel cost against a named reference
  including the reference's own detour cost.
- Along My Journey is the first vertical slice. Fuel Trip and Nearby remain
  distinct modes. Initial endpoints can be selected on a map.
- One manual vehicle first; no registration lookup in the vertical slice.
- No traffic, time valuation, toll-price calculation, accounts or background
  location in the first slice.
- Official Fuel Finder documentation/terms must be verified before integration.

## Provisional implementation choices

- Android namespace/application ID `uk.tankbear.app` is local-only and does not
  claim domain ownership or a store listing. Confirm before distribution.
- Minimum Android API 26; compile/target API 35 for the local skeleton.
- Search corridor around 3 km; 8–12 routed candidates, hard cap 12, concurrency 3;
  tune using benchmarks rather than treating them as proven defaults.
- Default comparison: eligible station with least extra driving time among
  stations checked. Explicitly identify that scope to the user.
- Proposed 15-minute feed refresh depends on official quotas and incremental
  API behaviour. No schedule is configured in Stage 1.
- Stage 2 uses a strict temporary route-review rule: any negative distance/time
  difference is returned unranked, with signed differences preserved. No
  negative value is silently clamped. This can hold legitimate shorter routes
  out of comparisons; reconsider with real engine evidence in Stage 4.
- Input decimals support up to 12 whole digits and 9 fractional places; official
  feed precision still needs verification. Money rounds half away from zero;
  exact values determine ranking. See `calculations.md` for reconciliation.

## Outstanding gates

- Fuel Finder exact schema, units, timestamps, pagination, quotas, removals,
  retention/redistribution terms, credentials and execution-region access.
- Routing instance ownership, access, costs, graph coverage/update policy.
- Tile/geocoding providers, attribution, terms and production budget.
- A routing-backed policy/tolerance for negative differences in Stage 4. The
  Stage 2 temporary rule is conservative; exact ties currently break on extra
  driving time, then stable station ID. Tiny differences are not a confident
  real-world recommendation merely because the model can rank them.
- Definition and thresholds for stale-feed eligibility, distinct from age of an
  unchanged price.
- Approved optimisation API before Stage 5.

None of these unknowns are represented as live features or fabricated data.
