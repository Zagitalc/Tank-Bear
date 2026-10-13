# Map price markers — 13 October 2026

Implemented directly in Compose/MapLibre at the user's request. Figwright is not
required for these locally drawn Android markers; the existing Figma file was
not modified.

## Presentation

- Pump-and-price pills display the exact supplied pump price, with p/L explained
  in the results legend. No brand logo downloads or new API fields are used.
- Green means more than 1p/L below the comparison median, amber is within 1p/L
  inclusive, red is more than 1p/L above, and grey means no usable comparison.
  Missing prices display a dash, never a different fuel's price.
- Nearby uses the API's local median. Routed searches use the numeric median of
  all returned routed candidates; this is explicitly labelled as the median
  among routed stations checked, not the local or national average.
- Open now and display sorting do not change the comparison population.
  Exact decimal arithmetic is used only for this presentation comparison;
  backend ranks, eligibility, routed costs and savings remain authoritative.
- Selection uses a larger price label and contrasting outline while preserving
  its price-band colour. Unselected pills use MapLibre collision detection;
  selection reserves space and remains visible. Zooming reveals more labels.
- Fit route/stations padding accounts for the actual pill dimensions, screen
  density and font scale, so a pill at the top of a fitted route is not clipped.
- Marker density is explicit and text responds to the font scale. Light/dark
  semantic colours are theme tokens. Theme reloads repopulate images and feature
  data, with camera, selection, route and picker state preserved.
- Result cards include a written median comparison, so colour is not the only
  cue. Nearby displays the supplied brand separately when it adds information.
  Routed results retain station names because that response has no brand field.
  Price age, feed health, attribution and driving-cost explanations remain.

## Verification

- 28 JVM tests pass, including exact even/odd medians, numeric ordering, the
  inclusive 1p boundary, missing/invalid prices and scope wording.
- Beta/debug builds, instrumentation compilation and beta lint pass. Lint has
  0 errors and 31 warnings; this is not an accessibility certification.
- Emulator checks cover normal/200% marker rendering in both themes, selection
  preserving the band colour, a real rendered price-pill tap on the public
  Reading/Oxford journey, dark/light image rebuilding, camera/selection/picker
  preservation and native attribution. Captures were visually inspected.
- Native source queries are viewport/tile dependent. The map regression waits
  for rendered markers and the expected theme/selection properties rather than
  assuming source queries return every point immediately after a camera change.

Local review captures: `.local/artifacts/map-price-markers/` (ignored by Git).

## Phone test and signed upgrade — 13 October 2026

At the user's request, built and installed 0.2.1-beta (versionCode 3) in place on
Samsung SM-S938B. The APK's signing certificate matches 0.2.0-beta. Visual
before/after comparison confirms E10, 40 Imperial MPG and 25 litres survived.
Saved-place upgrade persistence was not independently tested.

All three focused phone instrumentation tests passed: MapThemeTest,
PriceMarkerPresentationTest and FriendBetaSmokeTest. These use the debug package
with the same map code, public sample locations, and no phone location requests.
The map test restores that debug package's prior preferences after its run.
Live Nearby, return-trip, history and geocoding compatibility passed. Normal and
200% text marker renders in both themes passed; rendered-marker tapping and
light/dark/light reload preservation passed. This is focused coverage, not a
complete repeat of the friend-beta acceptance checklist.
The signed beta was also checked visually with the Reading/Oxford sample. This
revealed the old fit inset could clip a top-edge pill on the dense phone display;
padding was corrected, rebuilt/reinstalled, and MapThemeTest passed again with
an assertion that the entire selected pill lies inside the map after fitting.
The phone also exposed selection-versus-label overlap from symbol layer priority.
Selection now sits above ordinary pills and reserves placement space first.
A rendered-bounds collision assertion passed on the phone after rebuilding and
reinstalling; the final signed beta was visually checked again with the sample
journey and left open on Map.

Signed artifact: `.local/artifacts/tankbear-0.2.1-beta.apk`, adjacent SHA-256 file.
SHA-256: `e335904142be19cd82941c5ffad0d54213141e58bacff779e5608f8b86123b21`.
Phone captures: `.local/artifacts/map-price-markers/phone/`.
The earlier 0.2.0-beta artifact is retained unchanged.

Real TalkBack and permission checks remain deferred at the user's request;
neither is claimed as passed. No commit, push, deployment or publication occurred.
