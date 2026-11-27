# F10A26 / P2A4 acceptance — repaired exact candidate

Exact app SHA: f14b422e0153f22b9cd524a1fb5f2f9524bca45a. Original 504df099 failed both Java and Android Java compile due to the duplicate constructor variable; the fix changed four lines and was verified at the exact repaired SHA.

- [Java 45952286408](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45952286408) SUCCESS. Compile job 140350676457: 155 voice model store/archive/integrity/cancellation checks, 10 ownership/cancellation, 1702 Whisper capture policy, 72 TTS boundary checks, full application Java compile, typed AAR compile, ad/production lock, five locales and regression steps PASS. Host job 140350676660: real Whisper and Korean/English/Japanese neural TTS JNI PASS.
- [Native 45952286494](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45952286494) SUCCESS. Jobs 140350676541 and 140350830215: secure relay regressions, pinned RPC/Whisper/neural TTS builds, sidebar source audit, cooperative APK, native payload, unsigned QA AAB, NNAPI probe, ARM64 ELF and APK 16 KiB checks PASS. Fixed-signed publication SKIPPED.

Exact non-expired artifact IDs and archive SHA256 (all workflow_run.head_sha matched):
- Cooperative APK 15177676609: 4cc32b3d1c0466b2e942b233e98cb989ae370310b96d6891e991b5164ecf8881
- QA unsigned AAB 15178075123: 8dec48e286bea5cf330edb8f0c1a5ddff99f8d3f1f227270a16dbabea5cfc5a9
- Compatibility audit 15177049546: 4332cedd90c58e6168aea0bdd1e106a67f4b88751a3af5f8e314d0d675e13878

P2A4 strict archive extraction accepted. No Chinese neural TTS activation, device test, final reviewer score or release approval inferred.
