# F10A76 — second focused integration

Parent F10A75 5a235823e3cf2825156cc54feb64f0cecab3632c. Parent FAST CI run 46104863918 passed; parent FULL 46104863890 in progress at preparation. Workstreams independently scoped in source and fixtures, not independent human/agent or device execution.

- P1C: apply existing monotonic, exact-weight retry budget to actual failed resident handoff as well as GPU launch. Abort/cancellation/thermal/low-memory/generation guards preserved; manual and benchmark bypass remains. No measured latency claim.
- P2B: bound in-process search cache to 8192 variants / 2 million metadata chars total, 4096 variants / 500k chars per entry. Don't truncate complete results into false cache hits; oversized replacements are not cached. Ollama storage recommendation includes existing 20% pull reserve plus 512 MiB. HF conservative reserve unchanged.
- P3B: measured named filter chips cap at 240dp and available viewport (48dp minimum), still one scrollable line. Full label remains in accessibility description and menu. Recalculate on resize.
- P4B: invalid verifier specs classified before output and block instead of consuming retries. Existing valid gate semantics and workflow budget unchanged.
- P5B: interactive CPU NDJSON and GPU/RPC SSE reject wrong typed terminal, message, choice, delta, content and finish-reason values before forwarding. Null content becomes empty; metadata-only events and noninteractive pulls preserve behavior. Does not assert strict full JSON syntax or security certification.

Exact commit FAST/FULL, new fixtures and full regression are required. No official PASS or score uplift until evidence; physical device and actual independent reviews still deferred. Existing signing, production, advertising and release gates unchanged.
