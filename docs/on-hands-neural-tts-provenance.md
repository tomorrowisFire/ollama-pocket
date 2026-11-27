# Embedded neural TTS provenance and verification

## Runtime and model

The integration uses sherpa-onnx v2.0.3, commit `38016c6e0622e90bf417c35e8b47e0eb805af417`, through a private C API JNI bridge. The checked-in app code contains no runtime or model binaries. `scripts/build_neural_tts.sh` verifies the archive and each extracted ARM64 library before building `libonhand_tts.so` with the pinned NDK. CPU provider, two threads, speaker 0 and five synthesis steps are fixed.

| Input | Bytes | SHA256 |
|---|---:|---|
| sherpa-onnx-2.0.3.aar | 52,411,426 | `4c766e2e74c9d33d595338c21fe3b305c73ba45d47e96a235675c33142a2ec50` |
| libonnxruntime.so | as in verified AAR | `33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1` |
| libsherpa-onnx-c-api.so | as in verified AAR | `109347a4e3a0934cdbecfe1a09d0b524e74fb42039c1a57c74af43f4b26fafc1` |
| c-api.h, pinned source above | bounded to 256 KiB | `1f738de63a206328253ff471edda96b011fa1bd6f2a028f82967a7761abc2edd` |
| Supertonic3 int8 archive | 128,774,318 | `82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427` |
| Linux host-test runtime archive | 10,072,252 | `898e710a00ae0ed64e82d8696f65e40969e18c65e664b41fa80c52c4d865bd33` |

Sources:

- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.3
- https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2
- https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.3/sherpa-onnx/c-api/c-api.h

The app enables embedded TTS for Korean, English and Japanese. Chinese uses the existing explicitly offline system voice. Supertonic3 has additional upstream languages, but they are not exposed or claimed by this integration.

## Observed details that override example documentation

The pinned Supertonic3 package's `tts.json` and real inference output use **44,100 Hz**, although the upstream sample page's table says 24,000 Hz. The JNI bridge validates 44,100 Hz and ten preset speakers. Returned PCM is bounded to 60 seconds, finite samples, and the [-1, 1] range. Input is at most 120 Unicode code points, up to 240 UTF-16 units; the app's queue supplies at most 120 UTF-16 units per chunk.

The packaged ONNX Runtime's ELF Android note records API **27**, and its upstream build script specifies `--android_api 27`. Neural TTS therefore requires Android 8.1 / API 27; API 26 retains the installed offline system TTS. This does not change the app's minimum SDK or Whisper's API 26 support.

- https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/.github/workflows/android-shared.yaml

## Cancellation, ownership and completion

A single worker actor owns each playback's native engine and verified model lease, reusing them across chunks. A process-wide native-work lease prevents overlapping Whisper/TTS loaded engines. Stop invalidates the generation and pauses, flushes and stops AudioTrack immediately. Stale callbacks carry both a generation and utterance ID. Native destruction and model-lease release happen on the owning worker after the current native call returns. One closing player may have one bounded replacement waiting; there is no unbounded queue.

Supertonic3 v2.0.3 invokes its progress callback synchronously on the generating thread but **ignores the callback's integer abort return** in `ProcessChunksAndConcatenate`. A cancelled bounded synthesis may finish silently, and its waveform is discarded. Model loading is also not hard-preemptible. The integration does not claim immediate native computation termination.

- https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.3/sherpa-onnx/csrc/offline-tts-supertonic-impl.cc
- https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.3/sherpa-onnx/c-api/c-api.cc

AudioTrack uses nonblocking writes and a bounded drain deadline. Completion is emitted only after the playback-head position reaches the submitted frame count. Transient audio focus is requested using speech/assistant attributes; denied focus fails before sound, and focus loss stops output and reports one current-utterance error. Physical interruption behavior remains a final device test.

## Licensing and source records

The app bundles the full OpenRAIL-M model license separately from the model archive's MIT sample-code license. Installation presents model terms and requires explicit opt-in. Full runtime/dependency licenses and notices are under `app/src/main/assets/licenses`, indexed by `NEURAL-TTS-NOTICES.txt`.

The former espeak-ng / piper-phonemize dependency was removed in sherpa-onnx 2.0.0. The pinned 2.0.3 CMake/source tree has no such bundled dependency, and the actual C API binary contains the corresponding removal diagnostic. The app neither downloads nor links an external phonemizer.

- https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.3/CHANGELOG.md
- https://github.com/k2-fsa/sherpa-onnx/pull/4055

The ONNX Runtime Android archive omits redistribution notices. As of this implementation, the Microsoft source repository and its v1.28.2 files return HTTP 404, also recorded in upstream issue 4262. The unmodified ONNX Runtime license and broad third-party notice bundle were recovered from Microsoft's official `onnxruntime-windowsml` **1.28.2.202609030142** PyPI distribution. No Windows binaries are used. The notice set is from the same runtime version series, not a claim that the Windows and Android builds have identical dependency graphs.

- https://pypi.org/project/onnxruntime-windowsml/1.28.2.202609030142/
- https://github.com/k2-fsa/sherpa-onnx/issues/4262

| License source artifact | Bytes | SHA256 |
|---|---:|---|
| Official cp311 Windows ARM64 wheel | 26,167,729 | `95f4b53dd284052ff8b5a869d86cbc511342bec8e727249e4f3fcaece0dd99a8` |
| `onnxruntime/LICENSE` from that wheel | 1,094 | `c250d6278f0b47a6439fb7592b08b58a55eb9f535aa49a1db63211c3f982b674` |
| `onnxruntime/ThirdPartyNotices.txt` from that wheel | 331,175 | `fb0af774b4d7cffc5b9d046f2aaeade2f37df2f80abf8033c95dfffcc77a8866` |
| Supertonic3 model license, HF commit `724fb5abbf5502583fb520898d45929e62f02c0b` | 15,007 | `0d944a9110fed9a9602d60e0423a272903e7bd21ab060490774efc77c2275e9f` |

The full compiler/toolchain notice bundle is copied from the NDK r27c used to build the bridge. It includes notices for build tools not distributed in the APK. No upstream dependency sources were modified.

## Executed checks

- ARM64 bridge cross-compile using NDK r27c, three expected JNI exports, and all three TTS libraries' PT_LOAD alignment >= 16 KiB: PASS.
- `scripts/test_neural_tts_host.sh` executed against the real pinned model and the same JNI source: PASS.
- 72 Java boundary checks: PASS.
- Actual JNI synthesis in Korean, English and Japanese returned finite, nonzero 44,100 Hz PCM: PASS. Observed first-run frame counts were 170,149 / 128,056 / 149,267 respectively; these are fixture results, not quality or latency scores.
- Pre-cancel, cancellation after native admission, oversized input and unsupported Chinese locale rejection: PASS.

Device microphone/audio routing, subjective speech quality, latency, heat, RAM, battery and physical lifecycle/interruption testing remain NOT_RUN for the final device stage. Exact-commit full Android CI is recorded in the stage acceptance report, not inferred from these local checks.
