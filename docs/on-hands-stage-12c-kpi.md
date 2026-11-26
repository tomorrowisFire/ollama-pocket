# Stage 12-C — frozen automation KPI and design meeting

Baseline 705db91051d48847775e0739a91b50d168d98b92 (application 1baac26eac778aa4a17cf529c874c4f8cb411db2). User-authorized device-only deferral to 12-F. Single assistant simulates three roles; not independent executed agents.

A Product/UX: expose a new exact scalar JSON value verification choice in the guided step editor in five locales; retain old plans and make the result condition explicit.
B Engineering: reuse strict bounded JSON parser; accept 1–12 exact top-level scalar fields, decoded strings, booleans, finite integer/number literals. Exact key set and type plus numeric value equality within the same type; reject null, nested containers, duplicate keys, trailing data, malformed syntax. Only new @jsonvalues directive; old format-1 and format-2 validators are unchanged. Centralize deterministic advance/retry/block decision used by the real runner. No external action execution; no new network, credential or background access.
C QA/security: reject a value mismatch even if the JSON schema matches, block exhausted retries, reject truncated responses and recheck stop condition immediately before committing a completed step. Preserve journal/checkpoint semantics and original bounds (10 steps, 3 attempts per step, 30 total, 30 minutes, 512 output tokens), startup ads and privacy boundaries.

Agreed KPI before implementation:
| ID | Required criterion | Evidence |
|---|---|---|
| C1 | All existing and new Java, locale, ad guard, compile and native/APK/AAB/16 KiB static checks pass on same application commit | CI job/step evidence |
| C2 | 100% strict-value fixtures: wrong value/type, missing/extra/duplicate key, malformed/trailing JSON, string escapes, numeric precision, null/container, legacy parsing; guided editor round-trip | deterministic JVM fixture results |
| C3 | 10/10 sequential accepted synthetic steps; zero advances on wrong value, truncation or exhausted retries; 3-attempt boundary; all existing journal/recovery fixtures pass | pure Java gate decision fixture (not Android execution) and existing regression |
| C4 | Source inspection confirms local-only foreground operation, unchanged storage format and budgets, explicit stop recheck before commit, no external side effects; no known open P0/P1 source defect | three-role source review, defect register |
| C5 | Five locales resource parity; exact condition and guidance in editor | CI/source checks; visual checks deferred |
| C6 | Real 10-step continuous workflow and 5/5 pause/cancel/restart/failure/budget scenarios, process death/recovery, no duplicate side effects, measured performance/security, visual/locale and actual result quality | DEFERRED_TO_12F (NOT_RUN), original requirements retained |

Development progression requires C1–C5 plus three-role source review and ten-perspective stage report; C6 remains mandatory for 12-F acceptance. Never infer factual correctness from equality to user-specified values; weak validators remain weak and supported for compatibility. New gate plans are not understood by older app versions; existing plans/checkpoints are preserved on forward update.
