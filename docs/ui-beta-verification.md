# UI beta verification — 11 October 2026

## Artifact

Tank Bear 0.2.0-beta, versionCode 2, package `uk.tankbear.app.beta`, minimum API26.
Local artifact: `.local/artifacts/tankbear-0.2.0-beta.apk` with adjacent SHA-256.
APK v2 signature verified; signing certificate SHA-256 matches the prior beta:
`1ee1966cd26c4cee6ce002527b6700e98f93b07cc43e49082a9a20ac6824f720`.
Installed using an in-place upgrade on Samsung SM-S938B. UI comparison confirms
fuel grade, MPG and litres preserved. No uninstall or beta-data clearing.
Saved-place persistence was not independently compared.

## Design and implementation

Approved Figma: https://www.figma.com/design/9Be78YHjW3gkz0sP2zBja0
Per-slice grounding, captures and review differences: `figma-ui-enhancement.md`.

| Figma tokens | Compose mapping |
| --- | --- |
| Light/Dark semantic colours | `TankBearTokens.colors` in theme/Tokens.kt |
| Roboto title, heading, price, body, label, caption | `TankBearType` in theme/Typography.kt |
| space/4,8,12,16,24,32 | `TankBearTokens.space` |
| radius/small,card,pill | `TankBearTokens.radius` |
| Minimum touch target | `TankBearTokens.touch.min` (48dp) |
| Start, route, baseline | Semantic map colours; theme reload repopulates overlays |
| Launcher foreground/background/monochrome | Exact 432px Figma exports, xxxhdpi resources |

## Completed checks

- 24 JVM tests; beta build and lint pass. Lint warnings remain; no errors.
- Full phone instrumentation: 19 reported, 18 executed successfully and one
  legacy credentialed live test skipped by its opt-in assumption. Replaced
  live coverage for this beta uses the keyless FriendBetaSmokeTest.
- Navigation, selected-tab recreation, mode fields, missing car/start/destination
  recovery, saved-place add/remove, explicit map picker cancel and fixed search.
- All four car grades and numeric editing at 360dp/200% text in both themes.
- Find pill selection, place result selection, attribution reachability, loading,
  empty and rate-limit presentation. These state checks use local fixtures.
- Full app Find/Map at 200% text in landscape, both themes: search and picker
  controls remain accessible and map retains usable area.
- Live public Reading/Oxford journey and light → dark → light preserve selected
  station, camera, picker state, overlays and native attribution. Source pin
  checks compare semantic roles because tile queries duplicate/quantize features.
- Keyless live Nearby, return-trip, price-history and Reading geocoding succeed.
  Unavailable-server outcome handled using a refused localhost connection.
- Adaptive icon resolves foreground/background/monochrome on the phone; rendered
  icon checked against the approved Figma composite.

## Remaining acceptance checks

This is not a completed accessibility or release certification.

- User TalkBack traversal and spoken labels/order; real permission denial and
  approximate-location interaction (requested from user).
- Fresh installation/setup of this final signed build on a separate device;
  current phone was intentionally upgraded without deleting user data.
- Saved-place persistence across the signed upgrade.
- Actual airplane-mode recovery, provider outages and permission settings return;
  fixtures/refused localhost are not equivalent to these end-to-end conditions.
- Full station sorting/Open now/history-chart visual regression on both themes;
  live history API success does not certify every chart state.
- Complete Figma comparison at normal 360/412dp, 200% text and landscape including
  keyboard display, all touch targets and screen-reader focus. Existing captures
  cover selected component hosts, not every full-app frame.
- Map overlay/picker preservation through actual OS theme activity recreation;
  theme test changes Compose configuration in place, not the OS setting.
- Themed launcher appearance across launcher vendors.

No commit, push, deployment or publication was performed. Operational reliability
and public release remain separate roadmap gates.
