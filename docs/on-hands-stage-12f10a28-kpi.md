# F10A28 / P2A6 — pinned Chinese MeloTTS VITS host candidate

Exact accepted base 622ba80cb804e5797de0e6b81105fe4815c32b53; Java 45953439082 and cooperative native 45953439030 SUCCESS, including existing ad, five locales, regression, APK, unsigned QA AAB and 16 KiB. Fixed-signed publication skipped.

The previous dictionary-directory assumption is superseded by the pinned runtime source: sherpa-onnx v2.0.3 commit 38016c6e0622e90bf417c35e8b47e0eb805af417 VITS config documents dict_dir as unused; MeloTtsLexicon loads lexicon and tokens without the external dictionary. No older runtime downgrade. Preserve strict installed tree checks.

References: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html ; https://huggingface.co/myshell-ai/MeloTTS-Chinese (MIT model card); https://huggingface.co/csukuangfj/vits-melo-tts-zh_en/commit/a0d5c6a264c0ef92d70d8661d8cc502d79627cd6 (converted model, pinned FP32 weights 170429550 bytes SHA256 bf30582eb1b012250a35b1a4a80e7dfbcf8485e7bb9de0d95efbbeef0e4ad86d). Converted LICENSE identifies MyShell.ai MIT terms. The int8 model predates this revision's changed token vocabulary and is not selected without independent compatibility evidence. The legacy GitHub release asset lacks a published digest.

Three simulated roles, not independent reviewers:
- A: implement explicit VITS JNI model-family selection for a host-only candidate alongside unchanged Supertonic production route. Single speaker, 44100 Hz, CPU, lexicon/tokens plus approved date/number/phone rule FSTs; no unsupported language routing.
- B: acquire only pinned data files on CI, no checkout, no executable upstream code, no user data. Resolve Git blobs at exact revision; verify fixed model pointer size/SHA256, bounded sizes and LFS digests; record each selected file SHA256 and license. Retain input/waveform bounds, worker ownership/cancellation, no user-device downloads/activation.
- C: exercise actual Chinese simplified/mixed-language PCM, metadata, wrong-language rejection, cancellation suppression and retained Supertonic host fixtures through the same JNI bridge. Full exact Java/native CI required. No device or subjective quality claims.

KPI: real nonzero finite 44.1kHz Chinese fixture waveform(s), strict family admission and rejection, cancellation suppression; existing tests and all exact candidate package gates green. This is a native/host capability candidate, not end-user Chinese support. App catalog/player activation waits for verified install format/individual hashes and license notice review. No score uplift, production signing, release, privacy, network permission or locale change.
