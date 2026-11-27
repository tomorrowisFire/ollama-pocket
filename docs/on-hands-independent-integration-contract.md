# OnHand AI — independent source development, then batch integration

User decision 2026-11-27: "나머지 개별 개발 가능한 부분부터 진행하고 일괄 통합". This is a scheduling revision, not permission to weaken quality thresholds, release, or claim untested runtime support. Applies only to dev/on-hands-ai-v012; the prior stage-scoped native contract and historical acceptances stay in force except where the ordering of unactivated components is revised.

## Phase I — independently develop and test, without activating new cross-module/native routes

Work in one serial development queue and one branch. Prioritize independent source/test modules across P3–P8 regardless of integration dependencies. Do not mark a major stage finished merely because its components exist. Keep source-only code disconnected from real calls, native processes, data transfer, release and production settings until explicitly tested at integration. Reuse existing proven modules. Choose each increment by concrete gaps; store measurable A/B/C simulated conference KPI before implementation. Local tests, exact Java/host regression, scope and secure-relay FAST gates are mandatory for each source acceptance. Native packaging may be intentionally skipped for eligible source-only increments, recorded as DEFERRED_TO_BATCH_INTEGRATION/NOT_RUN, never PASS. An unfinished or failed prior required gate permits read-only inspection and detached isolated implementation/test preparation, not a new live branch update, except scoped repair. No competing branch writer.

| Area | Independent preparation and acceptance | Deferred integration |
|---|---|---|
| P3 VLM | P3A2 actual pinned host inference and pair/request tests accepted; next private verified model pair install/remove/lease, consent and request lifecycle | Android native multimodal server ownership, explicit UI route, model capability handshake |
| P4 Tools | Strict JSON argument admission (P4A1-I); permission, request identity, tool schema, budget, response bounds and mocked adapter tests | Connect to existing MCP/manual tool review and actual authorized adapter path |
| P5 Automation | Conditional workflow, finite retry, resume/idempotency and checkpoint policy tests | Connect executor and foreground controls; no unsupported background execution |
| P6 Artifacts | Safe typed chart/document/preview validation and export fixtures | Connect renderer and isolated viewer; no arbitrary active code/network |
| P7 UX | Independent responsive/accessibility/layout policy and five-locale fixtures | Apply coordinated workbench/phone/tablet UI changes, retain sidebar and first-run ad |
| P8 Model ecosystem | Verified profiles, bounded declarative agent import/export, provenance and privacy fixtures | Connect model catalog and explicit opt-in sharing; no fabricated capabilities |

This queue is a work breakdown, not a declaration that the listed components are implemented. Existing source and tests must be audited before each addition.

## Phase II — one coordinated integration batch

After independently feasible components have actual scoped acceptance, audit dependencies and unresolved defects; freeze the integration scope/KPI. Connect validated modules and complete engine-specific remaining work together, with actual local/host regressions and exact candidate Java. Include the required docs/on-hands-native-checkpoint.json marker for native-impact changes and the combined P3–P8 acceptance candidate. Perform FULL packaging and native payload, QA APK/AAB, ARM64/16 KiB, ad, locale and secure relay checks for that exact integrated candidate. If integration is necessarily divided because of a native-impact dependency or failed gate, perform FULL at that dependency before further activation; do not bypass classifier or known failures. Keep unqualified or unsupported capabilities disabled and explicitly recorded. The combined checkpoint is not the P9 release approval.

P9 freeze and real device testing remain last. Preserve original 22 release gates, zero unresolved P0/P1, ten actual independent reviewers each and mean >=90, original five locales, user data, permissions, production ad/signing locks, main branch protection and separate release approval. No artificial score uplift. No hidden network transfer, paid API, automatic main merge or deployment.
