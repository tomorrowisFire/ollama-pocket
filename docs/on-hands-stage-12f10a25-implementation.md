# F10A25 / P2A3 — embedded Whisper and neural speech

Parent candidate: `c60fe96b190d41e0d1022546dacdf954a25052d0`, accepted in `on-hands-stage-12f10a24-acceptance.md`. Branch: `dev/on-hands-ai-v012` only. User requested both Whisper and TTS implementation. Frozen criteria are in `on-hands-stage-12f10a25-kpi.md`; its integration note records the intervening scheduled commit and temporary automation pause. No main merge or production release.

## User-visible behavior

Open **Settings → Offline voice models**, or the same control in the voice options dialog. Each model has an installed state, an engine-use checkbox, install, remove and bundled model-license controls. Install requires confirmation of the exact download size, temporary storage budget, source and license. Merely opening the screen makes no network request. Downloads are foreground operations with cancellation and cleanup when the screen closes or the Activity pauses.

Whisper tiny is an optional 77,691,713-byte download. The installed model supplies Korean, English, Chinese and Japanese local speech recognition from Android API 26. Chinese variants use the same Whisper `zh` model; script fidelity is not guaranteed. Recording ends when the user taps Finish speaking, when the conservative energy endpoint detects silence, or at 30 seconds. Recognition runs after capture, without a provisional transcription claim.

Supertonic3 int8 is an optional 128,774,318-byte archive. Embedded neural speech is enabled for Korean, English and Japanese on Android API 27+. It uses a fixed voice preset, CPU provider, two threads and five synthesis steps. Its actual pinned sample rate is 44,100 Hz. Chinese and Android API 26 retain installed offline system TTS. When an enabled compatible model is absent, existing installed offline system speech remains available. A failure after native speech begins reports an error instead of silently replaying or recapturing with a different engine.

Both engines are integrated inside the existing `OnHandsVoice` interface, so chat, one-shot workspace dictation and workspace read-aloud use the same settings. Existing transcript review, append confirmation and conversation ownership remain in place. Model installation does not transmit audio, transcripts or conversation data; inference has no cloud path.

## Runtime and storage

- Whisper: pinned whisper.cpp v1.9.6, isolated static GGML, CPU-only ARM64 API 26 JNI. Native job IDs hold shared ownership through cancel/destroy races, use atomic abort/deadline checks, and decode standard UTF-8 bytes in Java.
- TTS: pinned sherpa-onnx v2.0.3 C API/ONNX runtime libraries extracted from a digest-verified AAR, plus the application's own JNI bridge. No AAR Java/Kotlin binding is used by the production path. The prior compile-only adapter is retained in its isolated source set.
- No executable code is downloaded by the running app. All four new native libraries must be present in complete APK builds. All three APK workflows build the voice payload; Java-only compilation explicitly excludes only native presence checks.
- Model store: pinned HTTPS URLs and redirect hosts, exact byte counts and SHA-256, 64 KiB buffers, bounded timeout/storage, strict tar allowlist, no links/extensions/traversal, per-file verification, atomic private-directory installation. Model leases prevent delete/replace while verification or inference is active. `ready()` does small metadata checks; full hashing runs on the worker.
- Runtime/model/dependency license texts are bundled. Supertonic weights use OpenRAIL-M separately from sample-code MIT. Exact provenance and the ONNX notice-source qualification are recorded in `on-hands-neural-tts-provenance.md`.

## Cancellation and bounded work

One controller owns audio interactions across chat and workspace instances. A shared native-work lease prevents simultaneous loaded Whisper and neural TTS engines, including canceled work still unwinding. Capture is capped at 480,000 PCM16 samples; Whisper results are bounded to 16 KiB UTF-8 and 4,096 UTF-16 units. Native decode uses at most four CPU threads and a 120-second job deadline.

Neural playback reuses one worker-owned model/engine across at most 120 UTF-16-unit application chunks. It bounds returned PCM to 60 seconds, requests transient audio focus, uses nonblocking AudioTrack writes, and reports completion only after the playback-head reaches the submitted frame count. Pause invalidates the current utterance ID and stops output; resume replays the unfinished chunk with a fresh ID. Formatting-only chunks advance without recursion. Conversation continuation runs exactly once after the existing 350 ms tail. Foreground/visibility cancellation and generation plus utterance checks suppress stale results.

Whisper model loading cannot be interrupted mid-loader; it is discarded on cancellation and freed by its worker. Supertonic3 ignores its callback's abort return, so a canceled bounded synthesis may finish silently. Audible output stops and results are suppressed; immediate native preemption is not claimed. Worker-start failures now clear admission ownership instead of leaving an orphan busy state.

## Executed local evidence

| Check | Observed result |
|---|---|
| Full Android Java compilation, including merged prior source | PASS |
| Whisper capture bounds/endpoint policy | 1,702 assertions PASS |
| Actual tiny-model host inference, cancellation and core bounds | 11 checks PASS |
| Actual Whisper host JNI, invalid handles, cancellation/destroy | 18 checks PASS |
| Android Whisper cross-compile, isolated exports, 16 KiB segments | PASS |
| Neural TTS boundary policy | 72 checks PASS |
| Actual same-source JNI Korean/English/Japanese waveform generation | PASS |
| TTS cancellation/result suppression, oversize and unsupported locale | PASS |
| Android TTS bridge plus both dependency libraries, JNI exports/16 KiB | PASS |
| Native engine ownership and canceled waiters | 10 checks PASS |
| Model storage/integrity/cancel/unsafe archives/leases | 121 checks PASS |
| Actual Supertonic archive installation, all nine file digests and removal | 133 checks PASS, including the preceding 121 |
| Actual production Whisper HTTPS/redirect download, digest, install, acquire, remove | PASS; host environment proxy and OS trust roots used without disabling TLS |
| Existing queue/cursor/continuation/offline-voice regressions | PASS; 81 cursor, 187 continuation, 2,105 offline-voice assertions |
| Five-locale keys/format parity and direct UI literal audit | PASS |
| Release-audit unit tests and workflow YAML parsing | PASS |

These are host/build results, not physical device quality, speed, memory, heat or battery measurements. Final exact-commit Java/native runs and APK/AAB artifacts must be recorded separately after push. Original package, signing configuration, first-run advertisement and production lock, existing data and inference routing are retained. Final ten independent reviewers, scores and 22 release gates remain separate; no score uplift is assigned here.
