# Stage 10-A — responsive local chat UX candidate

Baseline c7d6b98966ce4af641cf04004260524e5b7642c3. Prior 10-stage Java CI 45781825165 PASS; native API 36 builds and QA AAB/16 KiB audit still pending when reviewed.

- Consolidate export, automation, cloud entry and compute selection into a clearly labeled chat-tools menu; direct cloud shortcut remains when usable width permits. Keep original features, chat scopes and approval checks.
- Put attachment, voice and send/stop adjacent to the composer; stack full-width prompt input above actions on narrow or enlarged-text windows. Reuse the same EditText and controls on resize; no Activity recreation or draft mutation.
- Compact header on small windows; cap centered reading column to 880dp and user bubble width to 640dp. Group assistant action controls in one row; label and tooltip icon actions. Minimum 48dp touch height on shared buttons.
- On API 35+ use visible IME inset with navigation inset, hide HUD/connection chrome during typing and restore the user's HUD preference. Actual behavior requires device verification; not a screenshot-verified layout.
- Add 14 pure Java responsive policy checks and retain existing width sweep tests. Preserve startup/download/app-open ad placements and production lock; existing tablet modes and persistent history navigation remain.
- Actual device UX, keyboard/rotation/split-window and TalkBack tests NOT_RUN. No independent visual or usability score change is asserted. Final release gate remains BLOCKED pending all applicable evidence.
