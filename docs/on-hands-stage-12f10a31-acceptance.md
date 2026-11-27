# F10A31 / P3A1 acceptance and stage-scoped native bootstrap

Exact P3A1 app 27f89b8b1b18bfee46522b73126c5c9ce8f376f6: Java 45959129111 and Native 45959129225 SUCCESS. Android Java compiled, image policy 3837 checks passed, previous ad/locale/host/secure-relay/regressions and native runtime/APK/QA AAB/16 KiB passed. Exact nonexpired workflow artifacts (archive SHA256, not inner file hash):
- APK 15180023108: 7dec95618d1ed33c7b75d5a3dd220d6ebfc6b4b0a79aea265c26a31b3284fa5a
- QA AAB 15180122998: f627ea356dadad83ea0310d880b3b472ac15de9a1c75554ba4a903758f63223f
- compatibility audit 15181070604: 7de4a123f4b1908603ec69768c37223e22e9de5868f05e75772109690da46936

User-approved CI bootstrap f786b55e5631b5c442d72f9db85026ec4ea85721: Java 45960170519 and Native 45960170533 SUCCESS, including stage-scoped policy tests, native-build-scope, secure-relay and cooperative-apk. Exact matching nonexpired archive receipts:
- APK 15180213361: 6a69f2a264f83ed97919a01d9bbfcf7c2531fa27edecc182684e658d2955d905
- QA AAB 15181201740: 6c4e00892128dc7b71b79e7bc3d00623b88e23b140cf19974aedce9ca62169e5
- compatibility audit 15180239344: cfa5dee85fbf23ee4e761acd92272e6c183d5c8e0600c868e2bc502e848b07fc

Existing build/audit steps retained. New FAST eligible ordinary development increments may defer expensive packaging under the user-approved contract. Fixed production signing remains separate. P3A1 is a bounded image-input foundation only, not Android VLM support. Real Android decoding/rendering and device acceptance remain NOT_RUN. No score uplift.
