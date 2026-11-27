# F10A25 / P2A3 acceptance

Exact application candidate: `60a9e9892458561287558bcd9dd61ce515d5b401` on `dev/on-hands-ai-v012`, directly descended from the accepted P2A2 candidate `c60fe96b190d41e0d1022546dacdf954a25052d0`.

## Exact-candidate evidence

- [Java and embedded voice 45948161310](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45948161310): SUCCESS. Job 140337543172 passed the existing regression suite, voice/model policies, five locales, complete application Java compilation and the retained isolated typed AAR adapter compile.
- Host job 140337542836: SUCCESS. The pinned real Whisper tiny model passed 11 core inference/cancellation/bounds checks and 18 JNI checks. The same-source neural TTS JNI produced finite, nonzero 44,100 Hz waveforms for Korean, English and Japanese, and passed pre-cancel, in-flight result suppression, input bounds and unsupported-locale rejection. Runtime and model downloads were digest-verified. This is actual host inference, not only a mock or adapter compilation.
- [Cooperative APK / QA AAB 45948161252](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45948161252), jobs 140337542803 and 140337676747: SUCCESS. Both voice runtime builds, complete prior/new native payload checks, APK assembly, unsigned QA AAB assembly, versioned NNAPI probe checks, APK/AAB ELF audits and stored APK 16 KiB alignment passed. Fixed-signed publication was SKIPPED; this is not a production release.
- [Vulkan APK 45948161324](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45948161324), job 140337542950: SUCCESS, including both embedded voice runtime builds, voice payload checks and APK assembly. Exact-candidate artifact `AI-in-Pocket-Vulkan-GPU-APK` 15175467538 is non-expired; archive digest `sha256:2b1dcff4cd0b80b990cdf65f45db3fdb9f0a82b55e4fe9c8ce860eb294535cd6`.

All three workflow runs finished successfully for the exact application candidate. The following cooperative workflow artifacts were observed non-expired, each carrying that exact `workflow_run.head_sha`:

| Artifact | ID | Archive SHA-256 |
|---|---:|---|
| Cooperative RPC APK | 15175274423 | `9f5584e36761cb58e36aac55393b751e2f95f1b80edfc98705c0aa1d75bfa2f0` |
| Unsigned QA AAB, not for Play | 15176072526 | `4e0cbca14880f35dbf217d41679061de4c67df53e26cb64afe0f70928007e9ee` |
| Release compatibility audit | 15176356471 | `186a2d19abbaed3782b9025c28e893877441d6027e7c8bdb4e4b35e18394bb6b` |
| Official signing tools | 15176231603 | `3a68e510d9f6b33da91d9cb4b7b206359c481d404db23ad5c994087c5cc378ac` |

This acceptance document is a documentation-only descendant of the tested application candidate; it does not substitute a different SHA for the tested source.

## Requested network review

On 2026-11-27, the GitHub connector and a separate read-only `git ls-remote` request both returned the exact candidate branch head successfully. Fresh TLS-verified HEAD requests to the pinned model URLs completed without downloading the model bodies: Whisper redirected from `huggingface.co` to `us.aws.cdn.hf.co` and returned HTTP 200 with 77,691,713 bytes; Supertonic redirected from `github.com` to `release-assets.githubusercontent.com` and returned HTTP 200 with 128,774,318 bytes. Both destinations are permitted by the production redirect allowlist. The preceding real Java Whisper download/install/acquire/remove and real Supertonic archive installation evidence remains applicable to the unchanged model store/catalog.

The manifest and prior chat/cloud/LAN/RPC transport files are unchanged from P2A2. Existing cleartext permission supports loopback and explicitly selected private-IPv4 HTTP endpoints; it was not broadened by this patch. Hosted cloud HTTPS behavior and encrypted approved-peer RPC transport remain unchanged. The new app network path is explicit model installation with default certificate/hostname checks, bounded redirects/timeouts, exact size/digest verification and cancellation cleanup. Embedded capture/transcription/synthesis use local model and PCM paths; no audio-upload path was added. No connectivity blocker was found in this bounded source and endpoint review. It does not establish live private-LAN, paid-provider or Android device-network behavior.

## Scope and limits

Whisper capture, native transcription, neural synthesis and playback are connected to the existing chat and workspace voice interface. Settings provides optional, verified model installation/removal and engine selection. Whisper supports Korean, English, Chinese and Japanese; embedded neural TTS supports Korean, English and Japanese on API 27+, with the installed offline system TTS retained for Chinese and API 26. Chinese neural synthesis is not implemented by this model and remains a separate P2 language-coverage item; this acceptance must not be read as evidence for that engine capability.

This records implementation and host/build evidence. It does not establish Android microphone/AudioTrack execution, perceived speech quality, device latency, memory, heat or battery results. Whisper model loading and an in-flight Supertonic synthesis are not claimed to support immediate native preemption; canceled output is suppressed and ownership remains bounded through worker completion.

No physical device test, reviewer score uplift, final ten-reviewer acceptance, production release, signing change or main-branch merge is inferred. The original final device/release gates remain in force. The development automation was temporarily paused to serialize integration with its prior P2A2 commit; its existing prompt and schedule are preserved.
