# 12-F1 evidence checkpoint / 12-F2 document productivity preflight
Application candidate 7189bd2cd70da9be364230945a6db988da220234. Physical-device tests are deferred to final acceptance only. One assistant simulates three distinct development roles; not independently executed agents.

## F1 evidence
Java CI 45810343457 SUCCESS (job 139876220508): ChatBlocksTest 37 assertions, ChatWorkspacePolicyTest 46 assertions, 176 document batch, 86 workflow, 41 strict fields/editor and 81 voice interaction checks. Locale parity, initial-ad/production lock and Android compile passed. Scoped source review confirms shared ChatRichView.create in local and cloud, original raw table/code copies retained, derived TSV and per-block code wrap, no new inference or persistence. No known P0/P1 found in this scoped review; actual clipboard interoperability and UI behavior are NOT_RUN.
Native run 45810343423: secure-relay job SUCCESS, cooperative APK job IN_PROGRESS at CPU RPC host/worker build at inspection. F1-1 remains pending, F1-2/3 deterministic evidence available, F1-4 final signoff pending. No application changes in this checkpoint. Do not implement F2 on the development branch before F1's non-device gate passes. Provisional scores retained at 77.2 pending final F1 source/CI review.

## Three-role F2 preflight (proposed, not frozen)
Source findings:
- DocumentBatchUi.showSourcePreview shows each whole extracted prefix, but there is no direct completed-excerpt inspector for a cited Dn-Sn identifier.
- DocumentBatchUi.report includes coverage and goal, but a copied partial report has no explicit top-level terminal status or stop reason; the on-screen status label is separate from the copied text.
- A completed result is available in memory with the immutable analyzed Plan and Result. Existing copy is unstructured text; there is no structured report export.
- Extraction and generation bounds, source ID validation, local CPU-only operation, and existing no-background/no-history-write behavior should remain unchanged.

A Product/UX: add an in-dialog completed-excerpt selector, exact range and extracted source preview, generated note clearly distinguished from source; allow partial results to be inspected and copied. Add terminal status and analysis goal to exported report. Provide both original human-readable report and an explicit structured report copy.
B Engineering: derive reports only from the frozen analyzed plan/result and captured run metadata, not the editable goal. Match canonical IDs against actual completed notes and plan units; never invent a source or include unprocessed ranges as evidence. Include version, status, stop reason, model, source names/kind/partial and PDF page metadata, extracted/planned/completed UTF-16 counts, completed notes, synthesis only when completed, citation warnings and request/elapsed metadata. A bounded JSON export should exclude original full source text by default and retain generated strings exactly through JSON escaping. No new inference, file persistence, background work or network/permission change.
C QA/security: explicit confirmation and existing sensitive clipboard marker, secure local preview, no active links or execution. Treat citations as ID existence checks, not factual verification. Validate completed/stopped/failed snapshots, invalid/missing/whitespace IDs and mismatched plan/result, serialization of Unicode/quotes/control characters, bounded output and no raw source inclusion in JSON. Avoid fabricated completed state; preserve all prior resource, ad and data constraints.

## Proposed F2 KPIs
- F2-1 exact-commit Java/native APK/QA AAB/16 KiB, original ad and locale regression PASS.
- F2-2 100% deterministic source lookup fixtures including exact source and UTF-16 range, completed prefix, unknown and unprocessed references, whitespace-only notes, mismatch rejection; no generated text presented as extracted evidence.
- F2-3 100% structured-report fixtures for completed/stopped/failed, terminal status/stop reason, captured goal, source coverage/partial status, metrics, citation warnings, round-trip string escaping, size bounds; no full raw source text in structured export.
- F2-4 five-locale source review of preview and copy affordances, explicit consent and sensitive clipboard, no extra model requests, persistence or new permissions, no known P0/P1 source defects.
- F2-5 actual document task, clipboard/export interoperability, visual and accessibility evidence NOT_RUN / DEFERRED_TO_FINAL, all B4-B7 and final release thresholds remain required.
Freeze exact implementation scope and test denominators at the F2 kickoff after F1 native acceptance. No stage advance or score uplift from this preflight document.
