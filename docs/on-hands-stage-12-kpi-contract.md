# OnHand AI — Stage 12 KPI contract
Date: 2026-11-26. Scope: Stage 12-B onward. Three-role conference is simulated by a single assistant until actual independent execution is available. This does not lower the final 10-reviewer or release gates.

## Meeting and progression protocol
1. A (Product/UX) proposes user-task, language and usability KPIs. B (Engineering) proposes performance, resource and compatibility KPIs. C (QA/Security) proposes regression, failure and safety KPIs.
2. Agree unanimously on scope, fixtures, sample sizes, hardware/model settings, targets, evidence requirements and candidate identity. Freeze this before implementation or new measurement. Record changes and require explicit user approval before lowering a gate or changing a material scope. No compensating a failed metric with higher scores elsewhere.
3. Implement on the existing development branch; run relevant fixtures, full regressions, Android compile, applicable build/audits and actual task/device checks. Each metric has PASS / FAIL / BLOCKED / NOT_RUN status and its observed numerator/denominator or measured value and evidence.
4. Three roles independently assess the recorded evidence within the available execution model, discuss disagreements, and sign off only when all required KPIs pass, no unresolved P0/P1 exists and the ten-perspective review has been recorded. Any missing test or unresolved objection remains BLOCKED. The next stage is not started until this gate passes. No auto-release or main merge.
5. The final 12-F additionally requires every existing release gate, ten actual reviews and the final 90/100 individual and mean thresholds, verified signed update, and explicit user release approval. Source/CI checks alone cannot establish user experience or performance.

## First KPI meeting: 12-B (remaining acceptance)
A: prioritize useful multilingual document answers and transparent scope; never imply full-document review.
B: retain 2048 context, CPU-only execution, max 24 excerpts / 48 progress operations / 20 minutes and original safety guards. Test actual model behavior; character limits are not exact tokens.
C: require same-candidate native/Java evidence, known-source and invented-ID fixtures, cancellation, failure retention, no external transfer, original advertising protection. Block progression if actual device evidence is absent.
Joint decision: freeze the acceptance conditions below now. Earlier implementation preceded this meeting; the thresholds are prospective for the remaining measurements, not retrospective proof.

Application candidate at adoption: 0b7da4124798fab2aa4f6763738b700720ff7baa. Subsequent code changes require a new candidate and renewed affected/full regression evidence. Documentation-only policy commits do not change the application candidate.

| KPI ID | Required pass criterion | Measurement and evidence |
|---|---|---|
| B1 | 100% required Java, locale, original-ad and regression CI steps pass | Exact application commit; CI run and job steps |
| B2 | Native APK, QA AAB and 16 KiB static audit all pass | Exact application commit; native CI evidence. QA AAB is not a release approval |
| B3 | 100% fixture coverage, correct known/unknown ID classification, no unreported extracted-range omission, cancellation/failure retains completed notes | Existing and added deterministic document fixtures, including CJK and impossible merges; no false completion |
| B4 | At least 9 of 10 real-device synthetic document tasks pass; no false whole-file claims or unflagged unknown bracketed IDs in any case | Two tasks per locale (ko, en, ja, zh-Hans, zh-Hant), supported TXT/CSV/DOCX/XLSX/text PDF (PDF Android 15+); predefined source/answer checklist: at least 90% of required facts correct per passing task and no unsupported factual claim. Record exact model/tag, device, OS, input and reviewed output. Include 1-file and 3-file tasks |
| B5 | 5/5 fault scenarios pass | Cancel, thermal/memory safety injection, server interruption, context-limit failure and rejected unsupported file; no completed synthesis after failure/cancel, no history/model loss |
| B6 | 100% observed runs obey <=24 map operations, <=48 total operations, 20-minute deadline and local-only inference; zero unintended network transfers | Same device tasks, log request counts, monotonic time, safety and network traces; distinguish document-provider retrieval from inference |
| B7 | 5/5 locale smoke reviews pass; no blocking clipping or misleading coverage/status wording | Device screenshots and interaction checks, including preview, source map, progress, and failure; retain first-run ads and verify no production ad request |
| B8 | Three-role final review, ten-perspective stage report, zero unresolved P0/P1 | Evidence review and defect register; model-generated scores remain provisional |

At adoption: B1 PASS on Java CI 45793137893; B2 in progress on native CI 45793138011; B3 deterministic document policy checks included in successful Java CI but detailed case evidence to be recorded; B4–B8 incomplete. Overall BLOCKED. Prior 12-A exact-commit Java and native CI passed. No 12-C implementation may start while this gate is not fulfilled.

## Later conference agendas (proposed, NOT frozen yet)
Each stage will set its full KPI contract before starting, not revise targets after observing outcomes.

| Stage | Proposed minimum measurable acceptance topics |
|---|---|
| 12-C automation | 10/10 sequential steps without manual next; deterministic gate correctness, zero false advancement, no duplicate side effects; 5/5 pause/cancel/restart/failure/budget scenarios; real-device task and trace review |
| 12-D voice | Five locales, 10 turns per locale; >=90% correct intended task transcription per locale with predefined scripts; measure STT and TTS latency on declared devices; 100% cancel/review/no unintended send cases; offline voice availability verified rather than assumed |
| 12-E inference | Freeze a matched-device/model benchmark baseline (at least 3 repetitions per condition); target >=15% median improvement in the selected bottleneck without >5% regression in the other measured metric; no new crash, OOM, unsupported GPU claim or thermal safety failure; CPU/GPU/RPC real-device checks |
| 12-F integrated | 22 existing evidence gates pass, zero P0/P1, verified signed update/data retention, ten individual reviews >=90 and mean >=90, user approval before release |

The numbers in later agendas are initial proposed floors; actual scope, hardware and measurement protocol must be agreed before each stage's implementation. All existing data, privacy, ad, tablet, localization and release constraints remain mandatory.

## Evidence record format
For each KPI: ID | frozen criterion | actual value | sample/denominator | application commit | evidence URL/path | status | A/B/C verdict | open defect IDs. Never use PASS for a missing sample or an unverified assertion. Store only synthetic/redacted evidence in the repository.
