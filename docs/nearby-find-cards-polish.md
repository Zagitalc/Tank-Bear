# Nearby Find card polish — 31 October 2026

Nearby results on Find use a compact `ui/NearbyStationCard.kt`. The summary keeps
pump price, backend fill estimate and the submitted litres together, followed
by opening/closure status, explicitly straight-line distance and individual
price-change age. It explicitly excludes driving costs. Prices stack at large
text sizes and narrow widths. Supplied names use two summary lines.

Details shows the full supplied name, distinct brand, local median comparison,
separate feed-check age, source credit and the existing optional history.
Missing fuel prices never produce a fill amount or a history control. The badge
reads “Cheapest among stations checked”; its existing eligibility rule still
excludes stations reported closed or temporarily closed and those without a
price. Details explains that scope. Sorting and monetary calculations are
unchanged. Stable station keys keep expanded details attached to their station
when the list order changes. Source credit and feed-check age remain in the
result header too.

`ui/Screens.kt` integrates the card. `NearbyFindCardTest.kt` checks summaries,
details/history, distinct price/feed ages, missing prices, closure status,
sorting and cheapest eligibility. Layout checks cover 360/412dp, light/dark
and normal/200% text, including price and action overflow. Fixtures use
invented prices and public sample locations, not live price evidence.

Verification: 28 JVM tests and 17 focused emulator checks passed, including
recovery and navigation regressions. Debug/test and beta builds passed; beta
lint reported 0 errors and 31 warnings. Physical-phone and TalkBack review
remain outstanding. This slice is not installed or committed.
