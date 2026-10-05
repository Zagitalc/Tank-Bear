# Tank Bear

Smarter stops. Less wasted fuel.

Tank Bear will compare the cost of a fuel stop using the actual additional road
journey, your vehicle's Imperial MPG, and how many litres you buy.

**Current stage: Stage 2 — calculation engine, ready for review.** The Android
shell runs and the backend exposes a D1-backed health check. A pure calculation
engine now scores supplied route/price fixtures; it is not connected to the app
or an optimisation endpoint. Live fuel data, vehicle storage, routing and maps
are still pending. No remote resources have been created or deployed.

## Layout

```text
apps/
  android/               Kotlin / Compose / Material 3 shell
  ios/                   Reserved for a future native client
  web/                   Reserved for a future browser client
backend/
  src/api/               HTTP handlers
  src/domain/            Exact economics and ranking; no I/O
  src/repositories/      D1 access
  migrations/            Reserved until the feed model is verified
  tests/                 Backend contract and failure tests
contracts/
  openapi/               Implemented API contract
  fixtures/              Shared response examples used by tests
docs/                    Architecture, setup, assumptions, verification
```

Routing and ingestion folders will arrive with their first implementations.

## Backend quick start

Requires Node.js 24 and npm (tested with npm 11).

```sh
npm ci
npm run check
npm run dev
```

In another terminal:

```sh
curl -i http://127.0.0.1:8787/health
npm run test:runtime
```

The API and emulated D1 database are local. A `200` health response means the
server can query D1; `optimisationAvailable` is explicitly `false`. Stop with
Ctrl-C. No Cloudflare account or upstream credentials are needed.

## Review the calculation engine

```sh
npm run demo:economics
```

This offline fixture demo compares a 41.2-mile baseline with routes of 42.0 and
48.4 miles. At 45 Imperial MPG and a 30-litre fill, the 169.9p/L near-route station
beats the 166.9p/L cheapest pump. All stations, prices and route totals in this
demo are synthetic. `npm run check` includes the engine's unit/fixture tests.

## Android quick start

Open `apps/android` in Android Studio. Install Android SDK 35 and Build Tools
34.0.0. Use JDK 17 or 21 and select an emulator/device with Android 8.0+.
The app does not need the backend running in Stage 1.

```sh
cd apps/android
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

The second command requires a running emulator or connected device. Set
`ANDROID_HOME` to your SDK directory or let Android Studio create ignored
`local.properties`. See [Android setup](docs/android-setup.md).

## Documentation

- [Architecture and stage plan](docs/architecture.md)
- [Approved assumptions and open decisions](docs/assumptions.md)
- [Calculations](docs/calculations.md)
- [Data sources](docs/data-sources.md)
- [Routing method](docs/routing.md)
- [Privacy](docs/privacy.md)
- [Local development and backend setup](docs/local-development.md)
- [Android setup](docs/android-setup.md)
- [Verification results](docs/verification.md)

Development approval never authorises a commit, push, PR, release, or deployment.
