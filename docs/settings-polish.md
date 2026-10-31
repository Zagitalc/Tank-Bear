# Settings polish — 31 October 2026

Settings replaces the long introductory paragraph with Location, Privacy,
Data and App version cards. Section titles expose heading semantics. Text wraps
in the existing scrollable page; no new controls or permissions are added.

Location explains permission-free map selection, requested-only device location,
Find submissions and nearby Map requests. Privacy distinguishes local saved
settings from search inputs sent for cost estimates and names Geoapify place
searches and OpenFreeMap tile requests. Data keeps source attribution, separates
price-update age from feed refresh age and distinguishes nearby straight-line
distances from routed journey costs.

The unverified “Connected to Tank Bear” claim is removed. App version uses the
current variant's generated version name and code, including the beta suffix.
Debug-only connection settings remain debug-only; their explanatory text now
acknowledges nearby Map and place-search requests too.

Implementation: `ui/Screens.kt`. Existing `AppNavigationTest.kt` checks section
navigation and generated version text; `FinalLayoutTest.kt` checks section and
version access in landscape at 200% text in both themes. Physical-phone and
TalkBack review remain outstanding.

Debug/beta builds, all seven focused emulator checks and beta lint passed
(0 errors, 31 warnings). This slice is not installed or committed.
