# Stage 12-B — device evidence validation tooling

Application candidate: 1baac26eac778aa4a17cf529c874c4f8cb411db2 (unchanged).
Three-role simulated meeting: product/UX requests repeatable task grading; engineering requests a deterministic report validator; QA/security requires fail-closed evidence checks and manual inspection rather than automatic advancement. Decision: no more speculative app changes; keep the frozen KPIs.

Java CI 45794478365 succeeded, including 176 document policy assertions. Native run 45794478381 has published the APK artifact; AAB and 16 KiB audits were pending at inspection. The tool's own local synthetic cases passed 21/21, including empty reports, wrong candidate, mismatched APK hash, Android API below 35, request/deadline violations, prohibited claims, missing or tampered evidence, nine passing tasks accepted for manual review and eight blocked, fault and locale failures. Synthetic tests are not application-device observations. No physical device or ADB is available in this session.

## Use
Python 3.11+, extract the original frozen test kit. Copy its evidence-template.json into a separate private evidence folder. Fill the existing fields with observed values; add reviewer, physical_device (boolean), cpu_only (boolean), android_api (integer), and apk_evidence. The evidence JSON must retain the frozen candidate and manifest hashes. Each evidence object is {"path":"relative/path","sha256":"actual 64-character lowercase SHA-256"}; evidence paths resolve against the JSON parent. Keep all evidence local. Each file is limited to 100 MiB by this tool.

For each task, facts use CORRECT / INCORRECT / OMITTED, status PASS or FAIL based on the frozen criteria, and four claim/synthesis flags use booleans. Add network_isolated and timing_evidence; use actual report/network evidence and independently measured map_requests, total_requests, elapsed_ms integers. For each fault add correct_failure, no_false_synthesis, inventory_preserved, notes_retained_or_not_applicable. For each locale add no_blocking_clipping, accurate_status, first_run_ad_retained, no_production_ad. All require reviewed evidence objects. No placeholders may be reported as actual evidence.

    python scripts/validate_stage12b.py --kit path/to/extracted-kit --evidence path/to/evidence.json --report path/to/new-kpi-report.json

The script verifies hashes, fixture identity, reported values, denominators and mandatory fields. READY_FOR_MANUAL_EVIDENCE_REVIEW means reported B4–B7 criteria are structurally met, not that the observations, physical device, APK provenance, task grading, network isolation or test execution are authenticated. Manual review of actual evidence, B1–B3 and B8 are still mandatory. No KPI reduction, no stage advancement, no score increase, no release. This is a process-only commit.
