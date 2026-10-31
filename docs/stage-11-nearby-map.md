# Stage 11 — nearby-first Map

Stage 1 was committed as `384de20`. Stage 2 is implemented locally for review.

The Map tab loads nearby prices for the current session's confirmed start and
selected fuel. Without a start it offers map selection and requested-only
foreground location. Results use the existing nearby API's 5 km/20-station
bounds, and map movement does not send another query. A five-minute in-memory
reuse window, explicit refresh, deduplication, rate-limit delay and request
generations avoid unnecessary lookups and stale-response races. Invalid litres,
empty results and failures have visible recovery paths.

Nearby jobs, selected station, sort and Open now filter are independent of
Find's journey state. When available, the initial card prefers a priced station
that isn't reported closed. The panel shows the selected price, explicitly
straight-line distance, opening status, individual price age and separate feed
check age. A sheet provides a station list, comparison legend, sorting,
Open now and price history. Source credit and native map attribution remain.
The Map navigation tab always opens nearby; Find's Show route action opens the
routed result with Back to Find and Fit route. Android privacy text and
`privacy.md` describe the automatic lookup.

## Changed files

- `ui/AppViewModel.kt`: independent Map state, caching and request lifecycle.
- `ui/MapScreen.kt`, `ui/NearbyMapPanel.kt`, `ui/TankBearApp.kt`: nearby-first
  presentation, full station sheet, marker and route navigation.
- `ui/Screens.kt`, `docs/privacy.md`: automatic-lookup privacy wording.
- `androidTest/.../NearbyMapStateTest.kt`: mock-backed request races, retry,
  concurrent journey isolation, sheet/filter selection and route navigation.
- `androidTest/.../AppNavigationTest.kt`, `MapThemeTest.kt`: update existing
  navigation expectations and explicitly identify the journey-map context.
- `docs/stage-11-nearby-map.md`: this report.

The pre-existing `app/build.gradle.kts` 0.2.2/versionCode 4 edit and untracked
attachments were present before this stage and are preserved.

## Verification and limits

28 JVM tests, debug and beta builds, beta lint (0 errors, 31 warnings), and 11 focused emulator tests
passed. The new tests use injected sample outcomes; no live station prices were
needed. They check in-flight deduplication, stale responses when a start changes
away and back, retry delays, preservation of a concurrent journey, separate
selections and filters, sheet interaction and Find-to-route-to-Map navigation.
The real-map tests check expansion, selection, map theme changes and attribution.

Review screenshots under `.local/artifacts/stage-11/nearby-*-sample.png` use
**invented sample station names and prices** in an isolated Map screen; they are not live price evidence and do not show the app navigation.
Physical-phone behaviour, live nearby API compatibility, full route-geometry
rendering with live data, TalkBack, and systematic landscape/large-text checks
remain unverified. No new commit, push, phone installation or deployment was
performed as part of Stage 2.

## Expansion resize correction

Phone feedback showed a black strip inside the expanded map and a distorted
viewport. `MapScreen.kt` now measures the footer and map in one Compose Layout
pass, replacing footer-size state that applied the new map height in a later
frame. The native MapView remains at the same composition position. It uses
MapLibre's texture renderer so rendering follows the host view's bounds and
clipping when the map grows or shrinks.

`MapResizeTest.kt` checks thirty transitions between compact, expanded,
picker-footer and short/wide host sizes. A synthetic green map background must
reach the bottom edge; a white circle must remain round and centred. It checks
actual rendered pixels, not only the Compose container dimensions. The test
waits for app style setup before installing its synthetic style. The existing
MapExpansionTest now injects empty station results so nearby price labels cannot
intercept its intended empty-map taps, and keeps the test screen awake.

Two focused tests pass on Samsung SM-S938B: the thirty-transition resize test and
the real light/dark street-map expansion/picker regression. Earlier phone attempts timed out. The test fixtures were tightened to wait for
initial style setup and exclude nearby labels before the passing runs. This is focused coverage rather than a
complete guarantee against all device/driver conditions. Phone review capture:
`.local/artifacts/stage-11/resize-fix/phone-expanded-dark.png` (public Reading
example, visually inspected; no black strip). The phone beta was not replaced;
the tests used the separate debug package. No commit or push occurred.

Final resize verification: 28 JVM tests, 12 focused emulator tests, debug/beta
builds and beta lint pass. The corrected beta APK is built; installation on the
phone's beta package remains a separate action.
