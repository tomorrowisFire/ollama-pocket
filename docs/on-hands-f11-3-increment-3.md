# F11-3 r003 — continuous on-device recognition idle parity

Apply the existing 8-second continuous Whisper no-speech budget to Android on-device SpeechRecognizer after ready. Keep manual recognition and overall timeouts unchanged. Clear timer on speech onset, nonempty partial transcript, end, manual finish or cancellation; token and capture identity guard stale callbacks. Pure state fixtures check ready/early speech/late events and terminal cancellation. No online recognition fallback or microphone permission change. Physical latency and device lifecycle remain unverified.
