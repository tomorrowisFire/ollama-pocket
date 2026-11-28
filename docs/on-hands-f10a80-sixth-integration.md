# F10A80 — sixth focused integration

Parent F10A79 43657639b13f4725957a8c0cf598cb2bc8f584e2, FAST 46106539953 passed. Parent FULL 46106539991 was still in progress at inspection. Independent source modules, not independently executed expert agents.

- P1G: a successful GPU startup no longer clears failures before actual inference. Completed validated chat or benchmark resets exact-weight history; failed stream stops only the owning generation; no duplicate CPU fallback after partial output, cancelled/unsafe/stale generation. Existing 60/120/240/300-second automatic retry budget remains.
- P2F: installed Ollama size and quantization come from actual local metadata (unknown remains unknown); registry estimates for noninstalled models only. HF entries cannot inherit a local Ollama installation by same name. Use same metadata for recommendation admission, score, cards and variant labels; direct Ollama pull ignores HF file size.
- P2G: prune expired query cache entries before insertion so a recently accessed expired entry cannot evict a valid one.
- P3E/P4G: Resume button reflects exact draft/model, current allowed budget, terminal/context-blocked state and idle state; refresh after edit, cancel and run. Actual confirmation and fresh durable-checkpoint verification remain authoritative.
- Verification: expand GPU generation, metadata, cache and workflow fixtures; source wiring contract. Exact candidate FAST/FULL CI and device acceptance are pending. No throughput or review score uplift asserted.

Approved header/soft-peach design, model/draft persistence, transfer guards, original ads/billing production lock and release gates retained.
