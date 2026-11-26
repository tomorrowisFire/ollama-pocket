# OnHand AI — Stage 01 and 02 device acceptance

Status: NOT_RUN. Do not mark PASS without actual evidence. Use a dedicated test device/profile and the matching test-only development APK. Do not erase existing app data or force an incompatible-signature update. Capture synthetic data only, never API keys, connection codes or personal chats.

| Test group | Cases | Evidence required | State |
|---|---|---|---|
| Responsive layout | Auto/Phone/Tablet; usable widths 360/600/720/840/1024 dp; portrait/landscape; narrow split-screen; larger font | Window size, setting, screenshot, no clipped composer, correct drawer fallback | NOT_RUN |
| Global UI | Korean, English, zh-Hans, zh-Hant, Japanese; system/manual locale; chat, model search, settings, diagnostics, onboarding, guest, voice and cloud panel | Screenshots per language/screen and native-speaker review; no untranslated app-owned label or clipping; explicit original-language exception for historical/raw output | NOT_RUN |
| Accessibility | TalkBack, touch and keyboard focus, primary controls, scroll, dialogs, enlarged fonts | Focus order, announcements, touch targets and actual screen observations | NOT_RUN |
| Local model/history | Existing model and synthetic prior chat; select, send, stop, reopen, rotate | Model/history retention, real local response, no duplicate or lost messages | NOT_RUN |
| Advertising | First-run original banner, download banner, app-open; consent/dismissal, no-fill/offline; test ads only | Placement evidence, test-only lock, app usable without ad interaction | NOT_RUN |
| Cloud separation | Local/cloud switch, pause/rotate and credential UI; synthetic data only | No automatic local-history transfer; explicit authorization for any provider call; temporary history behavior documented | NOT_RUN |
| Cooperative | Host plus model-free guest; preflight and metrics in selected locale | Actual peer state, token output, resource allocation, unavailable metrics not invented, unsupported NPU identified | NOT_RUN |
| Signed update | Matching signature install/update with existing app data | APK SHA, signing evidence, retained model/history | NOT_RUN |

Stage 01/02 formal approval requires applicable device gates and CI PASS and no P0/P1 defect. Static/translation key parity is not real-screen proof. If hardware or credentials are unavailable mark BLOCKED. Stage 03 candidates may be prepared separately but cannot be formally approved while earlier stage gates remain open. No live advertisements, store submission or main merge.