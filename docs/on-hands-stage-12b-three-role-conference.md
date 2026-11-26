# Stage 12-B follow-up — three-role development conference

Baseline: 8b17a6b4cb21cbd409bdcd689688c238ef9a64e2. Java CI 45792646596 completed successfully; native build 45792646571 remained in progress when checked. This is a single assistant's simulated conference of three distinct roles, not three independently executed agents.

## Before implementation
- A (product/UX): multilingual long documents must not unexpectedly lose notes. Explain limits and preserve reportable partial results.
- B (engineering): fixed four-way reduction can exceed estimated input budget for long Korean/Japanese/Chinese notes. Prefer the largest fitting group of two to four and carry an unmerged singleton forward intact.
- C (QA/security): reject unlimited retries, silent truncation and fabricated completion. Require a strictly decreasing number of summaries per reduction round, explicit context-limit failure, fixture tests for long notes and impossible merges, and preserve the 2048 context, CPU-only policy, 20-minute deadline, startup ads and production lock.

## Joint decision
B implements adaptive grouping with A's five-language status/report wording and C's bounds. A completed merge reduces the number of summaries by at least one, except the one-excerpt case. Up to 24 excerpt operations and 24 synthesis operations (maximum 48 progress units); the existing deadline may stop earlier. Progress indicates an upper bound and reaches the end on successful completion. No extra model attempts for a carried singleton. If no further merge fits, retain original notes and report context-limit failure, not a final synthesis. Prompt estimates still are not exact model tokenization; generated summaries may omit facts.

## Evidence and blockers
- Added fixtures for four long CJK notes that fit only pairwise, and four oversized notes that cannot be combined. Existing ASCII, citation and partial-result fixtures remain.
- New candidate exact-commit CI and real-device tests pending. No score increase or release approval yet.
- Three-role conference is separate from the ten simulated evaluation perspectives; final target remains every reviewer >=90, with original evidence and release gates intact.
