# F10A82 — eighth focused integration

Parent F10A81 7168219527a7a56504cbf96d39a291bf78b4941c; Java 46107475350 passed; full APK 46107475368 in progress at inspection. Independent source modules, not independent expert/device execution.

- Model download: require explicit success status instead of treating EOF as success; reject malformed typed progress metadata; cancellation before/after dispatch and callbacks; 120-second idle read timeout (not a total download deadline). Keep layer-only progress and original advertisements.
- Ready action: only after a valid refreshed installed-model snapshot confirms the exact requested tag or implicit :latest; start chat with that exact tag, not the previously selected model. Missing snapshot means no ready dialog; success is still the engine's terminal claim, not a cryptographic integrity check.
- GGUF local/HF import: saturating two-copy reserve and registration-time free-space check, reject oversized HF response before writing, cheap header and length screening, bounded diagnostic retention, cleanup registration process on failure. Local import joins busy guard and both entry points check the other model work. Existing GGUF engine validation remains authoritative. No claim of full weight integrity or architecture compatibility.
- Lifecycle: ignore stale/destroyed progress and ready callbacks; preserve model and chat data, production locks and existing license confirmation. Device cancellation, slow network, free-space races, actual registration and exact model activation remain NOT_RUN.

Added exact protocol, storage, tag and file screening fixtures plus source contract. All modules integrated in one tree; exact FAST/FULL gate pending. No measured quality or speed uplift claimed.
