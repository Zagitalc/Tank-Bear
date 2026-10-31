# Location entry and result visibility fixes — 31 October 2026

User screenshots showed the location chooser covering its search field with the
keyboard and reported difficulty noticing completed results. A live check of
the supplied full postcode returned one valid result in both typed and uppercase
forms. That does not rule out an earlier provider/network failure. The supplied
postcode and returned coordinates are not recorded in this report or fixtures.
No backend or provider change/deployment is included.

## Fixes

- The location chooser opens fully expanded, respects keyboard insets and keeps
  the focused field in view as keyboard size changes. The activity explicitly
  requests resize behaviour for IME insets. Opening the chooser clears focus and
  dismisses a keyboard left by another field.
- The text field exposes a Search keyboard action. Both the keyboard action and
  Search places button clear focus/hide the keyboard before requesting results.
  Editing still cancels the previous lookup and old responses remain guarded.
- Full UK postcodes are uppercased/spaced consistently. Repeated typed whitespace
  is collapsed; ordinary place names keep their casing. Keyboard autocorrection
  is disabled for this field to avoid rewriting postcodes/proper names. This is normalisation,
  not postcode validity or provider-coverage certification.
- Place results show an explicit count and automatically reveal the first result
  (or an error/empty message). Selection still requires confirmation on the map.
- Fuel result auto-scrolling now targets the first station card instead of only
  a heading. The previous minimum scroll could leave actual cards off-screen.
  Find also has a fixed View results control for the current outcome; it makes
  no new request and disappears when inputs invalidate those results.

## Files and verification

Implementation: `AndroidManifest.xml`, `data/GeocodingClient.kt`,
`ui/FindLocationPicker.kt`, `ui/PlaceSearch.kt`, `ui/AppViewModel.kt`,
`ui/TankBearApp.kt`, `ui/Screens.kt`, `ui/JourneyStationCard.kt` and
`ui/NearbyStationCard.kt`.

`LocationKeyboardTest.kt` uses a real software keyboard to check field bounds,
Search submission, keyboard dismissal and visible place results for start and
destination, including landscape/200% text and both themes. Its temporary IME
test settings are restored after the keyboard test class. The emulator's floating stylus
toolbar was disabled for these tests; this is not a handwriting-keyboard pass.
`FindResultsVisibilityTest.kt` verifies that journey/nearby completion and the
View results control reveal an actual station without manual scrolling.
`DataTest.kt` covers postcode/whitespace normalisation with public sample data.

Verification: 29 JVM tests and the final 18 focused emulator checks passed.
All four real-keyboard checks were repeated successfully after the final
autocorrection setting change. Card, recovery and map-resize checks also passed
in the broader regression run. Debug/test and beta builds passed; beta lint
reported 0 errors and 31 warnings. Captures were visually reviewed and kept in ignored
`.local/artifacts/stage-11/location-fixes/`. Samsung/handwriting-keyboard and
TalkBack review remain outstanding; the phone was disconnected during testing.
No installation, commit, push or prerelease update is included in this slice.

Implementation references: [Android keyboard insets](https://developer.android.com/develop/ui/compose/system/insets-ui)
and [Geoapify geocoding documentation](https://apidocs.geoapify.com/docs/geocoding/).
