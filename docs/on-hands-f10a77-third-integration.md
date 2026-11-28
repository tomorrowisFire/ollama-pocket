# F10A77 — third focused integration

Parent d96a790d12ea9247cdd8a813f459149ea9668ac5 (F10A76 FAST 46105131432 PASS; FULL 46105131398 not yet complete when work began). Preserve phone/tablet UI, ads, billing, local privacy, data and release contracts.

| Module | Change | Verification |
|---|---|---|
| P1D | Benchmark results may remain visible without becoming recommendations. Recommend only completed measurements with known thermal below severe, at least 256 MiB free and <=20% slowdown; recheck saved structured recommendations. Mark copied benchmark reports sensitive. | BenchmarkProfileTest; CI source review |
| P2C | Contradictory available RAM > total is UNKNOWN rather than a fitting estimate. | ModelResourceAdviceTest |
| P4C | Short, single-unit document batch forwards the source-bound map note to final synthesis when the estimated final context fits; two calls rather than three in that case. Retains original reduction if not fitting; no changes to final context limits or provenance. | DocumentBatchPolicyTest, DocumentBatchReportTest |
| P4D/P5C | Local workflow uses shared strict StructuredTextStream, rejects malformed token counters and nonempty tool calls, keeps missing count unknown; only finalized bounded outputs advance. Preserve cancellation, request limits, retries and durable checkpoint. | StructuredTextStreamTest, WorkflowRunJournalTest and existing workflow CI |

One atomic source tree with independent module scopes. Exact candidate CI and device tests pending at integration. No measured speed gain or independently evaluated quality score claimed; the two-call path is a deterministic source-level request count. All physical performance and release acceptance remain deferred to final phase.
