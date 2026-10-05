# Privacy

Stage 1 Android requests no permissions, has no networking/analytics SDK, and
stores no vehicle, place or journey data. Android backup is disabled to prevent
future saved places being copied to cloud backup unintentionally.

For later stages:

- Ask for foreground location only when the user requests current location.
- Explain its purpose; support denied/approximate permission and manual inputs.
- No background location, account requirement or default location history.
- Keep saved vehicles, Home/Work and settings local and optional.
- Send only the vehicle/economic fields needed for calculations. Registration
  and nickname are not needed by the optimisation API.
- Route/geocoding providers may receive precise endpoints. Name those providers
  and explain transmission before enabling their integrations.
- Do not log coordinates, raw optimisation bodies, credential-bearing URLs or
  complete upstream error messages.
- Keep route caches short-lived and separate from price data. Define TTLs and
  access controls before adding persistent/shared caching; coordinate hashes
  alone are not anonymisation.
- Restrict development services to loopback. No production telemetry is enabled.

Development tools have their own behaviour: Wrangler telemetry is disabled in
the checked-in configuration. No assumption is made about Android Studio's
user-managed preferences.

## API access and rate limiting (Stage 5 hardening)

`/v1` routes require an `X-Tank-Bear-Key` app key (rotatable list in the `API_KEYS` secret);
`/health` stays open. The key identifies an app build, not a person, and can be extracted from
an Android app, so it deters casual abuse but is not user authentication. Requests are limited
to 10 journey optimisations and 60 nearby lookups per caller per minute, and 1500 optimisations
per hour service-wide (provisional values). Counters use a truncated SHA-256 of the connecting
IP and key, never the raw values, are kept at most two hours, and fail closed. Request bodies,
coordinates and routes are not logged or stored; route results are cached in memory for five
minutes and not persisted. Before release, add per-install tokens or attestation if abuse appears.
