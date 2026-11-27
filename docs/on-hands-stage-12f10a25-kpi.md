# F10A25 / P2A3 — embedded Whisper and neural TTS

User instruction: 2026-11-27, implement both Whisper and TTS. Frozen before app edits. Base app `e07f9a51bf4e56e45c01bdb36d68ae21e3323b4d` passed exact Java run 45944820415 and cooperative/native run 45944820367, including APK, unsigned QA AAB, all native 16 KiB checks, ads, five locales and regressions. Fixed-signing publication was skipped; no physical test or release approval is implied.

## Three-agent technical review

Three separate AI agents reviewed native Whisper, neural TTS, and integration/model storage. These are technical agent reviews, not independent human reviewers or device evidence. Root owns integration and the single guarded branch update; agents may edit disjoint local files only.

## Frozen deliverable and pass criteria

1. Embed CPU-only whisper.cpp v1.9.6 (`96ec4d9823e9d0211944172da35f9f29134af990`) with isolated native symbols. Optional multilingual tiny model: 77,691,713 bytes, SHA256 `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`. Real microphone PCM -> Whisper -> reviewed existing chat/workspace flow; API 26+ independent of Android's API31 system recognizer. No cloud audio or audio files.
2. Embed sherpa-onnx v2.0.3 CPU TTS through its pinned C API and verified ARM64 runtime. Optional Supertonic3 int8 weights; Korean/English/Japanese supported in this UI. Chinese retains explicitly disclosed installed offline system TTS. Model license is OpenRAIL-M, separate from runtime/sample licenses. No unsupported language/latency/accuracy claims.
3. Explicit opt-in model installation: pinned HTTPS source, exact download size and SHA256, strict extraction file allowlist, bounded disk/memory/network time, cancellation and cleanup, app-private atomic activation, verified model leases preventing deletion while running. Runtime libraries ship in APK; only model data downloads at runtime. Show size/source/license/support, install/remove/engine selection and failure states in all five UI locales.
4. Whisper: 16 kHz mono PCM16, at most 30 s / 480,000 samples, bounded output and CPU threads, one active native job, atomic abort plus stale-callback rejection. Do not free an active context from the UI. Model load itself is not immediately cancellable; canceled loads may finish silently before worker cleanup.
5. Neural TTS: bounded text chunks and PCM duration, serial native work, off-main model hashing/loading/inference, AudioTrack playback, immediate audible stop, pause/resume by replay of the unfinished chunk, and completion only after audio drain. Supertonic3 ignores callback abort returns: a canceled in-flight bounded synthesis may finish silently; no claim of hard native preemption.
6. Preserve existing capture finish/review, cancellation on lifecycle/visibility changes, playback guards, exactly-once continuation after playback tail, original package/signing/ads/data/routing. Apply the engines to both chat and workspace dictation/read-aloud, with offline system fallback and no automatic network recognition.
7. Verify corrupted/truncated/oversized/canceled model installs, unsafe archive entries, leases, bounds, cancellation/stale results and actual host inference fixtures for both engines. Host fixtures are not device tests. Build Android JNI, verify exact runtime symbols, complete APK/QA AAB payload and 16 KiB alignment. Full exact Java/native CI must pass before acceptance.

Physical microphone quality, latency, RAM, heat, battery, audio focus/interruption behavior, real device lifecycle stress, Chinese script fidelity, and final independent review scores remain NOT_RUN until the final authorized device stage. No score uplift in this change.

## Serial integration after the existing automation advanced

While this implementation was prepared locally, the existing scheduled queue committed compile-only adapter candidate `c60fe96b190d41e0d1022546dacdf954a25052d0` (F10A24/P2A2). The criteria above were frozen before local implementation on the previously accepted base; their substance is unchanged. Before integration, both exact c60fe96 runs were verified successful: Java 45945952313 and native 45945952315, including APK/QA AAB/16 KiB/ads/locales. No source push occurred against a stale head. The already-completed automation was temporarily paused to prevent a competing writer, and the local implementation was merged onto c60fe96 while preserving its policy, tests and isolated compile-only adapter. This candidate is F10A25/P2A3. The existing automation will resume after the final exact candidate gates are checked.
