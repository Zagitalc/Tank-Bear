# Friend APK beta

Enabled on the live server on 11 October 2026 after explicit user approval.

## Installation

Share `apps/android/app/build/outputs/apk/beta/app-beta.apk` after the server
activation and fresh-install check below. Friends need Android 8 or newer; no
Android Studio, USB connection, account, server address or app key is required.
Open the APK on the phone, allow installation from that browser/file manager,
then open **Tank Bear Beta**. Select the correct fuel, enter Imperial MPG and litres,
and tap **Find fuel**. Location permission is optional and requested only on demand;
a start/destination can instead be chosen on the map.

The beta uses `uk.tankbear.app.beta`, so it installs alongside the existing
`uk.tankbear.app` developer app and starts with its own settings. It is signed,
not debuggable, and uses HTTPS. No Fuel Finder credential or app key is bundled.
One-way fuel trips are no longer offered; return trips and Along Journey remain.
The backend retains one-way compatibility for previously installed APKs.

## Server activation (requires deployment approval)

Deploy the tested backend, then set `PUBLIC_APP_ACCESS=1` in the server environment
and restart the API. The default is disabled. Existing keyed clients still work.
This allows anyone who can reach the API to use it within the limits; possession
of the APK is not access control. There are no user accounts or device identifiers.
The Node proxy adapter supplies the trusted caller IP. Rate-limit storage holds
hashed caller buckets, not precise journeys; quotas may be shared on shared Wi-Fi.
Public callers cannot escape their bucket by changing a supplied key.

Existing minute/day per-caller limits and hourly service limits still apply; history
now also has a global hourly cap. Limiter failure denies requests. Turning the flag
back to 0 restores keyed access but disables new keyless beta installations.

After activation, check a keyless public-sample search and history over HTTPS,
then test a fresh beta install, car setup, location/map choice, search and rate-limit
messages. No activation or phone test is claimed by the local build alone.

## Rebuilding and updates

Run `./apps/android/gradlew -p apps/android :app:assembleBeta`.
Signing material is generated locally in ignored `.local/tankbear-beta.jks` and
`.local/beta-signing.properties`, restricted to the current user. Keep a private
backup of both: future updates must use the same signing identity. Never commit
or share these files. Increment versionCode for subsequent distributed versions.
This is a sideloaded beta, not a Play Store release or automatic-update channel.

## Verification

Backend typecheck/build and 190 tests passed. Android builds, 24 unit tests and
beta lint passed. APK v2 signature verification passed; package/minimum SDK checked.
Public-beta activation passed live keyless HTTPS checks for Nearby, price history and return searches across all four grades, plus Along Journey. The signed beta was subsequently installed alongside the developer app on the Samsung phone. Fresh car setup with all four grades was confirmed; the Settings check was interrupted and an end-to-end phone search remains pending.
