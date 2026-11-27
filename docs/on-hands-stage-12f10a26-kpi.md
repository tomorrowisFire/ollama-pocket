# F10A26 / P2A4 — bounded dictionary archive support
Base HEAD 4a4f170af8cfef58c20dc9f37211cfe4afb0fbcf; exact app 60a9e9892458561287558bcd9dd61ce515d5b401. Java 45948161310, cooperative 45948161252 and Vulkan 45948161324 all SUCCESS, including ad/locale/regression and 16 KiB checks. Fixed signing skipped.

Three simulated technical roles (not independent reviewers):
- A / voice integration: the documented Chinese MeloTTS VITS candidate requires dict/ assets, lexicon and FST files; current flat-only installer cannot install that layout. No activation or language claim until pinned archive, weights license, runtime and host synthesis verified.
- B / security: permit only bounded ASCII relative paths, derived directory allowlist, exact file size/hash, reject links, traversal, duplicates, prefix fields, file/directory collisions, unexpected entries and missing explicit parent directories. Retain raw single-file restriction, 16-file cap, size limits, private staging, cancellation and atomic installation.
- C / QA: extend actual filesystem/strict tar fixtures for nested success, lease acquisition/removal, corruption, unknown directories, duplicate directories, missing parents, traversal and file/directory collisions. Existing flat archive tests and all exact CI gates must remain green.

KPI: all existing and added storage tests pass; no catalog model or runtime behavior change for existing Whisper/Supertonic; no download or Chinese support advertised. Only verified host/build evidence counts. Physical tests final; no score uplift.
Reference: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html (MeloTTS layout). Model weights/license and actual archive bytes are not verified here; direct sandbox DNS failed. No widening of network/permissions or production configuration.
