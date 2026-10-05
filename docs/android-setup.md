# Android setup

Open `apps/android` in Android Studio and allow Gradle sync.

## Pinned local toolchain

- Gradle 8.11.1, including checked-in wrapper scripts/JAR and distribution SHA256.
- Android Gradle Plugin 8.7.3; Kotlin/Compose compiler plugin 2.1.0.
- Compose BOM 2024.12.01 and Activity Compose 1.9.3.
- AndroidX Test runner 1.7.0, JUnit extension 1.3.0 and Espresso 3.7.0.
- Compile/target SDK 35, minimum SDK 26, Build Tools 34.0.0.
- JDK 17 or 21 (verified locally with Android Studio's JBR 21).

This is a compatible installed baseline for a local skeleton, not the latest
SDK claim. Revisit target SDK/toolchain before store distribution.
[AGP compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes).

Set `ANDROID_HOME` to the SDK installation or let Android Studio generate
ignored `local.properties` containing `sdk.dir=/absolute/path/to/sdk`.
On this Mac the SDK is under `$HOME/Library/Android/sdk`.

```sh
cd apps/android
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`.
Lint report: `app/build/reports/lint-results-debug.html`.

To run, select a device in Android Studio and press Run. Or start an emulator,
then use:

```sh
./gradlew :app:installDebug
adb shell am start -n uk.tankbear.app/.MainActivity
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation test visits each tab and verifies selection survives
activity recreation. Test reports live in `app/build/reports/androidTests`.
Espresso is pinned explicitly because the older transitive version uses removed
InputManager reflection on the installed API 37 emulator; see the
[AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).
No Android JVM domain tests exist yet because Stage 1 has no domain behaviour.

The app currently displays honest early-version states in Find, Car and
Settings. It has light/dark Material themes, scrollable text and labelled
navigation. No location permission, HTTP client, map dependency or vehicle
storage is added until the corresponding stage.

The wrapper files are standard Gradle 8.11.1 distribution files, licensed under
Apache-2.0. The JAR SHA256 was checked against Gradle's published checksum:
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.

## Stage 6: journey screens

Find (coordinates for start and destination, along-journey or fuel-trip), Car (fuel type,
Imperial MPG, litres, saved with DataStore on the phone only) and Settings (server address and
app key, development only). Results come from `POST /v1/journeys/optimise`; the list is complete
without a map. Not built: map selection, location, route overlay, saved places.

```sh
cd apps/android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The unit tests parse `contracts/fixtures/optimise-response-sample.json`, a synthetic response
with invented stations. For a live run: start the backend (`npm run dev --workspace backend`
with `API_KEYS`, `ROUTING_BASE_URL` and `ROUTING_GRAPH_VERSION` set, e.g. `--var` flags), load
fuel data by calling `/cdn-cgi/handler/scheduled` on a `--test-scheduled` dev server, install the
debug build, and enter the key in Settings. The emulator reaches your computer at
`http://10.0.2.2:8787`; cleartext HTTP is allowed in debug builds for that address only.
If the emulator reports "No activity found" or `/sdcard` errors, cold-boot it
(`-no-snapshot-load`); a half-broken emulator also fails the navigation test.

## Stage 6b: map

A Map tab (MapLibre Native Android 13.6.1, OpenFreeMap style set in one place, `ui/MapConfig.kt`)
shows your start and destination pins, the direct route (grey), the route through the selected station
(amber, drawn over grey where they coincide), and a marker per ranked station. Tap a marker or use
"Show route on map" on a result card to select; long-press the map to set the start or destination
(chips choose which). The map sits above the info panel so MapLibre's OpenStreetMap attribution stays
visible. OpenFreeMap has no uptime guarantee; replace the style address with a supported or self-hosted
source before a public release (see docs/data-sources.md). Map selection needs internet for tiles.
Not built: current location, saved places, a route for stations the API did not rank.
