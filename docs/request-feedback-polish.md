# Loading and recovery polish — 31 October 2026

Find shows one request error in its fixed action bar. A validation error takes
precedence when inputs need correcting. Journey and nearby loading feedback use
one spinner/status and one reachable Cancel search action. The route map retains
its own status and retry action because Find's action bar is not present there.

Find journey, Find nearby and nearby Map requests share the server's positive
`retryAfterSeconds` deadline for the active server/key. The countdown rounds up
partial seconds and updates while visible. Request entry points enforce the
deadline as well as disabling buttons, so changing modes or locations cannot
send an early retry. Existing completed map results can still be viewed from
memory. A shorter subsequent delay cannot shorten an active wait. Changing the
connection ignores the old connection's wait and late cooldown responses.

The timer does not send requests at expiry. It only re-enables retry. Its deadline
is in memory and uses the existing supplied clock; process restarts do not retain
it. History and place-search request flows are outside this slice.

Nearby Map replaces Refresh with Cancel during a lookup. Cancel invalidates the
request generation and leaves a clear cancelled state with Refresh, preserving
Find's separate search. Empty nearby results on Find offer Change start, opening
the existing location chooser with permission-free map selection available.

Implementation: `ui/AppViewModel.kt`, `ui/RequestFeedback.kt`, `ui/Screens.kt`,
`ui/TankBearApp.kt`, `ui/NearbyMapPanel.kt` and `ui/MapResults.kt`.
`RequestRecoveryTest.kt` uses deterministic responses to verify single errors,
countdown boundaries, request guards in both directions between Find and Map,
mode switching, cancellation, late responses and empty-result recovery. The Map
countdown is checked at 360/412dp, light/dark and normal/200% text. Existing
navigation, map-state and presentation checks provide regression coverage.

Verification: 28 JVM tests and 21 focused emulator checks passed. Debug/test APKs
and beta builds passed; beta lint reported 0 errors and 31 warnings. Physical-phone
and TalkBack review remain outstanding. No installation or commit is included.
