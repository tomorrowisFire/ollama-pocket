# F10A79 — fifth focused integration

Baseline F10A78 a805ace7faf5b2a168bdb25633acebf3d6a51ce7. Parent FAST 46106216613 PASS; parent FULL 46106216593 still running when checked. No physical measurement or independent agents available. Workstreams independently scoped in source and fixtures and integrated in one tree.

| Module | Actual change | Gate |
|---|---|---|
| P1F | Early failed verified GPU stream enters existing exact model/weight cooldown and stops only its generation. CPU fallback still requires no output, no cancel/interruption and safe resources. Manual retry remains possible. | Existing handoff/benchmark tests plus source wiring |
| P2E | Local installed-model lookup accepts all whitespace-separated terms across identifier, metadata and quantization, normalizes case/full-width text, retains family/quant filters and exact IDs; no new provider query. Clear unused query cache on Android memory pressure. | ModelListFilterTest, source contract |
| P3D | Refresh model picker affordance on live theme change. Preserve header placement and soft palette. | source contract |
| P4F | Context-blocked immutable checkpoint cannot be resumed unchanged; prior results/history remain, user can start a newly reviewed plan. Cancelled checkpoints remain nonresumable. | WorkflowRunJournalTest |
| P5D | Bound diagnostic line retention during read, drain excess without allowing truncated lines as offload evidence; cap native log buffer and reject stale process log updates. CPU/RPC/GPU readers covered. | GpuOffloadEvidenceTest, source contract |

Exact candidate FAST and FULL results pending. Existing data, ads, billing and safety contracts maintained. No actual speed/quality uplift or release approval asserted. Physical testing remains final.
