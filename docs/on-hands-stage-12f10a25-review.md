# F10A25 / P2A3 technical review

Three separate AI agents implemented/reviewed Whisper, neural TTS, and model management. Root integrated the app and builds. This is not a human reviewer panel or physical evidence.

Concrete findings resolved before push:

1. `Activity.hasWindowFocus()` rejected workspace dialogs and voice-option dismissal timing. Capture now relies on established Activity/workspace lifecycle cancellation with finishing/destroyed checks.
2. Native library/model work was moved to workers. Model hashes and inference never run in the UI path; UI readiness is metadata only.
3. Whisper's arbitrary 256-token segment cap was removed to avoid silently truncating a rapid utterance; PCM/output/deadline limits remain.
4. The pinned Supertonic model actually produces 44.1 kHz, and its packaged ONNX runtime requires API 27. Code, UI and provenance use those observed constraints.
5. Supertonic callback return is not an abort mechanism. The implementation stops audio, discards canceled output and defers destruction safely; the limitation is documented.
6. TTS job publication/audio-stop ordering, JNI string allocation cleanup and worker-start admission failure were reviewed and repaired. Pause/resume callbacks retain generation plus utterance identity.
7. Audio completion follows actual submitted mono-frame drain. Focus loss stops audio and reports a current-utterance error. A canceled or failed utterance cannot restart the microphone.
8. Formatting-only chunks are skipped iteratively; concurrent loaded voice engines are prevented by a shared lease. No lock inversion was found in the reviewed coordinator/native/store ordering.
9. Model provenance, separate model terms, strict archive format, canceled-install cleanup, concurrent-use removal and actual network redirects were verified. The ONNX third-party notices source is explicitly qualified instead of invented.
10. A concurrent scheduled commit was discovered before any source push. Its full exact gates were checked, the schedule temporarily paused, and prior source/tests/compile adapter preserved during integration. The expected-head guarded update uses that accepted commit as parent.

No unresolved P0/P1 code issue was found in the reviewed scope. This is a bounded review finding, not a guarantee. Device recording/playback, voice quality, real interruption timing, RAM/heat/battery and final release gates remain NOT_RUN. Exact CI must still pass before candidate acceptance. Scores unchanged.
