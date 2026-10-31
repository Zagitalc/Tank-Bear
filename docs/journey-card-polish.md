# Journey result card polish — 31 October 2026

Find and the route map now share `ui/JourneyStationCard.kt`. The summary puts
the backend's estimated fill-plus-driving total first, alongside pump price,
then routed time and distance. The driving label distinguishes extra driving,
there-and-back trips and legacy one-way results. Backend ranks, amounts, named
comparison station and the warning for differences below 20p are preserved.

Station names use two summary lines; Details reveals the full supplied name,
fill/driving breakdown, rounding explanation, comparison information, separate
feed-check age and optional price history. Opening status, individual price
change age and source credit remain visible in the summary. Find offers View
route; map cards retain station selection and the selected border. Nearby
cards retain their existing presentation and explicit straight-line wording.

`Screens.kt` and `MapResults.kt` use the shared component. Find uses stable
station keys so details state follows the station when sorting. Monetary
values are displayed directly from the response: the client does not add
rounded line items to recreate a total or recalculate savings.

`JourneyCardPresentationTest.kt` checks distinct trip wording, named comparison,
small-savings caveats, independently rounded amounts, details/history, map
selection, route actions, light/dark themes, 360/412dp widths and normal/200%
text. `NearbyMapStateTest.kt` follows the renamed View route action. Screenshot
fixtures use invented prices and public sample names, not live price evidence.

Verification: 28 JVM tests and 29 focused emulator tests passed, including map
expansion/resize and Find location/navigation regressions. Debug and beta builds
passed; beta lint reported 0 errors and 31 warnings. Summary and dark 200% text
screenshots were visually reviewed in `.local/artifacts/stage-11/journey-cards/`.
Physical-phone and TalkBack checks remain outstanding for this slice. No
installation, commit, push or deployment is included in this stage.
