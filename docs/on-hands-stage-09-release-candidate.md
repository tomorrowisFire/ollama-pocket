# Stage 09 — release engineering candidate

Baseline 1c8614351d62b9b23fa19bdfcfabc583ccf6e850. 8-stage Java run 45780734601 PASS. No submission or production ad activation.

## Changes
- API 36 compile/target, AGP 8.11.1 / Gradle 8.13 on Java and APK workflows; minimum SDK 26 and package ID retained. Actual API 36 behavior and native device compatibility are unverified.
- Existing production AdMob lock, ad IDs, startup/download/open placement and debug demo IDs retained. Privacy choice destroys loaded ads, stale async app-open callbacks are ignored, lifecycle pauses/resumes banners, ads wait for consent flow. Real UMP/device/network tests remain necessary. Release settings cannot enable live ad requests.
- Avoid redundant HUD polling and hidden log rendering. This is a resource-load optimization, not measured speed improvement.
- Static 16 KiB ELF audit of every packaged native library, expected cooperative payload, SHA-256 and stored native APK alignment. Android zipalign verification and unsigned QA bundle build in cooperative CI. Any 16 KiB failure blocks QA bundle publication; earlier debug APK artifacts remain testing-only. AAB archive audit is not a substitute for bundletool-generated APK install and 16 KiB device tests.
- QA-only explicit property ONHAND_UNSIGNED_QA=true selects sample AdMob app ID and bypasses publisher app-ID requirement. Normal release path retains app-ID validation and production ads remain locked. No production signing or release upload.

## Official policy references (checked 2026-11-26)
- Target API policy: https://support.google.com/googleplay/android-developer/answer/11926878 — new apps/updates API 36 since 2026-08-31.
- 16 KiB support and ELF guidance: https://developer.android.com/guide/practices/page-sizes
- AGP compatibility: https://developer.android.com/build/releases/agp-8-11-0-release-notes
- API 36 behavior: https://developer.android.com/about/versions/16/behavior-changes-16
- UMP consent and privacy: https://developers.google.com/admob/android/privacy

## Release gate — not passed
- New CI and 16 KiB artifact audit results pending. Legacy CPU/Vulkan base payload may require rebuild if audit fails; third-party ML Kit/ads native libraries also included in audit.
- Physical 4/16 KiB device startup, local/remote inference, API 36 edge-to-edge, audio/voice, tablet resize/rotation, LAN discovery and permissions, test ad fill/consent withdrawal, offline behavior and package update with actual signing identity NOT_RUN.
- Dynamic private IPv4 and loopback HTTP are still explicitly supported; cleartext manifest remains broad, with existing endpoint validation. Do not claim full network audit, secure arbitrary LAN servers, vulnerability-free dependencies, or complete SDK data-safety classification.
- Play Console developer account, signed production AAB, privacy policy/data safety and special-use FGS declaration, app access/content rating, production AdMob identity/approval and all previous real-device and reviewer gates remain BLOCKED/NOT_RUN. No real user ratings or score increase from this patch.
