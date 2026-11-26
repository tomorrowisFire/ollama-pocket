# Stage 12-F — continue product development before final physical tests
User clarification: physical testing LAST. Baseline application eed2009e7d4cab546d7fbb350329ae888f3d6150, documentation baseline 38541d948ccdcba833442092d53181b23927199f. Java 45804552817 and native 45804552751 both SUCCESS. Provisional prior ten-perspective scores A–J 75,74,79,81,82,76,78,73,74,80, mean 77.2; min 73. These are simulated readiness judgments, not measured user satisfaction or ten actual reviews.

## Simulated three-role conference decision
A Product/UX prioritizes actual task convenience, response interaction and localization before test preparation.
B Engineering prioritizes working features, bounded deterministic implementations and backward compatibility; no speculative native engine changes solely to raise scores.
C QA/security requires evidence-based, frozen measurable source/CI KPIs, unchanged original ad placement/ID, privacy boundaries, data integrity, honest scores and final physical verification. Do not grade documentation/tool preparation as product improvement.

## Development order (proposed; freeze KPI for each stage before implementation)
| Stage | Main improvement scope | Primary perspectives |
|---|---|---|
| 12-F1 | Chat/answer UX: narrower layouts, rich table and code usability, efficient copy/export with explicit user action and safe text handling | H, A, C, I |
| 12-F2 | Document productivity: source traceability, useful structured exports, partial result/goal visibility and extraction coverage within existing limits | B, A, J |
| 12-F3 | Workflow productivity: task presets, deterministic quality conditions and clear retry/recovery controls, preserving local-only boundaries | D, C, A |
| 12-F4 | Voice/accessibility and five-language polish: clear controls, error recovery, consistent language state and keyboard/screen-reader affordances | F, I, H |
| 12-F5 | Inference, cloud/PC and security robustness: bounded streaming, resource guard and provider/connection error clarity, measurable non-device tests | G, E, J |
| 12-F6+ | Reassess all ten perspectives; repeat targeted development stages as needed. Do not advance until all pre-device readiness ratings >=90, mean >=90, non-device tests pass and no known P0/P1 source defect. | A–J |
| 12-F-FINAL | Freeze final candidate, perform all real-device and end-to-end observations, signed update, matched performance measurements, actual ten reviews and full release gate; remedy any actual failure and retest. | All |

Scope may be adjusted at each pre-implementation meeting based on code inspection and severity; KPI thresholds cannot be lowered to pass. Every source change must be verified on its exact candidate. Keep old test kits and validated input fixtures, update candidate identities only when ready for final physical acceptance, and avoid regenerating kits on every intermediate development iteration.

12-F1 preliminary finding: current ChatRichView table copy emits raw Markdown only, which is inconvenient for spreadsheet workflows; narrow toolbar with a single over-wide child is not explicitly covered by the stacking policy. Inspect exact source and freeze an actionable improvement and tests in the next development cycle. No app changes claimed in this schedule commit. No physical KPI PASS, no score uplift, no release.
