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
