# F10A27 / P2A5 — exact installed voice-model tree

Base exact app/HEAD f14b422e0153f22b9cd524a1fb5f2f9524bca45a; Java 45952286408 and cooperative native 45952286494 SUCCESS, including ad, five locales, regression, APK, unsigned QA AAB and 16 KiB; fixed-signed publication skipped. The existing P2A4 strict extraction gate is accepted at this exact SHA.

Concrete gap: VoiceModelStore.metadata checks the marker and declared files but does not reject additional files or directories after install. The upcoming Chinese MeloTTS/VITS layout passes a dictionary directory to the runtime; verifying only named files does not establish that the directory contains only approved data. Reference: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html. Direct sandbox DNS to GitHub remains unavailable; no new archive/license digest or Chinese support is claimed.

A (voice, simulated): require exact tree membership before exposing or leasing installed model data. Keep existing Supertonic/Whisper descriptors and model selection unchanged.
B (security, simulated): no symlink following, regular files only, exact declared files/derived directories plus the internal marker; abort at first unexpected entry, bounded depth, fail closed on missing/inaccessible entries. Keep per-file SHA-256 and size checks, atomic install and cleanup. Apply tree validation to metadata and before publication.
C (QA, simulated): pure Java exact-tree fixture suite plus actual VoiceModelStore nested post-install injection fixtures: unknown regular file/directory/symlink, missing file, type mismatch and recovery, with flat/archive tests and all CI gates retained.

KPI: all new exact-tree fixture checks pass; actual store rejects every undeclared entry in ready/acquire and releases any failed lease; valid original raw and archive installs remain compatible; full exact Java/native gates pass before next candidate. No model download, runtime claims, permissions, signing, advertising or locale changes. Actual independent review and device testing deferred; no score uplift.
