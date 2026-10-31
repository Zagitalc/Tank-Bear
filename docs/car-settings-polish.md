# Car settings polish — 31 October 2026

The Car screen labels the fuel selector and explains the two numeric inputs.
UK Imperial MPG estimates driving fuel costs. Nearby fill estimates use fuel
type and litres. Litres to buy means the amount planned for the purchase and
controls the fill estimates on Find and Map. Settings save automatically.

`Screens.kt` uses the existing `Validation` rules to show inline field errors:
5–200 Imperial MPG and 1–500 litres, with up to three decimal places. Empty and
malformed input stays editable and receives an error; no defaults are silently
substituted. Calculation, validation limits and persistence remain unchanged.

Existing `AppNavigationTest.kt` checks now cover hints, invalid litres, recovery
to valid decimal litres, missing MPG and the search guard. `FinalLayoutTest.kt`
checks the supporting text remains reachable in landscape at 200% text in both
themes. Physical keyboard/TalkBack and phone review remain outstanding.

Debug/beta builds and beta lint passed (0 errors, 31 warnings). All seven focused
emulator checks passed. This slice is not installed or committed.
