# Stage 12-F1 — answer productivity / responsive UI frozen KPI
Application baseline eed2009e7d4cab546d7fbb350329ae888f3d6150; process baseline f82ed5e4e311fd37c63415b4fbd0d63a81d48034. Physical tests only at final acceptance per latest user instruction. One assistant simulates three distinct roles; not independently executed agents.

A Product/UX: preserve original Markdown/code copying, add spreadsheet-oriented TSV copy and explicit code-wrap toggle in shared local/cloud rich answer rendering. Add localized actions with clear limits, and permit an over-wide single remaining toolbar control to wrap.
B Engineering: reuse bounded parsed tables. Keep column order, header, empty cells, literal Unicode and exact original Markdown; TSV is a derived paste format, not a replacement for source. Escape embedded controls/separators and prefix formula-like or quote-led text with an apostrophe; strict simple signed numeric literals may remain numeric. Bound derived clipboard text to 160000 UTF-16 units. Code wrapping is per displayed block, changes layout only, no persisted preference or output mutation. Preserve raw code text and original raw copy.
C QA/Security: do not guarantee spreadsheet behavior across all apps; disclose formula neutralization and automatic typing differences. No network/file/executable rendering, no new permissions; use existing SensitiveClipboard. Reject invalid/oversized derived table exports, never silently truncate. Keep original answer/parser limits and prevent final trailing plain block from exceeding MAX_BLOCKS.

Frozen non-device KPI:
| ID | Required evidence |
|---|---|
| F1-1 | Exact candidate Java/regression, locale/ad guard, native APK/QA AAB/16 KiB CI pass |
| F1-2 | All deterministic table export fixtures pass: header/data/order, empty cells, literal pipes and Unicode, formula/quote prefixes, signed numbers, controls/separators, shape/size/limit rejection, original raw source unchanged |
| F1-3 | All toolbar boundary and parser block-count fixtures pass, including single over-wide/fit and final trailing text |
| F1-4 | Source review verifies both local/cloud rich answers use shared view, code wrap toggles only layout, clipboard actions explicit and original raw copy retained, 5 locale resource parity, no new data/ads/security regressions and no known P0/P1 |
| F1-5 | Real phone/tablet/font/accessibility, actual spreadsheet paste interoperability and code scrolling/wrapping observations NOT_RUN / DEFERRED_TO_FINAL |

F1-1–4 and three-role source review plus provisional ten-perspective reassessment required before F2. No score increase based on declared features, no device gate before final development completion. Real spreadsheet paste may autoformat data; TSV is not a typed spreadsheet workbook or universally safe importer. Existing 22 release gates and all previous device thresholds unchanged.
