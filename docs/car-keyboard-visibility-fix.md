# Car input keyboard visibility — 31 October 2026

The Car page could leave Litres to buy behind the keyboard at the bottom of the
form. Its scroll viewport did not account for the keyboard, so reaching the end
of the page did not provide enough scroll space to reveal the input.

`ui/TankBearApp.kt` now applies IME padding outside the page's scroll container,
consuming the Scaffold padding first so navigation/keyboard insets are not
counted twice. `CarNumberField` in `ui/Screens.kt` requests visibility when it
receives focus, keyboard height changes or validation feedback changes. Both
numeric fields expose Done, which clears focus and closes the keyboard. Existing
validation, numeric keyboard type and automatic saving are preserved.

`LocationKeyboardTest.kt` adds real-keyboard checks for litres and MPG, retained
edits after Done, dark mode at 200% text in portrait and normal text in landscape.
It checks the complete field bounds above the keyboard after the IME settles.
The existing four location-keyboard checks cover the shared page inset change.
The test class restores its temporary keyboard settings.

Verification: seven real-keyboard checks and nine navigation/result checks
passed, plus 30 JVM tests, debug/beta builds and beta lint (0 errors, 31 warnings).
Captures in `.local/artifacts/stage-11/car-keyboard/` were visually reviewed.
Samsung-specific and
handwriting keyboard review remain outstanding. No installation, commit, push
or release is included. The user's no-release instruction remains in effect.
