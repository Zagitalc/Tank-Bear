# Find input simplification — 31 October 2026

After approval of the compact Map summary, Find now presents one Start row and
one Destination row (Along Journey only). Each opens a location chooser with map
selection first, optional requested-only current location for Start, saved-place
shortcuts and the existing name/postcode search. The main screen no longer
stacks separate searches, GPS controls and map buttons for both endpoints.

Saved-place management is collapsed behind Manage saved places. Coordinates and
the public Reading/Oxford example remain under More options. The existing search
modes, fixed search action, validation, result scrolling and routed results
remain in place. Choosing on the map still opens the expanded picker and returns
to Find after confirmation/cancellation.

Rows describe their current source: a saved-place name, Current location,
Selected on map, or an explicitly selected search result. These descriptions
are held in memory only and are tied to the confirmed coordinates. Moving a
preview pin drops its old place label; manually editing coordinates does not
keep an unrelated old description. No reverse-geocoding lookup was added.
Closing a chooser also prevents its late location callback from updating Start.

## Files and interfaces

- `ui/FindLocationPicker.kt`: location rows and the source chooser sheet.
- `ui/Screens.kt`: compact Find inputs, optional management/advanced sections,
  and the location button's optional completion callback/disposal guard.
- `ui/PlaceSearch.kt`, `ui/AppViewModel.kt`: pass the selected place label to map
  confirmation and maintain in-memory descriptions associated with coordinates.
- `FindLocationTest.kt`, `AppNavigationTest.kt`, `FinalLayoutTest.kt`: new input,
  source-selection and recreation coverage; update existing navigation tests for
  the chooser and collapsed optional controls.

## Verification and limits

The new checks cover hidden optional fields by default, saved Start/Destination
selection, cancellation, chooser restoration after activity recreation, and
labels following confirmed/moved/edited coordinates. Back is sent through the
actual dialog window, and recreation uses the real activity root. Existing
large-text landscape checks now follow the chooser into the expanded map.

Review images under `.local/artifacts/stage-11/find-simplification/` show sample
saved places and public test coordinates; no real journey data. Physical-phone,
real permission denial/approximate GPS and keyboard/provider-outage interaction
are not certified by these emulator tests. No new persistence, backend endpoint,
account, analytics or background location was added. No commit, installation,
push or deployment is included.

Final verification: 28 JVM tests, 25 focused emulator tests, debug/beta builds
and beta lint passed (0 errors, 31 warnings). This includes normal/200% text,
both themes and the full-app large-text landscape route into the map picker.
The final screenshot-only rerun of the three Find location tests also passed.
