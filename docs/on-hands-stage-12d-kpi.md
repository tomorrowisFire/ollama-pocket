# Stage 12-D — frozen speech UX and offline interaction KPI
Baseline 0d09c4f65397f278c5674724f3e7968ca79022d7; source/CI 12-C acceptance documented separately. Actual independent agents unavailable: three simulated roles of one assistant. Physical device and live inference tests explicitly deferred by user to 12-F.

A Product/UX: introduce an explicit finish-speaking control distinct from cancelling the voice session. Use translated labels and responsive answer-action toolbar; keep the review-before-send default and existing drafts. Provide understandable listening versus finalizing states.
B Engineering: one bounded capture lifecycle PREPARING/LISTENING/FINALIZING/TERMINAL; finish at most once, reject stale state transitions, do not extend existing 45-second recognition deadline. Validate final recognition text without truncating or silently removing dangerous controls; retain Unicode and draft bounds. Select a deterministic offline TTS voice by language/script, region and then quality/latency/name; for Chinese never silently substitute simplified/traditional or ambiguous script. Keep offline-only engine, existing settings, TTS queue and barge-in opt-in.
C QA/Security: no new network endpoint, microphone start, recording retention, automatic send route, permission, or preference migration. Preserve generation/session guards and terminal callback single-consumption. Catch recognizer availability/stop errors and teardown failures. Validate duplicate callbacks, premature/double finish, cancel, invalid final text, five-locale voice preference, Chinese mismatch, and existing pause/resume/Unicode fixtures.

Frozen gate before implementation:
| ID | Required criterion | Evidence |
|---|---|---|
| D1 | 100% existing/new Java, locale, ad, native APK/QA AAB and 16 KiB CI checks pass on the same application commit | exact-commit CI |
| D2 | 100% deterministic capture fixtures: premature, duplicate and terminal finish rejected; late ready cannot reopen finalizing; terminal result consumed at most once; cancel blocks callbacks | pure Java fixtures, source wiring review |
| D3 | 100% valid/invalid final transcript fixtures, no silent truncation; existing draft and Unicode regressions pass | pure Java fixtures |
| D4 | 100% locale ranking fixtures covering Korean, English, Japanese, simplified/traditional Chinese, unknown and wrong-script fallback; source filters network voices | pure Java and source |
| D5 | all five resource sets, responsive controls, unchanged permission/review/send/persistence boundaries and old playback fixtures, no known unresolved P0/P1 source defect | CI and three-role source review |
| D6 | real Android 10 scripted turns per locale (50), >=90% intended transcription per locale; zero unintended sends, all cancellation/review scenarios; measured offline availability and STT/TTS latency; visual checks | DEFERRED_TO_12F/NOT_RUN. Record exact model/device/engines; before real runs freeze latency baseline and matched comparisons rather than asserting speed gains from code. |

D1–D5 plus ten-perspective provisional source review required before 12-E. D6 and earlier physical tests mandatory at final 12-F, no release or fabricated runtime improvement.
