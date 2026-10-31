# Route sort selection fix — 31 October 2026

The route sort controls previously changed list order while retaining the old
selected station. Map list scrolling then followed that old selection, and its
route stayed highlighted. Re-tapping an active sort did not change any scroll
effect key, so it could not return the list to the leading result.

Best total, Pump price and Least driving now select the leading eligible
station, respecting Open now, and explicitly request a map fit and list reveal.
Every press has a reveal event, including a repeated press of the active sort.
Find's sort controls also reveal the first card. A new journey response follows
the current sort when choosing its initial selected station.

Least driving orders exact supplied duration, then distance, then station ID,
matching `backend/src/domain/economics.ts`'s detour ordering. Best total keeps
backend ranks; Pump price keeps exact price then backend rank. Backend ranks,
money, badges and comparison station are unchanged. If the filter leaves no
eligible station, selection is cleared rather than retaining a hidden route.

Implementation: `ui/AppViewModel.kt`, `ui/MapResults.kt`, `ui/MapScreen.kt` and
the Find sort handlers in `ui/Screens.kt`. `DataTest.kt` covers equal-time/distance
ties. `RouteSortSelectionTest.kt` checks native route-source geometry, selected
marker, camera bounds and visible card after all three sorts. It also checks
re-tapping Best total after scrolling away and panning the map, Find scrolling,
Open now filtering, empty selection and initial selection on another search.
Geometry and costs in these tests are synthetic display fixtures, not routing
calculations or actual user journeys.

Verification: 30 JVM tests and all 16 focused emulator checks passed, including
map expansion/resize and Find result visibility regressions. Debug/beta builds
and beta lint passed (0 errors, 31 warnings). Physical-phone review remains.
No installation, commit, push or release is included; the user explicitly
requested no release.
