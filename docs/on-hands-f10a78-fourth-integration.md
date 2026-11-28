# F10A78 — fourth focused integration and regression repair

Parent F10A77 9ec093f0cd1dd84273b986784d51e535a09ef712. FAST run 46105769928 failed at ProjectReportHandoffTest because the fixture asserted three calls for the old singleton reduction path, while F10A77 legitimately uses two when the final prompt fits. Keep exact count and +/-1 rejection, citation and version checks; adjust only the obsolete expected actual count. Prior F10A76 FAST/FULL PASS. F10A77 is not accepted as a completed gate.

- P1E: reverse-scan latest completed chat pairs with bounded allocation. Same context content/order/size limits and persisted history; deterministic 800 mixed-history equivalence fixtures against prior algorithm (no actual latency claim).
- P2D: explicit model identifiers take precedence over metadata; provider name Ollama no longer misclassifies unknown/Gemma/DeepSeek models as Llama. Expose IQ1-4 and MXFP4 filters; normalize exact choices. This is catalog classification, not new inference backend support.
- P3C: separate trailing disclosure glyph from model label so ellipsis doesn't remove the picker affordance. Preserve prior header dimensions and palette.
- P4E: block estimated over-context workflow prompt before consuming a reservation; use the shared document estimator (heuristic, not tokenizer), preserve prior checkpoint and no silent input truncation. Context limit is a durable, localized reason. Existing explicit resume remains available within budget.

All independent changes integrated atomically; exact-commit FAST/FULL and final device/independent reviewer gates required. No score increase, actual performance measurement or release approval claimed.
