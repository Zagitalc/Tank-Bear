# Stage 1 verification

## Results

| Check | Result |
| --- | --- |
| `npm run check` | PASS: strict TypeScript, 5 backend tests, Worker bundle |
| `npm run test:runtime` with `npm run dev` | PASS: 2 tests against local workerd and D1 |
| `:app:assembleDebug` | PASS: debug APK built |
| `:app:assembleDebugAndroidTest` | PASS: instrumentation APK built |
| `:app:connectedDebugAndroidTest` | PASS: 1 test on installed API 37 / Android 17 emulator |
| `:app:lintDebug` | PASS: 0 errors, 9 dependency-version warnings |
| App visual check | PASS: initial screen, readable card and labelled bottom navigation |
| Gradle wrapper checksum | Matches Gradle's published 8.11.1 wrapper checksum |
| OpenAPI JSON | Parses successfully; health fixture is exercised by backend tests |

The instrumentation test switches Find/Car/Settings and verifies Car selection
survives activity recreation. The first attempt exposed an old transitive
Espresso dependency using removed InputManager reflection. Pinning Espresso
3.7.0 and current compatible test runner dependencies resolved it; the rerun
passed. Final backup-policy-only changes were rebuilt and linted afterwards.

The remaining lint warnings recommend newer AGP, Activity Compose and Compose
BOM versions (reported across multiple variants). These are intentionally pinned
compatible SDK 35 versions for local Stage 1; the warnings are not suppressed.
Review the toolchain together before distribution. Gradle also notes that one
Compose native library is packaged without stripping; this does not fail the
debug build.

Reports/artifacts (ignored build output):

- `apps/android/app/build/outputs/apk/debug/app-debug.apk`
- `apps/android/app/build/reports/lint-results-debug.html`
- `apps/android/app/build/reports/androidTests/connected/debug/index.html`
- `apps/android/app/build/outputs/screenshots/stage-1.png`

## Scope and limitations

Stage 1 contains no live feed, routing, map, vehicle persistence or economic
calculation. Android and the backend run independently. A healthy D1 probe does
not mean optimisation is available; the API explicitly reports that it is not.

Emulator validation is not a real-device, full accessibility, minimum-SDK,
performance or production-readiness audit. The next stage is the pure
calculation engine, subject to user review of Stage 1.

All project changes are local. No commit, push, PR, remote database creation,
remote migration, deployment or release was performed.

# Stage 2 verification

Stage 2 is ready for review. The Android shell and health HTTP contract are
unchanged; calculations are exercised directly with synthetic normalized data.

| Check | Result |
| --- | --- |
| `npm run check` | PASS: TypeScript, 68 tests (63 economics + 5 existing health), both bundles |
| `npm run demo:economics` | PASS: offline fixture recommends near-route over cheapest pump |
| Built engine fixture smoke check | PASS: bundled JS reproduces the exact 70p saving |

The fixture demo uses 30 litres and 45 Imperial MPG:

| Synthetic station | Price | Extra miles | Extra minutes | Detour fuel cost | True saving vs reference |
| --- | --- | --- | --- | --- | --- |
| Near-route | 169.9p/L | 0.8 | 3 | £0.14 | £2.89 |
| Cheapest pump | 166.9p/L | 7.2 | 15 | £1.21 | £2.72 |
| Reference | 180p/L | 0 | 0 | £0.00 | £0.00 |

Coverage includes Imperial units, exact break-even, negative savings, fill-size
effects, non-zero reference detours, separate journey/return-trip modes, small
values, rounding reconciliation, deterministic ties, no input mutation, missing
and stale observations, old unchanged prices, invalid timestamps/coordinates/
MPG/volume, route failures/context mismatch, ferries/tolls, duplicate stations,
negative route differences, coincident endpoints and unsafe monetary totals.

No new dependency was added. TypeScript now checks erasable syntax so the Node
24 type-stripping test runner cannot encounter unsupported parameter properties.

Important Stage 2 files:

- `backend/src/domain/economics.ts`: validation, scoring, reference and ranking.
- `backend/src/domain/numbers.ts`: exact fractions and presentation rounding.
- `backend/src/domain/types.ts`: normalized internal records/results.
- `backend/tests/economics.test.ts`: 63 unit/fixture tests.
- `backend/scripts/economics-demo.ts`: runnable offline review example.
- `contracts/fixtures/economics-*.json`: synthetic shared examples.
- `docs/calculations.md`: verifiable formula and policy specification.

Package scripts/TypeScript configuration and the README, architecture,
assumptions, contracts and local-development notes were updated to match.

Limits: tests consume supplied synthetic route totals and prices; they do not
validate a real road route, forecourt access or official feed contract. Any
negative route difference is conservatively held for review until Stage 4,
even when a shorter alternative might be legitimate. Freshness policy is
caller-supplied, not a production threshold. Android was not rebuilt in this
stage because no Android file changed. The existing health handler is unchanged;
its unit tests passed, without rerunning the unchanged local runtime suite.

No commit, push, PR, remote modification, deployment or release was performed.
Stage 3 begins only after review and requires the official Fuel Finder contract.
