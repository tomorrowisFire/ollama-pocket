# F11-1C — popup presentation and lifecycle

Baseline F11-1B 80c1e575156640e9a088593b685f6ff48da2505a: Java FAST 46140865060 passed; Native FULL 46140864972 still running when isolated work started. Keep this detached until exact predecessor gates pass.

Scoped design goals: combine app and system font scaling for opted-in popup widths; calculate safe owner width without overflowing when insets exceed available space; allow one sizing binding per dialog decor and clean up its listeners and queued callback on refit/detach. Preserve dialog height, IME, security flags, listeners, selection identities, text, ads, billing, preferences and user data. Choice rows display a bold title and quieter detail only when both portions contain visible text. Existing translations and option strings are retained verbatim. No new permissions or runtime routes.

Independent Java 17 compilation and execution: 3,315 assertions passed, including scale extremes, inset-safe widths, six language/emoji title fixtures, and exact label boundaries. Locally tested source/test expected blob SHA-1: 3fe3bf2ba2ffee299f2de4f3f4119257d805766e and 958abf4e38fa56fb54b477123e30c8f021a5dd0b. App binding and new structural assertions prepared for exact FAST/FULL; Android layout, accessibility and dialog lifecycle not executed locally. No score uplift or full F11-1 completion claim.
