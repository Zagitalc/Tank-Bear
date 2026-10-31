# Saved places polish — 31 October 2026

Find's saved-place management shows each full name above its Start, Destination
and Remove actions. Actions and save buttons wrap as needed and retain at
least 48dp touch heights. The new-name field survives activity recreation.

Removing a place shows its name and Undo. Undo restores the most recent removal
at its original list position. This recovery state stays in the ViewModel while
navigating or rotating; it is not persisted after the app process ends. A new
removal replaces the previous Undo, and a successful save clears it. Restoration
guards against exceeding eight places or replacing a newer place of the same
name.

Saving displays Saved or Updated with the cleaned supplied name and local-only
storage wording. Existing case-insensitive replacement and the eight-place limit
remain unchanged. The form explains that an existing name updates that place.
Errors retain the typed name; a successful save clears it. Feedback uses normal
text for success and error colour for failures.

Implementation: `ui/Screens.kt` and `ui/AppViewModel.kt`. Existing navigation
checks cover missing start, save/update feedback, removal and Undo. The landscape
layout check covers a long place name and untruncated actions in light/dark at
200% text, restoring the prior test preferences afterward.

Debug/beta builds, 28 JVM tests and beta lint passed (0 errors, 31 warnings).
All seven focused emulator checks passed, including the corrected font-scaled
button widths in the landscape/large-text check. Physical-phone and TalkBack
review remain outstanding. This slice is not installed or committed.
