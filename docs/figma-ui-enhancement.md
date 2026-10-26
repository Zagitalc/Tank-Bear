# Figma UI enhancement — approved design, foundations implementation

Design file: https://www.figma.com/design/9Be78YHjW3gkz0sP2zBja0

## Status (11 October 2026)

The user approved the revised Figma designs on 11 October 2026. The foundations
slice is implemented for review. Existing uncommitted feature work is preserved.
No backend change, commit, push or deployment is part of this slice.

The five planned sections are grouped into three pages to respect Starter limits:
Baselines, Foundations + Components, and Screens + Review. Separate Light/Dark
semantic collections reference shared palette primitives. Figwright 0.6.0 now
connects to the existing file; the official connector quota no longer blocks
this design workflow. No paid upgrade was purchased.

## Review contents

- Twelve actual phone baselines: Find, results, Map/list, place search, Car and
  Settings in both themes. Public samples are Reading to Oxford and Oxford
  place search. Automatic phone theme was restored. The dark Map baseline
  honestly shows that the current app still uses a light map.
- Semantic palette aliases, layout tokens and six Roboto text styles. Android
  code syntax is a proposed mapping for the foundations implementation slice.
- Twenty reusable components, 36 main/responsive screen specimens, two map
  picker specimens and 20 loading/error/empty-state specimens.
- 360dp and 412dp portraits, plus Find/Map 200% text and landscape variants.
  Large text uses two rows for navigation so full labels remain readable.
  The landscape map uses separate map and scrolling results panels.
- Foreground, background, monochrome and composite launcher SVG assets.
  Foreground artwork is centred on the 108dp canvas; its bounding-box corners
  lie within a 30.61dp radius, inside the 33dp adaptive safe-circle radius.

Screen content uses editable text, vectors and component instances. Raster
screenshots are baseline references only. Map geography, station names, prices
and history values in the proposals are illustrative, not live evidence.

## Verification and refinements

Representative visual checks covered main screens, narrow layouts, enlarged
text, landscape, picker, error states, chart and icon. Fixes included narrow
content widths, component text scaling, landscape attribution, navigation
selection, destination controls, fixed place attribution and full-width actions.
Find cards retain “detour fuel” and “Total for this stop”; Map cards retain their
driving-cost wording. Nearby exclusions and the estimate-margin explanation
remain present. Feed-check age stays separate from individual price age.

The complete screen-tree audit found only Roboto text and no image-filled nodes.
All 20 distinct visible solid text/background pairs passed 4.5:1, with a minimum
ratio of 5.75:1. The foundations sheet also records 28 palette text/graphic checks.
Disabled picker actions are explicitly excluded from contrast requirements.
These static checks are not certification of native rendering or map imagery.

Local review artifacts are in the task visualization directory under
`tankbear-design-review`: `index.html` contains all 58 screen/state exports;
`verification.json` records frame IDs, token definitions, typography and audit
results; `icons/` contains the four launcher SVG exports. Baseline originals
remain in the sibling `tankbear-baselines` directory. These local artifacts are
not published or committed.

## Approval gate and remaining verification

Review corrections on 11 October: matching card actions now fill the same width;
sorting chips use equal widths (128dp, 220dp at 200% text). Pill labels are
centred with symmetric vertical padding. Journey and fuel-grade choices use
24dp editable radio indicators, a 12dp label gap and consistent centred rows,
with wrapping at larger text sizes. Both themes and responsive specimens were
updated, representative controls visually checked, and all 58 review exports
refreshed. The user subsequently approved this revision.

Approval of the Figma screens, tokens, icon and proposed dark map treatment
was received before editing Compose. OpenFreeMap Dark remains the proposed
dark style and Liberty the light style. The schematic maps do not establish
MapLibre Android compatibility; test it during the approved Map implementation
slice, including camera, overlays, selection and picker preservation on reload.

Then implement separately reviewed slices: foundations (no layout changes),
Find/results, Map/list, place search, Car, Settings/launcher. Each slice requires
the existing JVM tests, beta build and lint. Native scrolling, TalkBack, 48dp hit
areas, both themes, large text, landscape and the full friend-beta checklist
must still be verified on the implementation. Record accepted design differences,
increment versionCode and verify a signed upgrade preserves settings before
delivering the next beta. No commit/push/deploy is authorized by this document.

## Slice 1: foundations

- `ui/theme/Tokens.kt` maps all 13 light/dark semantic colour roles to the
  approved variables, plus 4/8/12/16/24/32dp spacing, 12/24/28dp radii and the
  48dp minimum touch token. Screen spacing will adopt these tokens in each
  reviewed screen slice; existing layout structure is unchanged here.
- `ui/theme/Typography.kt` supplies the six approved Roboto styles through the
  Android sans-serif family, using scalable sp units. Material roles map to the
  approved styles; titleMedium uses body size with bold weight for existing
  small headings. Screen-specific hierarchy changes remain in later slices.
- `ui/theme/Theme.kt` installs these roles, shapes and typography through
  MaterialTheme and the semantic token composition local.
- `ui/MapScreen.kt` replaces inline marker/route colours with semantic roles.
  It deliberately uses the light palette while Liberty remains the map style;
  coordinated dark style/overlay changes remain in the Map slice.
- Day/night window resources use matching cream/charcoal backgrounds and
  system-bar icon appearance. Existing edge-to-edge setup remains intact.

The foundations beta is a local verification build, not the versioned delivery
APK. Native screenshot comparison, theme-switch map checks and full phone
accessibility verification remain pending. Stop for review before Find/results.

Verification: all 24 JVM tests passed; `assembleBeta` and `lintBeta` passed
with JBR 21.0.11. Lint reports 0 errors, 28 warnings and 8 informational items;
`git diff --check` passed. Android Studio's bundled JBR is now 25 and failed
with the pinned Gradle/Kotlin toolchain, so use the installed JBR 21 for this
project. No phone installation or visual approval of this slice is claimed.

## Slice 2: Find/results — ready for review

Implemented the bear header from Figma exports (44dp slot), grouped journey
choices with 24dp indicators and whole-row selection, location summary surfaces,
equal full-width centred action pills, and a 56dp minimum fixed Find action.
Station/Nearby cards use 24dp corners, 16dp padding, 12dp gaps, prominent price
and total typography, separate price-age text and stacked equal-width actions.
Find results expose the existing sort/Open now state and ordering functions;
backend rankings and amounts are unchanged. The shared history panel gains its
own heading/surface and the recorded-price step chart gains axes and dp strokes.
No API, calculation, account or tracking changes were made.

Files: `ui/FindComponents.kt`, `ui/Screens.kt`, `ui/TankBearApp.kt`, two exported
bear PNGs in `res/drawable-nodpi`, and `FindPresentationTest.kt`. Existing edits
in these files were preserved. Place-search layout, navigation styling and the
Map/list redesign remain separate slices; their existing controls are retained.
The history panel is shared with Map and therefore receives the same styling.

Verification: 24 JVM tests passed; beta/debug builds and beta lint passed
(0 errors, 28 warnings, 8 informational items). Three presentation tests passed
on the connected SM-S938B: equal-width/centred clickable pills at 200% text,
wrapped whole-row radio selection, and light/dark Find with a reachable fixed
search action. The latter produces 360dp preview captures, inspected against
Figma, saved under the task visualization directory's
`tankbear-find-implementation`. These are component-host captures without app
navigation; they do not certify the complete landscape/TalkBack checklist or
live station/history rendering. Those checks remain in final verification.

Review differences: real controls retain location privacy explanations and the
existing expanded place-search/saved-place controls, which the Figma specimen
abbreviates. Location summaries say “Location selected” rather than inventing
an address from coordinates. Native radio rendering is used for accessibility.
The screenshots use no user coordinates. No commit, push or deployment occurred.
Stop for user review before implementing Map/list.

## Slice 3: Map/list — implemented and phone theme test verified

The user approved Find/results before this slice. Map location controls now
reuse equal-width centred pills, sort/Open now controls reuse the same fixed
widths, and result cards use the approved type hierarchy, padding and surfaces.
Portrait retains the adjustable map/list split; landscape puts the map beside
the list. Large-text controls scroll with results to reserve usable map space.
Picker controls remain confirm/cancel, with no implicit location change.

OpenFreeMap's quick-start page lists Dark and states that its styles work with
MapLibre Native: https://openfreemap.org/quick_start/ (checked 11 October).
`MapConfig` selects Liberty or Dark from the system theme. A style reload
recreates overlay sources, images and layers and repopulates their current
route/station/pin data through a style revision. It does not reposition the
camera. Camera save/restore now also retains bearing and tilt; restoring an
active picker avoids jumping back to its initial point. Dark markers use dark
foregrounds against the light semantic marker colours.

24 JVM tests, beta/debug builds and beta lint pass (0 errors, 28 warnings).
MapThemeTest passes on the connected SM-S938B with the public Reading–Oxford
journey. Liberty → Dark → Liberty preserves selected station, picker target and
camera position/zoom/bearing/tilt; route and station features reload, pin source
and marker images are recreated, and attribution stays enabled. Source queries
are tile/viewport dependent: the test deduplicates initial pins and waits for
station tile loading rather than requiring off-screen pins to be returned.
The dark picker screenshot is saved in the workspace visualizations directory
under `tankbear-map-implementation/map-dark-rendered.png` and was visually checked.
The automatic capture (`map-dark.png`) catches the tile-loading transition;
the rendered capture was taken manually during the preceding test run.
The shared Map search action now matches Find's 56dp minimum height and padding.

The test uses an isolated Map screen, not the entire navigation shell. Full
TalkBack, landscape, 200% text, configuration recreation and final friend-beta
checklist remain pending; this is not certification of those checks. The native
map imagery differs from the Figma schematic. Stop for user review before the
place-search slice.
No commit, push or deployment is included.

## Slice 4: Place search — implemented, ready for review

Map/list approved by the user. Grounded this slice in Figma `5:151` (full
context and light/dark screenshots); its motion inventory is complete with no
animations. Reuses semantic tokens and FindPill: 16dp section gaps, 24dp input
corners, 28dp pill corners, 48dp minimum actions and centred labels. Existing
request cancellation, query limits, explicit search, response wording and map
confirmation are unchanged. Empty/error messages use rounded surfaces and
polite accessibility announcements. Attribution links retain 48dp targets.

Changed `ui/PlaceSearch.kt`, added `PlaceSearchPresentationTest.kt`. The
presentation is isolated from its existing request owner for testing. All 24
JVM tests, beta/debug builds and beta lint passed. Two phone UI tests pass:
360dp/200% text in both themes preserves result selection and reachable
attribution; loading disables search and empty/rate-limit states retain retry.
Light/dark public Newport fixture captures were inspected and saved under
`tankbear-place-implementation` in workspace visualizations (capture includes
button touch ripples). No live geocoding request was needed for these tests.

Review differences: search stays inline in Find, preserving navigation. The
existing brand/navigation shell is not duplicated. Material's floating field
label remains for focus/editing accessibility; attribution uses separate
clickable targets rather than the Figma's compact static caption. Full TalkBack,
keyboard/landscape and provider end-to-end checks remain for final verification.
Stop for review before Car. No commit, push or deployment.

## Slice 5: Car — implemented, ready for review

Place search approved. Grounded in full Figma `5:194` context and light/dark
renders; motion inventory complete, no animations. Car now uses the existing
bear header, 16dp outer spacing, rounded soft fuel-choice rows with 16dp gaps,
the shared larger native radio controls, rounded surface-backed number fields,
and a full-width Find fuel action. Fuel grades, input values, validation,
autosave and navigation callbacks are unchanged.

Files: `ui/Screens.kt`, `ui/TankBearApp.kt`, and `CarPresentationTest.kt`.
All 24 JVM tests, beta/debug builds and beta lint pass. One focused phone test
passes through all four grades and edits MPG/litres at 360dp and 200% text in
both themes. Test changes are confined to the debug app and restored afterward.
Scrolled light/dark captures were visually inspected and saved under
`tankbear-car-implementation` in workspace visualizations. They show fuel
rows and fields, not the whole navigation shell. Full TalkBack, landscape and
upgrade persistence verification remain for the final checklist.

Review differences: native floating field labels and accessible radio controls
are retained; selected fuel text uses the shared primary emphasis. No new
behaviour or backend changes. Stop for review before Settings/launcher.
No commit, push or deployment.

## Slice 6: Settings and launcher — implemented, ready for review

Car approved. Settings grounded in full Figma `5:239` and screenshot, with a
complete empty motion inventory. Uses existing bear header, 16dp outer spacing,
heading/body/caption hierarchy and muted privacy/provenance text. All wording,
source attribution and debug-only connection controls remain unchanged.

The adaptive launcher uses exact 4× Figma PNG exports from foreground `7:4`,
background `7:18`, monochrome `7:23`, each on a 108dp canvas (432px at xxxhdpi).
Manifest icon/roundIcon reference the adaptive mipmap. API 26 has foreground
and background; API 33 adds monochrome. No icon geometry was redrawn. Native
phone rendering was inspected against Figma composite `7:30`.

Files: `ui/Screens.kt`, `ui/TankBearApp.kt`, AndroidManifest.xml,
`drawable-xxxhdpi/launcher_*.png`, `mipmap-anydpi-v26/ic_launcher.xml`,
`mipmap-anydpi-v33/ic_launcher.xml`, `SettingsPresentationTest.kt`.
24 JVM tests, beta/debug builds and beta lint pass. A phone test confirms
scrollable attribution in light/dark at 360dp/200% text and successful adaptive
foreground/background/monochrome loading. Captures under
`tankbear-settings-implementation` were visually inspected; the test is a debug
component host, so its developer toggle is absent from the beta. Actual themed
launcher appearance across launcher vendors remains unverified.

This completes implementation slices, not final acceptance. Stop for review
before the complete friend-beta/TalkBack/landscape checklist, version increment,
signed versioned artifact and upgrade-persistence verification. Current beta
installation uses the existing signing identity and retains application data.
No commit, push, publication or deployment.

## Versioned delivery and final verification status

All six implementation slices were approved. Built and installed signed
`0.2.0-beta` (versionCode 2); signature matches the existing signing identity.
Fuel, MPG and litres survived the upgrade. Local versioned APK and checksum are
in `.local/artifacts/`. The complete evidence and remaining acceptance checks
are recorded in `ui-beta-verification.md`. Automated checks pass, but final
acceptance remains open for the documented real-device/accessibility checks;
no claim is made that the entire friend-beta checklist has passed.
