# F10A45 — approved OnHand brand and UI redesign

Approved artwork: English smartphone/chat/keyboard icon from user selection. Full composition retained; only connected dark outer corners converted to warm ivory and resized to 192px for launcher. Asset SHA-256: 8fa18315645a1293401de6126006d7553f2e14bc72c4ba9263f43dee651626fd. Android adaptive layers have 26dp insets; actual OEM launcher mask rendering requires physical device review.

- Ivory + accessible burnt orange + muted lavender, with complementary dark mode; shared tokens across native workbench and main screens.
- Native stroke pictograms, action cards, compact search/filter section, grouped settings and model status tags. No new AI features are implied by illustrative icon artwork.
- Standard Android alert themes across app-authored dialogs; existing workbench dialogs share responsive sizing and action style. OS permission prompts, input methods and third-party ad/consent surfaces remain platform-controlled.
- Existing model/inference, ad placements, consent, storage, app ID, history, signing identity and user review gates preserved.
- Required validation: exact CI, APK/AAB, 16KiB audit, real phone/tablet light/dark/auto theme, font scales, TalkBack, five locales, confirmation/permission states and all app-specific dialog layouts. No actual device observations yet; do not mark visual acceptance PASS from structural tests.
