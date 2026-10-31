# Stage 11 — expanded map, stage 1

Implemented locally on `stage-11` for review. The nearby-first Map redesign is
stage 2 and remains pending review of this stage.

## Behaviour

- A four-corner expand icon sits inside the small map's bottom-right corner,
  with a contrasting fill, 48dp target and accessible label. Empty-space taps
  also expand the map, without changing the start.
- The expanded map hides app navigation and the fixed search action. A second
  empty-space tap enters start selection at that point. Move the map to refine
  the centre pin, then explicitly confirm. Cancel, Back and the collapse icon
  discard the provisional selection.
- Find's map and geocoding pickers open expanded for the appropriate endpoint;
  confirmation/cancellation returns to Find. Existing manual map selection and
  the requested-only foreground-location path remain available.
- One AndroidView/MapView remains at a stable composition position as its
  bounds change. Expansion does not reset camera, selection or map overlays.
- OpenFreeMap Dark retains its source and zoom/road hierarchy. Scoped overrides
  brighten charcoal land, water, local/main roads and road/place labels. The
  overrides run before Tank Bear's route, pin and price layers are added;
  price comparison colours and native attribution are preserved.

## Files

- `ui/MapScreen.kt`: stable map host, responsive expanded layout, tap picker,
  confirmation, cancellation and Back handling.
- `ui/TankBearApp.kt`, `ui/AppViewModel.kt`: expansion and picker-return state,
  navigation visibility and return to Find.
- `ui/MapContrast.kt`: scoped dark basemap overrides.
- `res/drawable/ic_fullscreen.xml`, `ic_close_fullscreen.xml`: corner icons.
- `androidTest/.../MapExpansionTest.kt`: real map interaction/theme regression
  with public Reading coordinates, without backend access or location permission.
- `androidTest/.../AppNavigationTest.kt`: updated picker navigation expectations and recreation during selection.

The existing `app/build.gradle.kts` version change (0.2.2/versionCode 4) and
untracked user attachments predate this work and are preserved.

## Verification

- 28 JVM tests pass; debug/beta builds and beta lint pass. Lint reports 0 errors,
  31 warnings and 8 informational items.
- All 7 focused emulator map/navigation tests pass (API 37 emulator).
- Native map regression covers icon expansion, empty-space expansion, preserving
  the native host and camera, provisional selection, cancel/confirm, Back,
  rendered map features, dark-style application and enabled attribution.
  The picker also remains expanded after activity recreation and cancels back
  to Find.
- Reading screenshots are stored under `.local/artifacts/stage-11/` (ignored):
  expanded light/dark, light picker and small dark map.
- The initial test checked UI before the native map tap callback completed;
  explicit callback/render waits corrected the test. An initial build used
  Android Studio's JDK 25; verification uses installed JDK 21 with the project's
  pinned Gradle instead.

## Limitations and review gate

The emulator checks are not physical-phone acceptance, TalkBack certification,
complete 360/412dp, landscape or 200% text coverage. Routed-station marker tapping,
route preservation across expansion and expanded-picker camera restoration on rotation have not been
independently exercised in this stage; existing route/marker regression tests
remain available. Map tiles still require network access.

Review the expanded interaction and actual dark streets before stage 2: automatic
nearby loading, independent journey/nearby state, compact station sheet, and
updated automatic-lookup privacy wording. No commit, push, remote branch deletion,
phone installation, publication or deployment occurred. Only the isolated emulator
debug app was installed by the instrumentation runner.
